package com.wms.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * TASK-MONO-768 AC-2 — inventory-service's dev seed ({@code db/seed/R__seed_dev_masterref.sql})
 * applies on top of the production migrations, and re-applies over its own rows without error or
 * duplication (a repeatable re-runs on every checksum change, over live data).
 *
 * <p>Before this, no test anywhere opened {@code classpath:db/seed} for this service — the demo
 * (via {@code infra/demo/wms-devseed.override.yml}) was the first place the file ever executed.
 * UUID agreement with the other services is {@code EcommerceSeedParityTest}'s job; this class
 * only proves the SQL itself is valid against the real schema (column set, CHECK constraints).
 */
@Tag("integration")
@DisplayName("inventory dev seed: applies over db/migration and re-applies idempotently")
class DevSeedMigrationIT {

    private static final String[] DEV_LOCATIONS = {"classpath:db/migration", "classpath:db/seed"};
    private static final String WH_MAIN = "01910000-0000-7000-8000-000000000002";
    private static final String WH_MAIN_LOCATION = "01910000-0000-7000-8000-000000001101";

    @Test
    void seedAppliesAndReappliesWithoutDuplicating() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("inventory_seed")
                .withUsername("inventory")
                .withPassword("inventory")) {
            pg.start();
            DataSource ds = DataSourceBuilder.create()
                    .url(pg.getJdbcUrl()).username(pg.getUsername()).password(pg.getPassword()).build();

            assertThatCode(() -> flyway(ds).migrate()).doesNotThrowAnyException();
            assertSeeded(ds);

            // Force the repeatable to run again over the rows it already wrote — the demo's
            // situation after any checksum change. ON CONFLICT DO NOTHING must hold.
            exec(ds, "DELETE FROM flyway_schema_history WHERE version IS NULL");
            assertThatCode(() -> flyway(ds).migrate()).doesNotThrowAnyException();
            assertThat(count(ds, "SELECT count(*) FROM flyway_schema_history "
                    + "WHERE version IS NULL AND script = 'R__seed_dev_masterref.sql' AND success"))
                    .as("the repeatable actually re-ran").isEqualTo(1);
            assertSeeded(ds);
        }
    }

    private static void assertSeeded(DataSource ds) throws Exception {
        assertThat(count(ds, "SELECT count(*) FROM warehouse_snapshot "
                + "WHERE id = '" + WH_MAIN + "' AND warehouse_code = 'WH-MAIN' AND status = 'ACTIVE'"))
                .isEqualTo(1);
        assertThat(count(ds, "SELECT count(*) FROM location_snapshot "
                + "WHERE id = '" + WH_MAIN_LOCATION + "' AND warehouse_id = '" + WH_MAIN + "' "
                + "AND location_code = 'WH-MAIN-A-01-01-01' AND status = 'ACTIVE'"))
                .isEqualTo(1);
        // WH01-A-01-01-01 (pre-existing) + WH-MAIN-A-01-01-01.
        assertThat(count(ds, "SELECT count(*) FROM location_snapshot")).isEqualTo(2);
        // SKU-APPLE-001 (pre-existing) + 86 ecommerce variants.
        assertThat(count(ds, "SELECT count(*) FROM sku_snapshot")).isEqualTo(87);
        assertThat(count(ds, "SELECT count(*) FROM sku_snapshot "
                + "WHERE sku_code LIKE 'c0000000-0000-0000-0000-%' "
                + "AND tracking_type = 'NONE' AND status = 'ACTIVE' AND base_uom = 'EA'"))
                .isEqualTo(86);
    }

    private static Flyway flyway(DataSource ds) {
        return Flyway.configure().dataSource(ds).locations(DEV_LOCATIONS).load();
    }

    private static void exec(DataSource ds, String sql) throws Exception {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    private static long count(DataSource ds, String sql) throws Exception {
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
