package com.wms.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * TASK-BE-585 — the migration timeline must stay applicable to a database that
 * already exists.
 *
 * <p><b>Why this test cannot be a normal fresh-database test.</b> Every other
 * migration test in this repo starts from an empty Postgres, where V1·V2·V3·R__
 * apply in ascending order and nothing can ever be out of order. The defect this
 * pins is invisible there — it lives in the {@code flyway_schema_history} rows
 * of a database that booted <em>before</em> {@code V3} existed, back when the
 * dev seed was {@code V99__seed_dev_data.sql} and therefore the highest applied
 * version. TASK-BE-584 measured the consequence on the live demo volume:
 * admin-service crash-looped (restarts=12) with
 *
 * <pre>Detected resolved migration not applied to database: 3.</pre>
 *
 * and all eight dashboard surfaces returned 500. CI was structurally blind to
 * it, and so were the 54 integration tests TASK-BE-583 shipped with the axis.
 *
 * <p>So this test <b>reconstructs the pre-existing database</b>: it applies the
 * production timeline only as far as V2, then writes the watermark history row
 * the old V99 seed used to leave behind. That is the state every real admin_db
 * is in.
 *
 * <p><b>The control is the defect.</b> {@link #outOfOrderDisabled_reproducesTheCrash()}
 * asserts the failure still happens with the shim off — without that cell the
 * passing cell would be indistinguishable from a test that reproduces nothing
 * and therefore cannot fail. The pair is the assertion, not either half.
 */
@Tag("integration")
@DisplayName("existing admin_db (pre-V3 watermark) must still migrate")
class ExistingVolumeMigrationOrderIT {

    private static final String OLD_SEED_SCRIPT = "V99__seed_dev_data.sql";
    private static final String VIEWER_ROLE_ID = "11111111-1111-1111-1111-111111111111";

    /**
     * Reconstructs the state every real admin_db is in: V1 and V2 applied, the
     * seed's rows present, and a {@code version='99'} watermark row standing
     * where {@code V99__seed_dev_data.sql} used to be — but V3 not yet applied.
     *
     * <p>Note {@code target=2} does <b>not</b> hold back repeatables — Flyway
     * runs them after the versioned ones regardless, so the seed's rows land
     * here exactly as V99 once wrote them. That is what we want; we only have to
     * relabel how they got there. Deleting the repeatable's history row (its
     * version column is NULL) and inserting the V99 row turns "R__ applied" into
     * "V99 applied", which is the real pre-existing shape. The rows stay behind,
     * so when the repeatable re-runs in the assertions it runs straight into
     * live conflicting data — which is precisely what must not crash.
     */
    private void seedPreExistingDatabase(DataSource ds) throws Exception {
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .target(org.flywaydb.core.api.MigrationVersion.fromVersion("2"))
                .load()
                .migrate();

        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM flyway_schema_history WHERE version IS NULL");
            s.executeUpdate(
                    "INSERT INTO flyway_schema_history "
                            + "(installed_rank, version, description, type, script, checksum, "
                            + " installed_by, installed_on, execution_time, success) "
                            + "VALUES (99, '99', 'seed dev data', 'SQL', '" + OLD_SEED_SCRIPT + "', "
                            + " 123456789, 'admin', now(), 1, true)");
        }
    }

    private Flyway realConfig(DataSource ds, boolean outOfOrder) {
        return Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration") // exactly application.yml
                .baselineOnMigrate(true)
                .outOfOrder(outOfOrder)
                .load();
    }

    @Test
    @DisplayName("control — with the shim off the pre-V3 database still refuses to migrate")
    void outOfOrderDisabled_reproducesTheCrash() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);
            seedPreExistingDatabase(ds);

            assertThatThrownBy(() -> realConfig(ds, false).migrate())
                    .isInstanceOf(FlywayValidateException.class)
                    .hasMessageContaining("not applied to database: 3");
        }
    }

    @Test
    @DisplayName("with the shim on, V3 applies and the repeatable seed does not collide")
    void outOfOrderEnabled_appliesTheTenantAxisOverAnExistingDatabase() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);
            seedPreExistingDatabase(ds);

            assertThatCode(() -> realConfig(ds, true).migrate()).doesNotThrowAnyException();

            // The axis actually reached the table — this is what ADR-MONO-065
            // could never deliver to an existing database before.
            assertThat(columnExists(ds, "admin_order_summary", "tenant_id")).isTrue();
            assertThat(columnExists(ds, "admin_shipment_summary", "tenant_id")).isTrue();

            // The repeatable re-ran over the rows the old seed had already
            // inserted and neither crashed nor duplicated them.
            assertThat(count(ds, "SELECT count(*) FROM admin_role WHERE id = '" + VIEWER_ROLE_ID + "'"))
                    .isEqualTo(1);
            assertThat(count(ds, "SELECT count(*) FROM admin_role")).isEqualTo(4);
        }
    }

    @Test
    @DisplayName("a fresh database is unaffected — the shim is a no-op there")
    void freshDatabase_migratesWithoutTheShim() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);

            assertThatCode(() -> realConfig(ds, false).migrate()).doesNotThrowAnyException();
            assertThat(columnExists(ds, "admin_order_summary", "tenant_id")).isTrue();
            assertThat(count(ds, "SELECT count(*) FROM admin_role")).isEqualTo(4);
        }
    }

    // --- helpers -------------------------------------------------------------

    @SuppressWarnings("resource")
    private PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("admin_db")
                .withUsername("admin")
                .withPassword("admin");
    }

    private DataSource dataSource(PostgreSQLContainer<?> pg) {
        return DataSourceBuilder.create()
                .url(pg.getJdbcUrl())
                .username(pg.getUsername())
                .password(pg.getPassword())
                .build();
    }

    private boolean columnExists(DataSource ds, String table, String column) throws Exception {
        return count(ds,
                "SELECT count(*) FROM information_schema.columns WHERE table_name = '"
                        + table + "' AND column_name = '" + column + "'") == 1;
    }

    private long count(DataSource ds, String sql) throws Exception {
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
