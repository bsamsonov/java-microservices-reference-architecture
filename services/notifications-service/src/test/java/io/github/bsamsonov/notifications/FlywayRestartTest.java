package io.github.bsamsonov.notifications;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A restart runs Flyway again on a migrated database and must find its schema history. The database user is named
 * after the service, so once a migration has created a schema of that name, "$user" in search_path resolves to it;
 * without a pinned default schema Flyway would look for the history there and refuse to start.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class FlywayRestartTest {

    @Autowired
    Flyway flyway;

    @Test
    void secondMigrateFindsTheHistoryAndAppliesNothing() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
