package com.example.erp.approval.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testsupport.integration.DockerAvailableCondition;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
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
 * {@code validate} with a checksum mismatch and never booted. The fix gives
 * each service its own database.
 *
 * <p>This test drives Flyway directly (no Spring context) against a single
 * MySQL container:
 * <ul>
 *   <li><b>fix</b>: approval's real migrations apply cleanly in a dedicated DB;
 *   <li><b>repro</b>: if another service "wins" version 1 first in a SHARED DB,
 *       approval's migrate fails fast with {@link FlywayValidateException}.
 * </ul>
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

    private static String urlFor(String db) {
        // Swap the container's default database name for the target one. Split on
        // '?' first so a '/' inside query params can't be mistaken for the path.
        String base = MYSQL.getJdbcUrl();
        int q = base.indexOf('?');
        String head = q >= 0 ? base.substring(0, q) : base;
        String params = q >= 0 ? base.substring(q) : "";
        int slash = head.lastIndexOf('/');
        return head.substring(0, slash + 1) + db + params;
    }

    private static void createDatabase(String db) throws Exception {
        try (Connection c = MYSQL.createConnection("");
                Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE IF NOT EXISTS " + db
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private static Flyway flyway(String db, String location) {
        return Flyway.configure()
                .dataSource(urlFor(db), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .baselineOnMigrate(true)
                .load();
    }

    @Test
    void approvalMigratesCleanlyInItsOwnDatabase() throws Exception {
        createDatabase("erp_approval_db");

        assertThatCode(() -> flyway("erp_approval_db", "classpath:db/migration").migrate())
                .as("approval's real migrations apply with no checksum mismatch in its own DB")
                .doesNotThrowAnyException();
    }

    @Test
    void approvalFailsValidateWhenAnotherServiceWonTheSharedHistory() throws Exception {
        createDatabase("erp_shared_db");

        // Another service migrates version 1 first into the SHARED history table.
        flyway("erp_shared_db", "classpath:db/collision").migrate();

        // approval's real version 1 now mismatches the stored checksum → fail fast.
        Flyway approval = flyway("erp_shared_db", "classpath:db/migration");
        assertThatThrownBy(approval::migrate)
                .as("shared flyway_schema_history won by another service must fail approval's validate")
                .isInstanceOf(FlywayValidateException.class);
    }
}
