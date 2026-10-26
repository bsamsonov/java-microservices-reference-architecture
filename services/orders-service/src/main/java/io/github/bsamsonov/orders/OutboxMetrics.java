package io.github.bsamsonov.orders;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox backlog (ADR-0003, risk R1): how many publications are not delivered yet and how long the oldest one waits.
 * The alert {@code OutboxBacklogStale} fires on the age, so a long broker outage is visible before anyone asks.
 *
 * <p>The values are refreshed on a schedule, not on scrape: a scrape must not wait for a DB connection when the DB is slow.
 * When the query fails, both gauges become NaN until the next successful refresh; the DB itself has its own alerts.
 */
@Component
class OutboxMetrics implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(OutboxMetrics.class);

    // completion-mode delete keeps only the backlog in the table; the filter keeps the query right for other modes too
    private static final String BACKLOG = """
            select count(*) as pending,
                   coalesce(extract(epoch from now() - min(publication_date)), 0) as oldest_age_seconds
            from event_publication
            where completion_date is null
            """;

    private final JdbcClient jdbc;
    private volatile double pending = Double.NaN;
    private volatile double oldestAgeSeconds = Double.NaN;

    OutboxMetrics(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("outbox.publications.pending", this, m -> m.pending)
                .description("Event publications not delivered yet (transactional outbox)")
                .register(registry);
        TimeGauge.builder("outbox.publications.oldest.age", this, TimeUnit.SECONDS, m -> m.oldestAgeSeconds)
                .description("Age of the oldest undelivered event publication, 0 when the outbox is empty")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${outbox.metrics.interval:15s}")
    void refresh() {
        try {
            jdbc.sql(BACKLOG).query(rs -> {
                pending = rs.getLong("pending");
                oldestAgeSeconds = rs.getDouble("oldest_age_seconds");
            });
        } catch (DataAccessException e) {
            pending = Double.NaN;
            oldestAgeSeconds = Double.NaN;
            log.debug("Outbox metrics not refreshed: {}", e.getMessage());
        }
    }
}
