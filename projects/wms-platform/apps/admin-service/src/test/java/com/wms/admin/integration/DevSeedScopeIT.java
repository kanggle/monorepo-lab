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
 * TASK-BE-587 — the dev seed must apply in dev and the demo, and nowhere else.
 *
 * <p><b>What was wrong.</b> {@code R__seed_dev_data.sql} lived in
 * {@code db/migration}, which {@code application.yml} always names, while the
 * file's own header claimed *"the prod migration profile gates this file out via
 * callback / location filter at the platform level"*. Measured: admin-service had
 * one {@code application.yml}, zero profile variants, zero callbacks, zero
 * location filters, and it was absent from the demo override. The gate did not
 * exist, so the seed applied in every environment — planting a bootstrap
 * {@code admin@wms.internal} user with a global {@code WMS_SUPERADMIN} assignment
 * under a UUID the file publishes in its own comments.
 *
 * <p><b>The fix is placement, and this class is what makes placement checkable.</b>
 * The seed now lives in {@code db/seed}, opened only by {@code application-dev.yml}
 * and {@code infra/demo/wms-devseed.override.yml}. The first two cells are a
 * differential pair on exactly one variable — the location list — so a passing
 * "production seeds nothing" cannot be a test that seeds nothing for some other
 * reason.
 *
 * <p><b>Why removing it from production closes no surface.</b> Authorisation
 * here reads the JWT, never {@code admin_role}: {@code SecurityConfig} builds
 * authorities from the token's role claim plus the {@code entitled_domains} →
 * {@code ROLE_WMS_VIEWER} synthesis. {@code permissions_json} is only ever read
 * back out through the role CRUD API. The obvious objection — "this is the only
 * source of admin_role, so the eight dashboards die" — is false, and
 * {@link #production_locationsOnly_seedNothingButTheSchemaIsComplete()} pins the
 * half of that which is checkable from a migration test: the schema the
 * dashboards query is fully present with the seed absent.
 *
 * <p><b>The load-bearing cell is
 * {@link #existingDatabase_seededBeforeTheMove_refusesProductionLocationsUntilRepaired()},
 * and it overturned what I expected.</b> Flyway tolerates a missing VERSIONED
 * migration (it classifies it {@code future}) but a missing REPEATABLE is a
 * different question. I reasoned that the move was transparent because the
 * history row is byte-identical either way, ran it, and got
 * {@code Detected applied migration not resolved locally: seed dev data}. The
 * measured consequence is pinned in that cell together with its one-statement
 * repair, rather than smoothed over with an ignore-pattern that would cost a real
 * check in production permanently.
 */
@Tag("integration")
@DisplayName("admin dev seed applies in dev/demo only, and its absence breaks nothing")
class DevSeedScopeIT {

    /** Exactly what application.yml names — the production shape. */
    private static final String[] PRODUCTION = {"classpath:db/migration"};

    /** Exactly what application-dev.yml and the demo override name. */
    private static final String[] DEV_AND_DEMO = {"classpath:db/migration", "classpath:db/seed"};

    private static final String BOOTSTRAP_USER_EMAIL = "admin@wms.internal";

    @Test
    @DisplayName("production locations seed nothing, and the schema the dashboards read is complete")
    void production_locationsOnly_seedNothingButTheSchemaIsComplete() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);

            assertThatCode(() -> flyway(ds, PRODUCTION).migrate()).doesNotThrowAnyException();

            assertThat(count(ds, "SELECT count(*) FROM admin_role")).isZero();
            assertThat(count(ds, "SELECT count(*) FROM admin_user")).isZero();
            assertThat(count(ds, "SELECT count(*) FROM admin_user_role_assignment")).isZero();
            assertThat(count(ds, "SELECT count(*) FROM admin_setting")).isZero();

            // Nothing structural went missing with the seed: the read models the
            // eight dashboards query exist, including the ADR-MONO-065 tenant axis.
            assertThat(columnExists(ds, "admin_order_summary", "tenant_id")).isTrue();
            assertThat(columnExists(ds, "admin_shipment_summary", "tenant_id")).isTrue();
        }
    }

    @Test
    @DisplayName("control — the same migrate with db/seed added does seed all four tables")
    void devAndDemo_locations_seedTheFourTables() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);

            assertThatCode(() -> flyway(ds, DEV_AND_DEMO).migrate()).doesNotThrowAnyException();

            assertThat(count(ds, "SELECT count(*) FROM admin_role")).isEqualTo(4);
            assertThat(count(ds, "SELECT count(*) FROM admin_user WHERE email = '"
                    + BOOTSTRAP_USER_EMAIL + "'")).isEqualTo(1);
            assertThat(count(ds, "SELECT count(*) FROM admin_user_role_assignment")).isEqualTo(1);
            assertThat(count(ds, "SELECT count(*) FROM admin_setting")).isEqualTo(4);
        }
    }

    /**
     * 🔴 This cell records a consequence of the move that I assumed away and was
     * wrong about, and it is the reason the cell exists rather than a comment.
     *
     * <p>I expected the move to be transparent: a repeatable's identity is its
     * description, and {@code script} is recorded relative to the location root,
     * so the history row reads {@code R__seed_dev_data.sql} whichever directory
     * the file is in. All of that is true — and it does not help, because the
     * question Flyway asks is not "did the name change" but "does the applied
     * migration still RESOLVE". Once {@code db/seed} is off the location list it
     * does not, and validate refuses to boot:
     *
     * <pre>Detected applied migration not resolved locally: seed dev data.</pre>
     *
     * <p>So this is pinned rather than papered over. Papering over it means
     * {@code ignore-migration-patterns: repeatable:missing} in
     * {@code application.yml}, which would make production tolerate ANY deleted
     * repeatable forever — a permanent loss of a real check, bought to spare a
     * database that only exists because the gate was missing in the first place.
     *
     * <p><b>Blast radius, measured.</b> Only a database seeded under the old
     * always-on arrangement and later booted with production locations can hit
     * this. Dev boots with {@code application-dev.yml} and the demo boots with
     * {@code infra/demo/wms-devseed.override.yml}, which
     * {@code infra/demo/projects.sh} includes unconditionally in the wms stack —
     * both open {@code db/seed}, so both keep resolving it. What is left is a
     * hand-rolled {@code docker compose} against a previously-seeded volume.
     *
     * <p><b>Recovery is one statement</b>, and it is worth running deliberately:
     * such a database also holds the bootstrap {@code WMS_SUPERADMIN} under a
     * published UUID, which is the thing this ticket exists to stop shipping.
     *
     * <pre>DELETE FROM flyway_schema_history WHERE version IS NULL AND description = 'seed dev data';</pre>
     */
    @Test
    @DisplayName("an existing database seeded under the old arrangement refuses production locations until repaired")
    void existingDatabase_seededBeforeTheMove_refusesProductionLocationsUntilRepaired() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);

            flyway(ds, DEV_AND_DEMO).migrate();
            assertThat(appliedRepeatableScripts(ds)).isEqualTo(1);

            assertThatThrownBy(() -> flyway(ds, PRODUCTION).migrate())
                    .isInstanceOf(FlywayValidateException.class)
                    .hasMessageContaining("applied migration not resolved locally: seed dev data");

            // The documented repair, and then the same boot succeeds.
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.executeUpdate("DELETE FROM flyway_schema_history "
                        + "WHERE version IS NULL AND description = 'seed dev data'");
            }
            assertThatCode(() -> flyway(ds, PRODUCTION).migrate()).doesNotThrowAnyException();

            // Repair clears the history row, not the data. The rows the seed
            // already wrote survive — removing them is a separate, deliberate act
            // and this migration deliberately does not do it silently.
            assertThat(count(ds, "SELECT count(*) FROM admin_role")).isEqualTo(4);
        }
    }

    @Test
    @DisplayName("demo path — re-migrating with db/seed open does not duplicate the seeded rows")
    void existingDatabase_withTheSeedLocationOpen_reappliesWithoutDuplicating() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);

            flyway(ds, DEV_AND_DEMO).migrate();
            assertThatCode(() -> flyway(ds, DEV_AND_DEMO).migrate()).doesNotThrowAnyException();

            assertThat(count(ds, "SELECT count(*) FROM admin_role")).isEqualTo(4);
            assertThat(count(ds, "SELECT count(*) FROM admin_user")).isEqualTo(1);
            assertThat(count(ds, "SELECT count(*) FROM admin_setting")).isEqualTo(4);
        }
    }

    // --- helpers -------------------------------------------------------------

    private Flyway flyway(DataSource ds, String[] locations) {
        return Flyway.configure()
                .dataSource(ds)
                .locations(locations)
                .baselineOnMigrate(true) // exactly application.yml
                .load();
    }

    @SuppressWarnings("resource")
    private PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("admin_seedscope")
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

    private long appliedRepeatableScripts(DataSource ds) throws Exception {
        return count(ds, "SELECT count(*) FROM flyway_schema_history WHERE version IS NULL");
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
