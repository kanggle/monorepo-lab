package com.example.product.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-752 — {@code seller_members} / {@code seller_member_invitations} must exist on the h2 tree too
 * ({@code db/migration-h2/V15}, twin of postgres {@code V22}). The postgres half is exercised by
 * {@code SellerMemberIntegrationTest} (Docker); this half needs none — same shape as
 * {@link CollectionRefMigrationTwinTest}.
 */
@DisplayName("셀러 구성원 마이그레이션 쌍둥이 (h2 트리) — TASK-MONO-752")
class SellerMembersMigrationTwinTest {

    @Test
    @DisplayName("h2 트리를 적용하면 두 테이블과 그 열이 생긴다")
    void h2Tree_createsSellerMemberTables() throws Exception {
        String url = "jdbc:h2:mem:seller-members-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration-h2").load().migrate();

        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columns(c, "seller_members"))
                    .containsExactlyInAnyOrder("tenant_id", "seller_id", "account_id", "role", "status", "joined_at");
            assertThat(columns(c, "seller_member_invitations"))
                    .containsExactlyInAnyOrder("id", "tenant_id", "seller_id", "email", "token_hash", "status",
                            "expires_at", "invited_by", "created_at", "accepted_at", "accepted_account_id");
        }
    }

    private static List<String> columns(Connection c, String table) throws Exception {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = c.createStatement().executeQuery(
                "SELECT column_name FROM information_schema.columns WHERE table_name = '" + table + "'")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
