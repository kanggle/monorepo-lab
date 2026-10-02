package com.example.product.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-739 AC-8 — {@code V21__seed_artist_goods.sql} applied to an <b>existing volume</b>: a database that
 * already carries every migration up to V20 <i>and data</i> (the demo DB shape), not only a fresh schema.
 *
 * <p>Why not the Spring-managed database: Flyway takes that one to the latest version before any test runs,
 * so it only ever proves the fresh-volume path — the path that stayed green while {@code V19} (TASK-MONO-638)
 * was broken for {@code product_variants.tenant_id}. Here Flyway stops at V20, a row is written the way the
 * pre-goods code wrote it, and only then V21 runs. Same shape as account-service's
 * {@code ConsumerPoolMigrationOnExistingVolumeIntegrationTest}.
 *
 * <p>No Spring context: Flyway is driven directly against a dedicated Postgres container.
 */
@Tag("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("TASK-MONO-739 AC-8 — 굿즈 시드 V21 이 기존 볼륨(V20 + 데이터)에 적용된다")
class ArtistGoodsSeedOnExistingVolumeIntegrationTest {

    private static final String LAST_PRE_GOODS_VERSION = "20";
    /**
     * TASK-MONO-752 — the version this IT is about. Both the upgrade and the re-run are pinned to it: with no
     * target, any later migration (V22 seller members on) would run too, and «only V21 applies» / «re-run is a
     * no-op» would start asserting «no newer file exists» instead (the same fix
     * {@code ConsumerPoolMigrationOnExistingVolumeIntegrationTest} needed in account-service).
     */
    private static final String GOODS_SEED_VERSION = "21";
    private static final String GOODS_CATEGORY = "a0000000-0000-0000-0000-000000000008";
    private static final String OPERATOR_PRODUCT = "b0000000-0000-0000-0000-0000000007a9";

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("product_db")
            .withUsername("product_user")
            .withPassword("product_pass");

    private MigrateResult upgrade;
    private List<String> productsBefore;

    @BeforeAll
    void migrateExistingVolume() throws SQLException {
        // 1. the volume as main left it before this ticket: every migration up to V20 …
        flyway(LAST_PRE_GOODS_VERSION).migrate();
        assertThat(column("SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success"))
                .containsExactly(LAST_PRE_GOODS_VERSION);

        // 2. … holding data written after V20: an operator-registered product (with a variant) on the old catalogue.
        exec("INSERT INTO products (id, name, description, price, status, category_id, version, created_at, updated_at, "
                + "tenant_id, seller_id) VALUES ('" + OPERATOR_PRODUCT + "', '운영자 등록 상품', NULL, 10000, 'ON_SALE', "
                + "'a0000000-0000-0000-0000-000000000004', 0, NOW(), NOW(), 'ecommerce', 'default')");
        exec("INSERT INTO product_variants (id, product_id, option_name, stock, additional_price, version, tenant_id) "
                + "VALUES ('c0000000-0000-0000-0000-0000000007a9', '" + OPERATOR_PRODUCT + "', '기본', 3, 0, 0, 'ecommerce')");
        productsBefore = column("SELECT id || '|' || name || '|' || COALESCE(collection_ref, 'NULL') FROM products ORDER BY id");

        // 3. the new version on top of it
        upgrade = flyway(GOODS_SEED_VERSION).migrate();
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private List<String> column(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    @Test
    @DisplayName("V21 만 성공으로 적용된다 (V20 이하가 다시 나오면 «기존 볼륨» 전제가 깨진 것)")
    void onlyV21Applies() {
        assertThat(upgrade.success).isTrue();
        assertThat(upgrade.migrations).extracting(m -> Integer.parseInt(m.version)).containsExactly(21);
    }

    @Test
    @DisplayName("굿즈 18개 · 팬 아티스트 6명 × 3 · tenant ecommerce · seller default")
    void goodsSeeded() throws SQLException {
        assertThat(column("SELECT collection_ref || '|' || COUNT(*) FROM products WHERE category_id = '" + GOODS_CATEGORY
                + "' GROUP BY collection_ref ORDER BY collection_ref"))
                .containsExactly(
                        "0199de80-0000-7000-8000-00000000a001|3",
                        "0199de80-0000-7000-8000-00000000a002|3",
                        "0199de80-0000-7000-8000-00000000a003|3",
                        "0199de80-0000-7000-8000-00000000a004|3",
                        "0199de80-0000-7000-8000-00000000a005|3",
                        "0199de80-0000-7000-8000-00000000a006|3");
        assertThat(column("SELECT DISTINCT tenant_id || '|' || seller_id FROM products WHERE category_id = '"
                + GOODS_CATEGORY + "'")).containsExactly("ecommerce|default");
        assertThat(column("SELECT name || '|' || tenant_id FROM categories WHERE id = '" + GOODS_CATEGORY + "'"))
                .containsExactly("아티스트 굿즈|ecommerce");
    }

    @Test
    @DisplayName("🔴 옵션 21개 전부 tenant_id = ecommerce (V19 의 결함 부류)")
    void variantsCarryTenant() throws SQLException {
        assertThat(column("SELECT v.tenant_id || '|' || COUNT(*) FROM product_variants v JOIN products p ON p.id = v.product_id "
                + "WHERE p.category_id = '" + GOODS_CATEGORY + "' GROUP BY v.tenant_id"))
                .containsExactly("ecommerce|21");
    }

    @Test
    @DisplayName("기존 행은 그대로 — 굿즈가 아닌 상품의 collection_ref 는 NULL 로 남는다")
    void existingRowsUntouched() throws SQLException {
        List<String> after = column("SELECT id || '|' || name || '|' || COALESCE(collection_ref, 'NULL') FROM products "
                + "WHERE category_id <> '" + GOODS_CATEGORY + "' ORDER BY id");
        assertThat(after).isEqualTo(productsBefore);
        assertThat(after).anyMatch(r -> r.startsWith(OPERATOR_PRODUCT));
    }

    @Test
    @DisplayName("재실행은 no-op")
    void secondRunIsNoOp() {
        MigrateResult again = flyway(GOODS_SEED_VERSION).migrate();
        assertThat(again.success).isTrue();
        assertThat(again.migrationsExecuted).isZero();
    }
}
