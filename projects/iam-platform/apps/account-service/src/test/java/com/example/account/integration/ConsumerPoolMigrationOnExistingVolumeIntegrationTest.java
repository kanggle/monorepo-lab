package com.example.account.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-614 AC-1 / AC-2 — V0029 + V0030 applied to an <b>existing volume</b>: a database that
 * already carries every migration up to V0028 <i>and data</i> (the shape of a running deployment),
 * not only a fresh schema.
 *
 * <p>Why a separate database instead of the Spring-managed one: Flyway has already taken that one
 * to the latest version by the time any context starts, so it can only ever prove the fresh-volume
 * path — the path that is permanently green for migration-order mistakes. Here the test stops
 * Flyway at V0028, writes rows the way the pre-pool code did, and only then runs the new versions.
 *
 * <p>No Spring context: Flyway is driven directly against the shared MySQL container.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("TASK-BE-614 AC-2 — 소비자 풀 마이그레이션이 기존 볼륨(V0028 + 데이터)에 적용된다")
class ConsumerPoolMigrationOnExistingVolumeIntegrationTest extends AbstractIntegrationTest {

    private static final String ROOT_USER = "root";
    private static final String DB = "be614_existing_volume";
    private static final String LAST_PRE_POOL_VERSION = "28";
    /**
     * The last of the two versions under test. TASK-MONO-750 added V0031 (an unrelated seed row);
     * upgrading to "latest" would apply it too and break {@code newVersionsApplySuccessfully}'s
     * "exactly the new files" premise for a reason that has nothing to do with the pool. So the
     * upgrade stops here, the same way step 1 stops at {@link #LAST_PRE_POOL_VERSION}.
     */
    private static final String LAST_POOL_VERSION = "30";

    private static final String FAN_ACCOUNT = "00000000-0000-0000-0000-00000000f614";
    private static final String SHOP_ACCOUNT = "00000000-0000-0000-0000-00000000e614";

    private MigrateResult upgrade;
    private List<String> accountsBefore;
    private List<String> rolesBefore;

