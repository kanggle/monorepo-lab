package com.example.account.integration;

import com.example.account.application.port.AuthServicePort;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-618 against a real MySQL, pool flag on — {@code POST /internal/consumer-pool/legacy-moves}
 * (account-maintenance-internal.md) over rows written the way the PRE-ADR-MONO-078 code wrote them (plain
 * SQL: a site account with its profile, status history, identity and {@code account_roles}) — the
 * existing-volume shape (AC-6), not rows the new code produced.
 *
 * <p>auth-service is the base class's {@code AuthServicePort} mock: its default (do nothing) is «auth moved
 * the credential»; a stubbed throw is «auth refused / failed», which is what AC-5 needs.
 *
 * <p>🔴 The MySQL is shared by every integration class of this JVM, so a run also sees site accounts other
 * classes left behind (and moves the movable ones — harmless, they are leftovers). Every assertion here is
 * therefore about THIS class's seeded ids, never about totals. A subclass of
 * {@link AbstractConsumerPoolIntegrationTest} on purpose — one shared context (TASK-BE-615 «Too many
 * connections» lesson).
 */
@DisplayName("TASK-BE-618 — 한 사이트 계정을 같은 id 로 풀로 옮긴다 (MySQL, 풀 켜짐)")
class ConsumerPoolLegacyMoveIntegrationTest extends AbstractConsumerPoolIntegrationTest {

    private static final String FAN = "fan-platform";
    private static final String SHOP = "ecommerce";
    private static final String CREATED_AT = "2025-03-04 05:06:07.123456";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    // ── seeding: the pre-pool shape ──────────────────────────────────────────────────────────────

    private record Seeded(String accountId, String identityId, String email) {}

