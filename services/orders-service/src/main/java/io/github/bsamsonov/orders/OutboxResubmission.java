package io.github.bsamsonov.orders;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Outbox delivery while the service runs (D12): a publication whose listener failed (Kafka down, audit listener error)
 * stays in {@code event_publication} as FAILED and is resubmitted here until it succeeds.
 * The registry claims each row with a conditional update, so replicas do not resubmit the same row twice.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class OutboxResubmission {

    private final FailedEventPublications failed;
    private final ResubmissionOptions options;

    OutboxResubmission(FailedEventPublications failed,
                       @Value("${outbox.resubmission.min-age:10s}") Duration minAge,
                       @Value("${outbox.resubmission.batch-size:100}") int batchSize) {
        this.failed = failed;
        this.options = ResubmissionOptions.defaults().withMinAge(minAge).withBatchSize(batchSize);
    }

    @Scheduled(fixedDelayString = "${outbox.resubmission.interval:30s}", initialDelayString = "${outbox.resubmission.interval:30s}")
    void resubmitFailedPublications() {
        failed.resubmit(options);
    }
}