    @BeforeAll
    void migrateExistingVolume() throws SQLException {
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), ROOT_USER, MYSQL.getPassword());
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + DB);
            s.execute("CREATE DATABASE " + DB + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        // 1. the volume as main left it: every migration up to V0028 …
        flyway(LAST_PRE_POOL_VERSION).migrate();
        assertThat(Integer.parseInt(currentVersion())).isEqualTo(Integer.parseInt(LAST_PRE_POOL_VERSION));

        // 2. … holding pre-pool data: a fan site account and an ecommerce seller with a role.
        exec("INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, version) "
                + "VALUES ('" + FAN_ACCOUNT + "', 'fan-platform', 'fan@example.com', 'ACTIVE', NOW(6), NOW(6), 0)");
        // profiles.tenant_id is NOT NULL and has no default at V0028 — the first CI run of this
        // fixture failed here ("Field 'tenant_id' doesn't have a default value") because the
        // pre-pool row omitted it; the migration under test was never reached.
        exec("INSERT INTO profiles (account_id, tenant_id, locale, timezone, updated_at) "
                + "VALUES ('" + FAN_ACCOUNT + "', 'fan-platform', 'ko-KR', 'Asia/Seoul', NOW(6))");
        exec("INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, version) "
                + "VALUES ('" + SHOP_ACCOUNT + "', 'ecommerce', 'seller@example.com', 'ACTIVE', NOW(6), NOW(6), 0)");
        exec("INSERT INTO account_roles (tenant_id, account_id, role_name, granted_by, granted_at) "
                + "VALUES ('ecommerce', '" + SHOP_ACCOUNT + "', 'SELLER', NULL, NOW(6))");
        accountsBefore = column("SELECT CONCAT(id, '|', tenant_id, '|', email, '|', status, '|', version) "
                + "FROM accounts ORDER BY id");
        rolesBefore = column("SELECT CONCAT(tenant_id, '|', account_id, '|', role_name) FROM account_roles ORDER BY 1");

        // 3. the new versions on top of it
        upgrade = flyway(LAST_POOL_VERSION).migrate();
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(jdbcUrl(), ROOT_USER, MYSQL.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static String jdbcUrl() {
        return MYSQL.getJdbcUrl().replaceFirst("/" + MYSQL.getDatabaseName() + "(\\?|$)", "/" + DB + "$1");
    }

    private String currentVersion() {
        MigrationInfo current = flyway(null).info().current();
        return current == null ? null : current.getVersion().getVersion();
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl(), ROOT_USER, MYSQL.getPassword());
             Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private List<String> column(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl(), ROOT_USER, MYSQL.getPassword());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    @Test
    @DisplayName("V0029·V0030 이 성공으로 적용되고, 그 둘만 적용된다")
    void newVersionsApplySuccessfully() {
        assertThat(upgrade.success).isTrue();
        // Flyway may render the version with or without the file's leading zeros — compare numerically.
        assertThat(upgrade.migrations)
                .extracting(m -> Integer.parseInt(m.version))
                .as("the upgrade must apply exactly the new files — if V0028 or earlier reappears here, "
                        + "the 'existing volume' premise did not hold")
                .containsExactly(29, 30);
    }

    @Test
    @DisplayName("consumer-pool 테넌트 행: B2C_CONSUMER · ACTIVE · org_node 없음(D7 합법 상태)")
    void poolTenantRowExists() throws SQLException {
        assertThat(column("SELECT CONCAT(tenant_type, '|', status, '|', IFNULL(org_node_id, 'NULL')) "
                + "FROM tenants WHERE tenant_id = 'consumer-pool'"))
                .containsExactly("B2C_CONSUMER|ACTIVE|NULL");
    }

    @Test
    @DisplayName("AC-1: 기존 행은 바이트 그대로 — 계정·역할 무변경, 새 테이블은 비어 있다")
    void existingDataUntouched() throws SQLException {
        assertThat(column("SELECT CONCAT(id, '|', tenant_id, '|', email, '|', status, '|', version) "
                + "FROM accounts ORDER BY id")).isEqualTo(accountsBefore);
        assertThat(column("SELECT CONCAT(tenant_id, '|', account_id, '|', role_name) FROM account_roles ORDER BY 1"))
                .isEqualTo(rolesBefore);
        assertThat(column("SELECT COUNT(*) FROM consumer_site_memberships")).containsExactly("0");
        assertThat(column("SELECT COUNT(*) FROM consumer_site_roles")).containsExactly("0");
    }

    @Test
    @DisplayName("새 제약이 실제로 문다: 계정 FK · 상태 CHECK · 멤버십 없는 사이트 역할 FK")
    void newConstraintsBite() throws SQLException {
        String poolAccount = "00000000-0000-0000-0000-0000000a0614";
        exec("INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, version) "
                + "VALUES ('" + poolAccount + "', 'consumer-pool', 'pooled@example.com', 'ACTIVE', NOW(6), NOW(6), 0)");
        exec("INSERT INTO consumer_site_memberships (account_id, site_tenant_id, status, consented_at) "
                + "VALUES ('" + poolAccount + "', 'ecommerce', 'ACTIVE', NOW(6))");
        exec("INSERT INTO consumer_site_roles (account_id, site_tenant_id, role_name, granted_by, granted_at) "
                + "VALUES ('" + poolAccount + "', 'ecommerce', 'SELLER', NULL, NOW(6))");

        assertThatThrownBy(() -> exec("INSERT INTO consumer_site_memberships "
                + "(account_id, site_tenant_id, status, consented_at) "
                + "VALUES ('no-such-account', 'ecommerce', 'ACTIVE', NOW(6))"))
                .as("fk_consumer_site_memberships_account").isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> exec("INSERT INTO consumer_site_memberships "
                + "(account_id, site_tenant_id, status, consented_at) "
                + "VALUES ('" + poolAccount + "', 'fan-platform', 'GONE', NOW(6))"))
                .as("ck_consumer_site_memberships_status").isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> exec("INSERT INTO consumer_site_roles "
                + "(account_id, site_tenant_id, role_name, granted_by, granted_at) "
                + "VALUES ('" + poolAccount + "', 'fan-platform', 'ARTIST', NULL, NOW(6))"))
                .as("a site role without that site's membership cannot exist").isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> exec("INSERT INTO consumer_site_memberships "
                + "(account_id, site_tenant_id, status, consented_at) "
                + "VALUES ('" + poolAccount + "', 'no-such-site', 'ACTIVE', NOW(6))"))
                .as("fk_consumer_site_memberships_site_tenant").isInstanceOf(SQLException.class);

        // cleanup cascades: deleting the membership deletes its roles; deleting the account its memberships
        exec("DELETE FROM accounts WHERE id = '" + poolAccount + "'");
        assertThat(column("SELECT COUNT(*) FROM consumer_site_memberships WHERE account_id = '" + poolAccount + "'"))
                .containsExactly("0");
        assertThat(column("SELECT COUNT(*) FROM consumer_site_roles WHERE account_id = '" + poolAccount + "'"))
                .containsExactly("0");
    }

    @Test
    @DisplayName("재실행은 no-op — 적용할 것이 없다")
    void secondRunIsNoOp() {
        // Same target as the upgrade — with no target, any later migration (V0031 on, TASK-MONO-750)
        // would run here and turn «re-run is a no-op» into «a newer file exists».
        MigrateResult again = flyway(LAST_POOL_VERSION).migrate();
        assertThat(again.success).isTrue();
        assertThat(again.migrationsExecuted).isZero();
    }
}
