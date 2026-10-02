package com.example.account.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-744 (ADR-MONO-078 A) — the demo consumer {@code demo@demo.com} is seeded as ONE
 * consumer-pool account pre-joined to both consumer sites (account-service migration-dev R__05).
 *
 * <p>What this proves against a real MySQL, through the production read paths:
 * <ul>
 *   <li>the account and its identity land in {@code consumer-pool} under the surviving id
 *       {@code …ec01};</li>
 *   <li>the issuance read auth-service uses for a pool principal
 *       ({@code GET /internal/tenants/{site}/consumer-members/{id}}) answers ACTIVE for BOTH
 *       {@code fan-platform} and {@code ecommerce} — so the issuer mints a token for both sites with
 *       the same {@code sub} and the first-visit consent screen never shows for the demo account
 *       (AC-1, by construction; the live click path is AC-4);</li>
 *   <li>the console account search (multi-tenancy.md § 소비자 계정 풀 § 5) finds the account when the
 *       operator is on {@code ecommerce} and on {@code fan-platform} — the account row is in the pool,
 *       so a tenant-only query would have lost it.</li>
 * </ul>
 *
 * <p>Same harness as {@link FanArtistRoleSeedIntegrationTest} — the file is executed directly, not
 * through Flyway (see that class for why). 🔴 Only the consumer statements run: R__05 also seeds the
 * {@code demo-corp} tenant and its five subscriptions, and inserting (then deleting) a tenant in the
 * one MySQL every integration class shares would make this class an invisible input to any suite
 * that reads the tenant list. The skipped statements are counted, so a reshaped file fails here
 * instead of silently running less.
 */
@DisplayName("TASK-MONO-744 demo consumer seed — one pool account, member of both consumer sites")
class DemoConsumerPoolSeedIntegrationTest extends AbstractConsumerPoolIntegrationTest {

    private static final String DEMO_EMAIL = "demo@demo.com";
    private static final String DEMO_ACCOUNT_ID = "0199de70-0000-7000-8000-00000000ec01";
    private static final String DEMO_IDENTITY_ID = "0199de71-0000-7000-8000-00000000ec01";

    /** The artifact under test — the same file the e2e profile hands to Flyway. */
    private static final Path SEED = Path.of("src", "main", "resources", "db", "migration-dev",
            "R__05_seed_demo_corp_tenant_and_consumer_accounts.sql");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void applyConsumerStatements() throws IOException {
        assertThat(SEED).as("the R__05 seed must be resolvable from this module").exists();
        String sql = Files.readString(SEED, StandardCharsets.UTF_8);
        String statements = Arrays.stream(sql.split("\\R"))
                .filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b);