    private static String email(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private Seeded siteAccount(String site, String email, String status, String... roles) {
        String accountId = UUID.randomUUID().toString();
        String identityId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version) "
                + "VALUES (?, ?, ?, 'ACTIVE', ?, NOW(6), 0)", identityId, site, email, CREATED_AT);
        jdbc.update("INSERT INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version) "
                + "VALUES (?, ?, ?, ?, ?, ?, NOW(6), 0)", accountId, identityId, site, email, status, CREATED_AT);
        jdbc.update("INSERT INTO profiles (account_id, tenant_id, locale, timezone, updated_at) "
                + "VALUES (?, ?, 'ko-KR', 'Asia/Seoul', NOW(6))", accountId, site);
        jdbc.update("INSERT INTO account_status_history "
                + "(tenant_id, account_id, from_status, to_status, reason_code, actor_type, actor_id, details, occurred_at) "
                + "VALUES (?, ?, 'ACTIVE', 'ACTIVE', 'USER_LOGIN', 'user', NULL, NULL, ?)", site, accountId, CREATED_AT);
        for (String role : roles) {
            jdbc.update("INSERT INTO account_roles (tenant_id, account_id, role_name, granted_by, granted_at) "
                    + "VALUES (?, ?, ?, NULL, NOW(6))", site, accountId, role);
        }
        return new Seeded(accountId, identityId, email);
    }

    // ── running the endpoint to the end of the cursor ────────────────────────────────────────────

    private record Run(List<String> moved, List<String> failed, Map<String, Integer> skipped, int scanned) {}

    private Run runToEnd() throws Exception {
        List<String> moved = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, Integer> skipped = new HashMap<>();
        int scanned = 0;
        String after = null;
        do {
            String body = after == null ? "{\"limit\": 1000}"
                    : "{\"limit\": 1000, \"afterAccountId\": \"" + after + "\"}";
            String response = mockMvc.perform(post("/internal/consumer-pool/legacy-moves")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            moved.addAll(JsonPath.read(response, "$.movedAccountIds"));
            failed.addAll(JsonPath.read(response, "$.failedAccountIds"));
            Map<String, Integer> page = JsonPath.read(response, "$.skipped");
            page.forEach((k, v) -> skipped.merge(k, v, Integer::sum));
            scanned += (Integer) JsonPath.read(response, "$.scanned");
            after = JsonPath.read(response, "$.nextAfterAccountId");
        } while (after != null);
        return new Run(moved, failed, skipped, scanned);
    }

    // ── assertions on the rows ───────────────────────────────────────────────────────────────────

    private String one(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    /** Every account_db row of the account still has its pre-move shape. */
    private void assertStillSiteShaped(Seeded s, String site, String... roles) {
        assertThat(one("SELECT tenant_id FROM accounts WHERE id = ?", s.accountId())).isEqualTo(site);
        assertThat(one("SELECT tenant_id FROM profiles WHERE account_id = ?", s.accountId())).isEqualTo(site);
        assertThat(one("SELECT tenant_id FROM identities WHERE identity_id = ?", s.identityId())).isEqualTo(site);
        assertThat(jdbc.queryForList("SELECT role_name FROM account_roles WHERE tenant_id = ? AND account_id = ? "
                + "ORDER BY role_name", String.class, site, s.accountId())).containsExactly(roles);
        assertThat(count("SELECT COUNT(*) FROM consumer_site_memberships WHERE account_id = ?", s.accountId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM consumer_site_roles WHERE account_id = ?", s.accountId())).isZero();
    }

    // ── AC-4 · AC-2 · AC-1 · events ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-4/AC-2: 팬 아티스트(FAN+ARTIST) → 같은 id 로 풀 · 옮길 행 전부 · 역할은 consumer_site_roles · 멤버십 ACTIVE(consented_at=created_at) · account.created 없음")
    void movesFanArtist_everyRow_sameId_noEvent() throws Exception {
        Seeded artist = siteAccount(FAN, email("618-artist"), "ACTIVE", "ARTIST", "FAN");

        Run run = runToEnd();

        assertThat(run.moved()).contains(artist.accountId());
        String id = artist.accountId();
        // AC-2: the id did not change — artists.account_id (fan-platform DB) points at the same row.
        assertThat(count("SELECT COUNT(*) FROM accounts WHERE id = ?", id)).isEqualTo(1);
        // AC-4: every row that is moved, moved — and no row of the account is left on the site.
        assertThat(one("SELECT tenant_id FROM accounts WHERE id = ?", id)).isEqualTo("consumer-pool");
        assertThat(one("SELECT tenant_id FROM profiles WHERE account_id = ?", id)).isEqualTo("consumer-pool");
        assertThat(one("SELECT tenant_id FROM identities WHERE identity_id = ?", artist.identityId()))
                .isEqualTo("consumer-pool");
        assertThat(one("SELECT identity_id FROM accounts WHERE id = ?", id))
                .as("same identity row — credentials.identity_id stays valid").isEqualTo(artist.identityId());
        assertThat(count("SELECT COUNT(*) FROM accounts WHERE id = ? AND tenant_id = ?", id, FAN)).isZero();
        assertThat(count("SELECT COUNT(*) FROM profiles WHERE account_id = ? AND tenant_id = ?", id, FAN)).isZero();
        assertThat(count("SELECT COUNT(*) FROM identities WHERE identity_id = ? AND tenant_id = ?",
                artist.identityId(), FAN)).isZero();
        assertThat(count("SELECT COUNT(*) FROM account_roles WHERE account_id = ?", id)).isZero();
        assertThat(jdbc.queryForList("SELECT role_name FROM consumer_site_roles WHERE account_id = ? "
                + "AND site_tenant_id = ? ORDER BY role_name", String.class, id, FAN))
                .containsExactly("ARTIST", "FAN");
        assertThat(count("SELECT COUNT(*) FROM consumer_site_memberships m JOIN accounts a ON a.id = m.account_id "
                + "WHERE m.account_id = ? AND m.site_tenant_id = ? AND m.status = 'ACTIVE' "
                + "AND m.consented_at = a.created_at", id, FAN))
                .as("signing up was consenting: consented_at = the account's creation time").isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM consumer_site_memberships WHERE account_id = ?", id)).isEqualTo(1);
        // account_status_history is append-only (DB trigger) — deliberately NOT moved; reads are by account_id.
        assertThat(one("SELECT tenant_id FROM account_status_history WHERE account_id = ?", id)).isEqualTo(FAN);
        // auth-service moved the credential (last step) exactly once.
        verify(authServicePort).moveCredentialToConsumerPool(id, FAN);
        // No event: the account was already usable on the site (account-events.md).
        assertThat(count("SELECT COUNT(*) FROM account_outbox WHERE aggregate_id = ?", id)).isZero();

        // The site still finds it (§ 5) and its role read is the same set as before the move (정정 ⑤).
        mockMvc.perform(get("/internal/tenants/{t}/accounts/{a}/roles", FAN, id).header("X-Tenant-Id", FAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("ARTIST"))
                .andExpect(jsonPath("$.roles[1]").value("FAN"));
        mockMvc.perform(get("/internal/tenants/{t}/consumer-members/{a}", FAN, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.siteRoles[0]").value("ARTIST"));
        // Another site: not a member → no roles, no membership (no flattening).
        mockMvc.perform(get("/internal/tenants/{t}/accounts/{a}/roles", SHOP, id).header("X-Tenant-Id", SHOP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty());

        // Re-running: the moved account is no longer a candidate.
        Run again = runToEnd();
        assertThat(again.moved()).doesNotContain(id);
        verify(authServicePort, times(1)).moveCredentialToConsumerPool(eq(id), anyString());
    }

    @Test
    @DisplayName("AC-1: 셀러(SELLER) · 두 사이트 계정 · DELETED · 같은 이메일 풀 계정은 옮기지 않는다 — 행 무변경, auth 호출 없음")
    void sellerTwoSiteDeletedPoolTwin_notMoved() throws Exception {
        Seeded seller = siteAccount(SHOP, email("618-seller"), "ACTIVE", "SELLER");
        String twoSiteEmail = email("618-two-site");
        Seeded twoOnFan = siteAccount(FAN, twoSiteEmail, "ACTIVE");
        Seeded twoOnShop = siteAccount(SHOP, twoSiteEmail, "ACTIVE");
        Seeded deleted = siteAccount(FAN, email("618-deleted"), "DELETED");
        String twinEmail = email("618-pool-twin");
        Seeded siteTwin = siteAccount(FAN, twinEmail, "ACTIVE");
        jdbc.update("INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, version) "
                + "VALUES (?, 'consumer-pool', ?, 'ACTIVE', NOW(6), NOW(6), 0)", UUID.randomUUID().toString(), twinEmail);

        Run run = runToEnd();

        for (Seeded s : List.of(seller, twoOnFan, twoOnShop, deleted, siteTwin)) {
            assertThat(run.moved()).doesNotContain(s.accountId());
            assertThat(run.failed()).doesNotContain(s.accountId());
            verify(authServicePort, never()).moveCredentialToConsumerPool(eq(s.accountId()), anyString());
        }
        assertStillSiteShaped(seller, SHOP, "SELLER");
        assertStillSiteShaped(twoOnFan, FAN);
        assertStillSiteShaped(twoOnShop, SHOP);
        assertStillSiteShaped(deleted, FAN);
        assertStillSiteShaped(siteTwin, FAN);
        assertThat(run.skipped().get("SELLER")).isGreaterThanOrEqualTo(1);
        assertThat(run.skipped().get("TWO_SITE")).isGreaterThanOrEqualTo(2);
        assertThat(run.skipped().get("POOL_EMAIL_EXISTS")).isGreaterThanOrEqualTo(1);
    }

    // ── AC-5 ────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-5: auth 실패(마지막 단계) → 그 계정은 아무것도 안 옮겨짐(반쯤 옮겨진 계정 없음) · 다음 실행에서 완결 · 그다음 실행은 후보 아님")
    void authFailure_rollsBackWholeAccount_thenRerunCompletes() throws Exception {
        Seeded artist = siteAccount(FAN, email("618-auth-down"), "ACTIVE", "ARTIST", "FAN");
        Seeded bystander = siteAccount(FAN, email("618-bystander"), "ACTIVE");
        doThrow(new AuthServicePort.AuthServiceUnavailable("auth-service is unavailable", null))
                .when(authServicePort).moveCredentialToConsumerPool(artist.accountId(), FAN);

        Run failedRun = runToEnd();

        assertThat(failedRun.failed()).contains(artist.accountId());
        assertThat(failedRun.moved()).doesNotContain(artist.accountId());
        assertStillSiteShaped(artist, FAN, "ARTIST", "FAN");
        assertThat(failedRun.moved())
                .as("one account's failure does not stop or undo another's move").contains(bystander.accountId());

        // auth-service is back — the same account is still a candidate and now moves completely.
        doNothing().when(authServicePort).moveCredentialToConsumerPool(artist.accountId(), FAN);
        Run rerun = runToEnd();

        assertThat(rerun.moved()).contains(artist.accountId());
        assertThat(one("SELECT tenant_id FROM accounts WHERE id = ?", artist.accountId())).isEqualTo("consumer-pool");
        assertThat(jdbc.queryForList("SELECT role_name FROM consumer_site_roles WHERE account_id = ? ORDER BY role_name",
                String.class, artist.accountId())).containsExactly("ARTIST", "FAN");
        verify(authServicePort, times(2)).moveCredentialToConsumerPool(artist.accountId(), FAN);

        Run third = runToEnd();
        assertThat(third.moved()).doesNotContain(artist.accountId());
        assertThat(third.failed()).doesNotContain(artist.accountId());
        verify(authServicePort, times(2)).moveCredentialToConsumerPool(artist.accountId(), FAN);
    }

    @Test
    @DisplayName("AC-5: auth 거절(409 SOCIAL_LINKED · OPERATOR_FACETED) → 건너뜀으로 집계 · 행 무변경")
    void authRefusal_isSkip_rowsUnchanged() throws Exception {
        Seeded social = siteAccount(FAN, email("618-social"), "ACTIVE");
        Seeded operator = siteAccount(SHOP, email("618-operator"), "ACTIVE");
        doThrow(new AuthServicePort.CredentialPoolMoveRefused(social.accountId(), "SOCIAL_LINKED"))
                .when(authServicePort).moveCredentialToConsumerPool(social.accountId(), FAN);
        doThrow(new AuthServicePort.CredentialPoolMoveRefused(operator.accountId(), "OPERATOR_FACETED"))
                .when(authServicePort).moveCredentialToConsumerPool(operator.accountId(), SHOP);

        Run run = runToEnd();

        assertThat(run.moved()).doesNotContain(social.accountId(), operator.accountId());
        assertThat(run.failed()).doesNotContain(social.accountId(), operator.accountId());
        assertThat(run.skipped().get("SOCIAL_LINKED")).isGreaterThanOrEqualTo(1);
        assertThat(run.skipped().get("OPERATOR_FACETED")).isGreaterThanOrEqualTo(1);
        assertStillSiteShaped(social, FAN);
        assertStillSiteShaped(operator, SHOP);
    }

    @Test
    @DisplayName("limit 범위 밖 → 400 VALIDATION_ERROR")
    void limitOutOfRange_400() throws Exception {
        mockMvc.perform(post("/internal/consumer-pool/legacy-moves")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limit\": 1001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
