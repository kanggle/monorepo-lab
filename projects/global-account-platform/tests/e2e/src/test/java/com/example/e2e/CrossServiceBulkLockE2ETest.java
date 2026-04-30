package com.example.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Cross-service bulk-lock E2E (TASK-BE-041c §3, depends on 041b consumer).
 *
 * Flow:
 *  1. Obtain authenticated operator access token (reusing enrolled dev SUPER_ADMIN)
 *  2. Seed 2 accounts directly in account-service DB (host-mapped MySQL port)
 *  3. POST /api/admin/accounts/bulk-lock → per-row outcome=LOCKED
 *  4. Assert accounts.status=LOCKED in account_db
 *  5. Awaitility poll → security_db.account_lock_history 2 rows
 */
class CrossServiceBulkLockE2ETest extends E2EBase {

    @Test
    @DisplayName("bulk-lock: admin 명령 → account-service DB 잠금 → security-service account_lock_history 전파")
    void bulk_lock_propagates_to_account_and_security_dbs() throws Exception {
        String accessToken = OperatorSessionHelper.loginAsDevSuperAdmin();

        // Seed two distinct accounts in account_db
        String accountId1 = UUID.randomUUID().toString();
        String accountId2 = UUID.randomUUID().toString();
        String email1 = "bulk-lock-" + accountId1.substring(0, 8) + "@example.com";
        String email2 = "bulk-lock-" + accountId2.substring(0, 8) + "@example.com";
        seedAccount(accountId1, email1);
        seedAccount(accountId2, email2);

        // Invoke bulk-lock
        Response bulk = admin()
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Operator-Reason", "e2e-cross-service-bulk-lock")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(Map.of(
                        "accountIds", List.of(accountId1, accountId2),
                        "reason", "e2e bulk-lock cross-service smoke"
                ))
                .post("/api/admin/accounts/bulk-lock");
        assertThat(bulk.statusCode()).isEqualTo(200);
        JsonNode results = MAPPER.readTree(bulk.asString()).path("results");
        assertThat(results.isArray()).isTrue();
        assertThat(results.size()).isEqualTo(2);
        for (JsonNode r : results) {
            assertThat(r.path("outcome").asText()).isEqualTo("LOCKED");
        }

        // Account-service DB: both accounts LOCKED
        assertThat(readAccountStatus(accountId1)).isEqualTo("LOCKED");
        assertThat(readAccountStatus(accountId2)).isEqualTo("LOCKED");

        // Security-service account_lock_history: 2 rows (Awaitility up to 10s)
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            long cnt = countAccountLockHistory(List.of(accountId1, accountId2));
            assertThat(cnt).isEqualTo(2L);
        });
    }

    private static void seedAccount(String accountId, String email) throws Exception {
        Instant now = Instant.now();
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:13306/account_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "account_user", "account_pass")) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO accounts(id,email,status,created_at,updated_at,version) " +
                    "VALUES(?,?,?,?,?,0)")) {
                ps.setString(1, accountId);
                ps.setString(2, email);
                ps.setString(3, "ACTIVE");
                ps.setTimestamp(4, java.sql.Timestamp.from(now));
                ps.setTimestamp(5, java.sql.Timestamp.from(now));
                ps.executeUpdate();
            }
        }
    }

    private static String readAccountStatus(String accountId) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:13306/account_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "account_user", "account_pass");
             PreparedStatement ps = c.prepareStatement("SELECT status FROM accounts WHERE id=?")) {
            ps.setString(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static long countAccountLockHistory(List<String> accountIds) throws Exception {
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < accountIds.size(); i++) {
            if (i > 0) placeholders.append(',');
            placeholders.append('?');
        }
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:13306/security_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "security_user", "security_pass");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM account_lock_history WHERE account_id IN (" + placeholders + ")")) {
            for (int i = 0; i < accountIds.size(); i++) {
                ps.setString(i + 1, accountIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }
}