        int executed = 0;
        int skipped = 0;
        for (String statement : statements.split(";")) {
            if (statement.isBlank()) {
                continue;
            }
            String normalized = statement.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (normalized.startsWith("insert ignore into tenants ")
                    || normalized.startsWith("insert ignore into tenant_domain_subscription ")) {
                skipped++;
                continue;
            }
            jdbc.execute(statement);
            executed++;
        }
        // identities · accounts · two memberships run; demo-corp + its subscriptions are skipped.
        assertThat(executed).as("consumer statements executed").isEqualTo(4);
        assertThat(skipped).as("demo-corp statements skipped (class javadoc)").isEqualTo(2);
    }

    @AfterEach
    void removeSeed() {
        jdbc.update("DELETE FROM consumer_site_roles WHERE account_id = ?", DEMO_ACCOUNT_ID);
        jdbc.update("DELETE FROM consumer_site_memberships WHERE account_id = ?", DEMO_ACCOUNT_ID);
        jdbc.update("DELETE FROM accounts WHERE id = ?", DEMO_ACCOUNT_ID);
        jdbc.update("DELETE FROM identities WHERE identity_id = ?", DEMO_IDENTITY_ID);
    }

    private JsonNode get200(String url, Object... vars) throws Exception {
        MvcResult result = mockMvc.perform(get(url, vars))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("the demo account and its identity are ONE row each, in consumer-pool, under …ec01")
    void demoAccountIsOnePoolAccount() {
        Integer pool = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE id = ? AND tenant_id = 'consumer-pool' AND email = ?",
                Integer.class, DEMO_ACCOUNT_ID, DEMO_EMAIL);
        assertThat(pool).as("R__05 must seed the demo consumer as a pool account").isEqualTo(1);
        Integer identity = jdbc.queryForObject(
                "SELECT COUNT(*) FROM identities WHERE identity_id = ? AND tenant_id = 'consumer-pool'",
                Integer.class, DEMO_IDENTITY_ID);
        assertThat(identity).isEqualTo(1);
        // § 2 coexistence: no SITE account with the demo email may sit beside the pool one. The old
        // fan-platform row (…fa02) is gone from the seed; the old ecommerce row became this account.
        Integer siteAccounts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE email = ? AND tenant_id IN ('ecommerce', 'fan-platform')",
                Integer.class, DEMO_EMAIL);
        assertThat(siteAccounts)
                .as("a site account beside the pool account is the state § 2 forbids")
                .isZero();
    }

    @Test
    @DisplayName("issuance read: ACTIVE member of fan-platform AND ecommerce, no stored site roles (seed roles come at issuance)")
    void demoAccountIsPreJoinedToBothSites() throws Exception {
        for (String site : new String[] {"fan-platform", "ecommerce"}) {
            JsonNode member = get200("/internal/tenants/{tenantId}/consumer-members/{accountId}",
                    site, DEMO_ACCOUNT_ID);
            assertThat(member.path("consumerSite").asBoolean()).as("%s is a consumer site", site).isTrue();
            assertThat(member.path("membershipStatus").asText())
                    .as("no ACTIVE membership of %s ⇒ the demo account meets the consent screen there "
                            + "(or, for prompt=none, no token) instead of passing through (AC-1)", site)
                    .isEqualTo("ACTIVE");
            assertThat(member.path("siteRoles").size())
                    .as("CUSTOMER / FAN are seed roles added per site at issuance — never stored")
                    .isZero();
        }
        Integer consentedAtCreation = jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumer_site_memberships m JOIN accounts a ON a.id = m.account_id "
                        + "WHERE m.account_id = ? AND m.consented_at = a.created_at",
                Integer.class, DEMO_ACCOUNT_ID);
        assertThat(consentedAtCreation)
                .as("consented_at = the account's created_at, TASK-BE-618's value for the same shape")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("console account search finds the demo account on ecommerce AND on fan-platform (§ 5)")
    void consoleSearchFindsTheDemoAccountOnBothSites() throws Exception {
        for (String site : new String[] {"ecommerce", "fan-platform"}) {
            MvcResult result = mockMvc.perform(get("/internal/accounts")
                            .param("tenantId", site).param("email", DEMO_EMAIL))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(page.path("totalElements").asInt())
                    .as("an operator switched to %s must still see the demo shopper — its row is in "
                            + "consumer-pool, so only the § 5 widened query can find it", site)
                    .isEqualTo(1);
            assertThat(page.path("content").get(0).path("id").asText()).isEqualTo(DEMO_ACCOUNT_ID);
        }
    }

    @Test
    @DisplayName("re-running the consumer statements is a no-op")
    void seedIsIdempotent() throws Exception {
        applyConsumerStatements();

        Integer accounts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE email = ? AND tenant_id = 'consumer-pool'",
                Integer.class, DEMO_EMAIL);
        Integer memberships = jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumer_site_memberships WHERE account_id = ?",
                Integer.class, DEMO_ACCOUNT_ID);
        assertThat(accounts).isEqualTo(1);
        assertThat(memberships).isEqualTo(2);
    }

    @Test
    @DisplayName("on a volume where …ec01 is still an ecommerce SITE account, the guarded membership statements write nothing")
    void membershipStatementsSkipAStillSiteAccount() throws IOException {
        // Reproduce an existing local volume: the pre-744 shape of …ec01. removeSeed() of the
        // @BeforeEach run first, then the old row, then the file again.
        removeSeed();
        jdbc.update("""
                INSERT INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version)
                VALUES (?, 'ecommerce', ?, 'ACTIVE', NOW(6), NOW(6), 0)
                """, DEMO_IDENTITY_ID, DEMO_EMAIL);
        jdbc.update("""
                INSERT INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version)
                VALUES (?, ?, 'ecommerce', ?, 'ACTIVE', NOW(6), NOW(6), 0)
                """, DEMO_ACCOUNT_ID, DEMO_IDENTITY_ID, DEMO_EMAIL);

        applyConsumerStatements();

        Integer site = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE id = ? AND tenant_id = 'ecommerce'",
                Integer.class, DEMO_ACCOUNT_ID);
        assertThat(site).as("INSERT IGNORE leaves the old site row in place — the documented caveat").isEqualTo(1);
        Integer memberships = jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumer_site_memberships WHERE account_id = ?",
                Integer.class, DEMO_ACCOUNT_ID);
        assertThat(memberships)
                .as("a membership on a still-site account would make TASK-BE-618's mover (a plain "
                        + "INSERT) fail for it — the statements must be guarded on consumer-pool")
                .isZero();
    }
}
