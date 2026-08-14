package com.wms.master.integration;

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
 * TASK-MONO-531 — the wms dev seeds must (a) run in foreign-key order as
 * repeatables and (b) leave an already-seeded database still migratable.
 *
 * <p><b>Why a fresh-database test cannot cover half of this.</b> Every other
 * migration test in this repo starts from an empty Postgres, where versions
 * apply in ascending order and nothing can ever be out of order. The defect
 * lives in the {@code flyway_schema_history} rows of a database that booted
 * while the seeds were still {@code V99}–{@code V103}: production for
 * master-service only reaches V8, so those rows made 103 the highest APPLIED
 * version, and the next production migration would resolve <em>below</em> it.
 * Flyway rejects that by default. wms admin-service crash-looped on exactly this
 * on 2026-08-14 (TASK-BE-584 AC-0: restarts=12, all eight dashboards 500) and
 * iam auth-service on 2026-08-11 (TASK-MONO-524). CI never saw either.
 *
 * <p>So two different things are pinned here, and they fail for different
 * reasons:
 *
 * <ul>
 *   <li>{@link #freshVolume_repeatableSeedsRunInForeignKeyOrder()} — the risk
 *       created by the fix itself. Flyway runs repeatables in DESCRIPTION order,
 *       so warehouse → zone → location survives only because the files are named
 *       {@code R__01}…{@code R__05}. Get that wrong and the seed dies on a
 *       foreign key. The FK constraints are the assertion mechanism: a wrong
 *       order cannot reach the row counts below.</li>
 *   <li>{@link #existingVolume_theNextProductionMigrationCrashes()} /
 *       {@link #existingVolume_withTheShim_theNextProductionMigrationApplies()}
 *       — the pre-existing demo volume. <b>The control is the defect.</b>
 *       Without the crashing cell the passing cell would be indistinguishable
 *       from a test that reproduces nothing. The pair is the assertion, not
 *       either half.</li>
 * </ul>
 *
 * <p><b>Measured, and worth stating plainly:</b> with no pending migration below
 * the band, an existing volume migrates fine today — the shim in
 * {@code infra/demo/wms-devseed.override.yml} is a no-op right now. It stops
 * being one the moment someone adds master V9 / outbound V19, which is why these
 * cells inject {@code db/futureproduction} rather than asserting on today's
 * timeline.
 */
@Tag("integration")
@DisplayName("wms dev seeds: FK order as repeatables + an already-seeded volume stays migratable")
class ExistingSeedVolumeMigrationOrderIT {

    /** Exactly what application-dev.yml and the demo override open. */
    private static final String[] REAL_LOCATIONS = {"classpath:db/migration", "classpath:db/seed"};

    /** The same, plus a stand-in for the next production migration (V9). */
    private static final String[] WITH_NEXT_PRODUCTION_MIGRATION = {
        "classpath:db/migration", "classpath:db/seed", "classpath:db/futureproduction"
    };

    /** The version rows the pre-TASK-MONO-531 seeds left in every demo volume. */
    private static final String[][] OLD_SEED_BAND = {
        {"99", "seed dev warehouse", "V99__seed_dev_warehouse.sql"},
        {"100", "seed dev zones", "V100__seed_dev_zones.sql"},
        {"101", "seed dev locations", "V101__seed_dev_locations.sql"},
        {"102", "seed dev skus", "V102__seed_dev_skus.sql"},
        {"103", "seed dev partners", "V103__seed_dev_partners.sql"},
    };

    @Test
    @DisplayName("fresh volume — R__01..R__05 satisfy the warehouse → zone → location foreign keys")
    void freshVolume_repeatableSeedsRunInForeignKeyOrder() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);

            // A wrong repeatable order does not produce wrong data — it throws,
            // because zones.warehouse_id and locations.zone_id are real FKs.
            assertThatCode(() -> flyway(ds, REAL_LOCATIONS, false).migrate())
                    .doesNotThrowAnyException();

            assertThat(count(ds, "SELECT count(*) FROM warehouses")).isEqualTo(1);
            assertThat(count(ds, "SELECT count(*) FROM zones")).isEqualTo(3);
            assertThat(count(ds, "SELECT count(*) FROM locations")).isEqualTo(3);
            assertThat(count(ds, "SELECT count(*) FROM skus")).isEqualTo(3);
            assertThat(count(ds, "SELECT count(*) FROM partners")).isEqualTo(3);

            // Not just "3 rows landed" — each one resolves through the chain the
            // prefixes exist to order.
            assertThat(count(ds,
                    "SELECT count(*) FROM locations l "
                            + "JOIN zones z ON z.id = l.zone_id "
                            + "JOIN warehouses w ON w.id = z.warehouse_id"))
                    .isEqualTo(3);

            // Nothing versioned above the production timeline was applied. This is
            // the property the whole ticket buys, stated directly.
            assertThat(count(ds,
                    "SELECT count(*) FROM flyway_schema_history "
                            + "WHERE version IS NOT NULL AND version::numeric > 8"))
                    .isZero();
        }
    }

    @Test
    @DisplayName("control — an existing V99..V103 volume refuses the next production migration")
    void existingVolume_theNextProductionMigrationCrashes() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);
            reconstructPreExistingDemoVolume(ds);

            assertThatThrownBy(() -> flyway(ds, WITH_NEXT_PRODUCTION_MIGRATION, false).migrate())
                    .isInstanceOf(FlywayValidateException.class)
                    .hasMessageContaining("not applied to database: 9");
        }
    }

    @Test
    @DisplayName("with the demo shim on, the next production migration applies and the seeds do not duplicate")
    void existingVolume_withTheShim_theNextProductionMigrationApplies() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);
            reconstructPreExistingDemoVolume(ds);

            assertThatCode(() -> flyway(ds, WITH_NEXT_PRODUCTION_MIGRATION, true).migrate())
                    .doesNotThrowAnyException();

            assertThat(tableExists(ds, "mono531_next_production_marker")).isTrue();

            // The repeatables re-ran over rows the old versioned seeds had already
            // inserted — over live conflicting data, which is precisely what must
            // not crash or double up.
            assertThat(count(ds, "SELECT count(*) FROM warehouses")).isEqualTo(1);
            assertThat(count(ds, "SELECT count(*) FROM zones")).isEqualTo(3);
            assertThat(count(ds, "SELECT count(*) FROM locations")).isEqualTo(3);
            assertThat(count(ds, "SELECT count(*) FROM skus")).isEqualTo(3);
            assertThat(count(ds, "SELECT count(*) FROM partners")).isEqualTo(3);
        }
    }

    // --- fixture -------------------------------------------------------------

    /**
     * Reconstructs the state every demo master_db is in: production V1–V8
     * applied, the seed rows present, and {@code version='99'}…{@code '103'}
     * watermark rows standing where the old versioned seeds used to be.
     *
     * <p>Migrating with the seed location first is deliberate — it puts the rows
     * there exactly as V99–V103 once wrote them; only the history has to be
     * relabelled. Deleting the repeatables' rows (their version column is NULL)
     * and inserting the band turns "R__ applied" into "V99..V103 applied", which
     * is the real pre-existing shape, and it leaves the seeded rows behind so the
     * repeatables re-run into live data.
     */
    private void reconstructPreExistingDemoVolume(DataSource ds) throws Exception {
        flyway(ds, REAL_LOCATIONS, false).migrate();

        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM flyway_schema_history WHERE version IS NULL");
            for (String[] row : OLD_SEED_BAND) {
                s.executeUpdate(
                        "INSERT INTO flyway_schema_history "
                                + "(installed_rank, version, description, type, script, checksum, "
                                + " installed_by, installed_on, execution_time, success) "
                                + "VALUES (" + row[0] + ", '" + row[0] + "', '" + row[1] + "', "
                                + " 'SQL', '" + row[2] + "', 123456789, 'master', now(), 1, true)");
            }
        }
    }

    private Flyway flyway(DataSource ds, String[] locations, boolean outOfOrder) {
        return Flyway.configure()
                .dataSource(ds)
                .locations(locations)
                .baselineOnMigrate(true)
                .outOfOrder(outOfOrder)
                .load();
    }

    // --- helpers -------------------------------------------------------------

    @SuppressWarnings("resource")
    private PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("master_seedband")
                .withUsername("master")
                .withPassword("master");
    }

    private DataSource dataSource(PostgreSQLContainer<?> pg) {
        return DataSourceBuilder.create()
                .url(pg.getJdbcUrl())
                .username(pg.getUsername())
                .password(pg.getPassword())
                .build();
    }

    private boolean tableExists(DataSource ds, String table) throws Exception {
        return count(ds,
                "SELECT count(*) FROM information_schema.tables WHERE table_name = '" + table + "'")
                == 1;
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
