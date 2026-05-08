package com.wms.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Verifies V1 + V99 Flyway migrations apply cleanly against a real Postgres
 * container, all 6 tables exist, and the seed data is present (4 built-in
 * roles + 1 admin user + global assignment + 4 default settings). Covers
 * AC-04.
 */
@Tag("integration")
@SpringBootTest(classes = com.wms.admin.AdminServiceApplication.class,
        properties = {
                "spring.flyway.locations=classpath:db/migration",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        })
// `standalone` gates AdminOutboxPublisher (@Profile("!standalone")) +
// RedisIdempotencyStore (@Profile("!standalone")) OFF so Flyway-only IT
// boots without Kafka/Redis. `test` keeps test-specific config (random
// consumer group-id reservation for BE-046).
@ActiveProfiles({"test", "standalone"})
@ContextConfiguration(initializers = AdminServiceIntegrationBase.Initializer.class)
class FlywayMigrationIntegrationTest extends AdminServiceIntegrationBase {

    @Autowired DataSource dataSource;

    @Test
    void allSixTablesExist() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String table : new String[]{
                "admin_user",
                "admin_role",
                "admin_user_role_assignment",
                "admin_setting",
                "admin_outbox",
                "admin_event_dedupe"
        }) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table %s must exist", table).isEqualTo(1);
        }
    }

    @Test
    void v2ReadModelTablesExist() {
        // BE-046 — 15 read-side tables created by V2__init_readmodel.sql.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String table : new String[]{
                "admin_warehouse_ref",
                "admin_zone_ref",
                "admin_location_ref",
                "admin_sku_ref",
                "admin_lot_ref",
                "admin_partner_ref",
                "admin_asn_summary",
                "admin_inspection_summary",
                "admin_order_summary",
                "admin_shipment_summary",
                "admin_inventory_snapshot",
                "admin_adjustment_audit",
                "admin_alert_log",
                "admin_throughput_inbound_daily",
                "admin_throughput_outbound_daily"
        }) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table %s must exist", table).isEqualTo(1);
        }
    }

    @Test
    void v99_seeds_fourBuiltInRoles() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM admin_role WHERE is_builtin = TRUE",
                Integer.class);
        assertThat(count).isEqualTo(4);
    }

    @Test
    void v99_seeds_adminUserWithSuperadminAssignment() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer userCount = jdbc.queryForObject(
                "SELECT count(*) FROM admin_user WHERE email = 'admin@wms.internal'",
                Integer.class);
        assertThat(userCount).isEqualTo(1);
        Integer assignCount = jdbc.queryForObject(
                "SELECT count(*) FROM admin_user_role_assignment WHERE status = 'ACTIVE'",
                Integer.class);
        assertThat(assignCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void v99_seeds_fourDefaultSettings() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM admin_setting WHERE scope = 'GLOBAL'",
                Integer.class);
        assertThat(count).isEqualTo(4);
    }

    @Test
    void jsonb_roundTrip_through_admin_role_permissions() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // permissions_json is JSONB — the seed inserted '["INVENTORY_READ", ...]'.
        // jsonb_array_length confirms Postgres parsed it as JSON, not text.
        Integer length = jdbc.queryForObject(
                "SELECT jsonb_array_length(permissions_json) FROM admin_role "
                        + "WHERE role_code = 'WMS_VIEWER'",
                Integer.class);
        assertThat(length).isPositive();
    }
}
