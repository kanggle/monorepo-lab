package com.wms.inbound.integration;

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
 * TASK-MONO-768 AC-2 — inbound-service's dev seed ({@code db/seed/R__seed_dev_masterref.sql})
 * applies on top of the production migrations, and re-applies over its own rows without error or
 * duplication (a repeatable re-runs on every checksum change, over live data).
 *
 * <p>Before this, no test opened {@code classpath:db/seed} for this service against a database
 * ({@code ScmInboundExpectedDemoSeedShapeDltTest} parses the file text, it never executes it).
 * UUID agreement with the other services is inventory-service's {@code EcommerceSeedParityTest}.
 */
@Tag("integration")
@DisplayName("inbound dev seed: applies over db/migration and re-applies idempotently")
class DevSeedMigrationIT {

    private static final String[] DEV_LOCATIONS = {"classpath:db/migration", "classpath:db/seed"};
    private static final String WH_MAIN = "01910000-0000-7000-8000-000000000002";
    private static final String WH_MAIN_ZONE = "01910000-0000-7000-8000-000000000201";
    private static final String WH_MAIN_LOCATION = "01910000-0000-7000-8000-000000001101";

    @Test
    void seedAppliesAndReappliesWithoutDuplicating() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("inbound_seed")
                .withUsername("inbound")
                .withPassword("inbound")) {
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
        // What ReceiveAsnService / InstructPutawayService / ConfirmPutawayLineService read.
        assertThat(count(ds, "SELECT count(*) FROM warehouse_snapshot "
                + "WHERE id = '" + WH_MAIN + "' AND warehouse_code = 'WH-MAIN' AND status = 'ACTIVE'"))
                .isEqualTo(1);
        assertThat(count(ds, "SELECT count(*) FROM zone_snapshot "
                + "WHERE id = '" + WH_MAIN_ZONE + "' AND warehouse_id = '" + WH_MAIN + "'"))
                .isEqualTo(1);
        assertThat(count(ds, "SELECT count(*) FROM location_snapshot "
                + "WHERE id = '" + WH_MAIN_LOCATION + "' AND warehouse_id = '" + WH_MAIN + "' "
                + "AND zone_id = '" + WH_MAIN_ZONE + "' AND status = 'ACTIVE'"))
                .isEqualTo(1);
        // The reused supplier for the ecommerce ASNs (seed-wms.sh $SUPPLIER_ID).
        assertThat(count(ds, "SELECT count(*) FROM partner_snapshot "
                + "WHERE partner_code = 'SUP-001' AND partner_type = 'SUPPLIER' AND status = 'ACTIVE'"))
                .isEqualTo(1);
        assertThat(count(ds, "SELECT count(*) FROM warehouse_snapshot")).isEqualTo(2);
        assertThat(count(ds, "SELECT count(*) FROM location_snapshot")).isEqualTo(2);
        // SKU-APPLE-001 (pre-existing) + 86 ecommerce variants.
        assertThat(count(ds, "SELECT count(*) FROM sku_snapshot")).isEqualTo(87);
        assertThat(count(ds, "SELECT count(*) FROM sku_snapshot "
                + "WHERE sku_code LIKE 'c0000000-0000-0000-0000-%' "
                + "AND tracking_type = 'NONE' AND status = 'ACTIVE'"))
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
