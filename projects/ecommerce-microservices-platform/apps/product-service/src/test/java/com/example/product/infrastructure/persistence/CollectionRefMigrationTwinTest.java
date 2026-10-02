package com.example.product.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-749 (ADR-MONO-079 D3) — {@code products.collection_ref} must exist on
 * <b>both</b> Flyway trees: {@code db/migration} (postgres — demo / CI) and
 * {@code db/migration-h2} (the {@code local} standalone profile).
 *
 * <p>Failure Scenario 1 of the ticket is exactly «postgres changed, h2 forgotten».
 * The postgres tree is exercised by the Testcontainers IT
 * ({@code ProductRepositoryIntegrationTest#collectionRef_roundTrips_nullByDefault_andIndexed});
 * that IT never runs the h2 tree, so without this test the h2 half could be
 * missing and every suite would stay green. This test needs no Docker: it runs the
 * real h2 migrations into an in-memory H2 with the same URL flags as
 * {@code application-local.yml} and reads the column back from
 * {@code information_schema}.
 */
@DisplayName("collection_ref 마이그레이션 쌍둥이 (postgres · h2) — TASK-MONO-749")
class CollectionRefMigrationTwinTest {

    @Test
    @DisplayName("h2 트리(db/migration-h2)를 실제로 적용하면 products.collection_ref VARCHAR(64) NULL 이 생긴다")
    void h2Tree_createsCollectionRefColumn() throws Exception {
        String url = "jdbc:h2:mem:collection-ref-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration-h2")
                .load()
                .migrate();

        try (Connection c = DriverManager.getConnection(url, "sa", "");
             ResultSet rs = c.createStatement().executeQuery(
                     "SELECT character_maximum_length, is_nullable FROM information_schema.columns "
                             + "WHERE table_name = 'products' AND column_name = 'collection_ref'")) {
            assertThat(rs.next()).as("h2 트리에 products.collection_ref 가 없다 — V13 을 빠뜨렸다").isTrue();
            assertThat(rs.getLong(1)).isEqualTo(64L);
            assertThat(rs.getString(2)).isEqualTo("YES");
            // 🔴 대조군(AC-2) — 기존 시드 상품은 전부 NULL 이다(백필 없음).
            try (ResultSet seeded = c.createStatement().executeQuery(
                    "SELECT COUNT(*), COUNT(collection_ref) FROM products")) {
                seeded.next();
                assertThat(seeded.getLong(1)).as("h2 시드 상품이 없으면 이 대조는 공허하다").isPositive();
                assertThat(seeded.getLong(2)).isZero();
            }
        }
    }

    @Test
    @DisplayName("두 트리 모두에 collection_ref 를 더하는 마이그레이션이 정확히 하나씩 있다")
    void bothTrees_carryTheColumn() throws Exception {
        assertThat(countAddingMigrations("classpath:db/migration/*.sql")).isEqualTo(1);
        assertThat(countAddingMigrations("classpath:db/migration-h2/*.sql")).isEqualTo(1);
    }

    private static int countAddingMigrations(String pattern) throws Exception {
        int n = 0;
        for (Resource r : new PathMatchingResourcePatternResolver().getResources(pattern)) {
            String sql = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (sql.contains("ADD COLUMN collection_ref")) {
                n++;
            }
        }
        return n;
    }
}
