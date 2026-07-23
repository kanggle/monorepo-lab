package com.example.erp.approval.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testsupport.integration.DockerAvailableCondition;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * TASK-ERP-BE-035 — regression for the shared-{@code erp_db} Flyway history
 * collision that crash-looped approval-service on the demo host (MONO-399).
 *
 * <p>approval / masterdata / notification each ship their own {@code V1__init}
 * (plus generic {@code outbox} / {@code processed_events} tables). When all
 * three shared one database they shared one {@code flyway_schema_history}: the
 * first to migrate wrote version 1's checksum, the others failed
 * {@code validate} with a checksum mismatch and never booted.
 *
 * <p>The shipped fix gives each service its <b>own database</b> (approval →
 * {@code erp_approval_db}), which yields its own {@code flyway_schema_history}.
 * This test exercises that isolation property directly, driving Flyway against
 * the container's database:
 * <ul>
 *   <li><b>fix</b>: approval's real migrations apply cleanly when their Flyway
 *       history is isolated (its own history table = its own DB in production);
 *   <li><b>repro</b>: when another service has already won version 1 in a
 *       <em>shared</em> history, approval's migrate fails fast with
 *       {@link FlywayValidateException} — exactly the demo-host crash.
 * </ul>
 *
 * <p>Uses only the container's default database + app user (no root / no extra
 * databases), so it is deterministic and privilege-safe. baseline-on-migrate is
 * off so a fresh history table always replays from V1 regardless of test order.
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@Testcontainers
class FlywayHistoryIsolationIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withUsername("erp")
                    .withPassword("erp")
                    .withStartupTimeout(Duration.ofMinutes(3));

    /** Drop every object so each test starts from an empty schema (the two tests
     *  share one container; without this the second sees a non-empty schema with
     *  no history table and Flyway aborts). */
    @BeforeEach
    void wipeSchema() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .cleanDisabled(false)
                .load()
                .clean();
    }

    private static Flyway flyway(String historyTable, String location) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .table(historyTable)
                .locations(location)
                .baselineOnMigrate(false)
                .load();
    }

    @Test
    void approvalMigratesCleanlyWithAnIsolatedHistory() {
        // Its own history table (what a dedicated erp_approval_db gives it in
        // production) → approval's real migrations apply with no mismatch.
        assertThatCode(() -> flyway("flyway_history_approval", "classpath:db/migration").migrate())
                .as("approval's real migrations apply cleanly when its Flyway history is isolated")
                .doesNotThrowAnyException();
    }

    @Test
    void approvalFailsValidateWhenAnotherServiceWonTheSharedHistory() {
        // Another service migrates version 1 first into the SHARED default
        // history table (the pre-fix erp_db situation).
        flyway("flyway_schema_history", "classpath:db/collision").migrate();

        // approval's real version 1 now mismatches the stored checksum → the
        // exact FlywayValidateException that crash-looped it on the demo host.
        Flyway approval = flyway("flyway_schema_history", "classpath:db/migration");
        assertThatThrownBy(approval::migrate)
                .as("a shared Flyway history won by another service must fail approval's validate")
                .isInstanceOf(FlywayValidateException.class);
    }
}
