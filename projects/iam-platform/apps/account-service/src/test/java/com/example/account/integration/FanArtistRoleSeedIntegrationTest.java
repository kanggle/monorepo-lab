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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-512 (ADR-MONO-059 ACCEPTED — A) — proves the demo artists' {@code ARTIST}
 * grant is real: the {@code migration-dev} seed SQL executes against a real MySQL, and
 * the roles come back out of <b>the endpoints auth-service actually calls at token
 * issuance</b>.
 *
 * <p><b>TASK-MONO-744 (ADR-MONO-078 A) — the artists are consumer-POOL accounts now.</b>
 * Same ids (so {@code artists.account_id} and seed-fan.sh stay valid), tenant
 * {@code consumer-pool}, an ACTIVE {@code fan-platform} membership, and the roles in
 * {@code consumer_site_roles} instead of {@code account_roles}. So the issuance read for
 * these principals is {@code GET /internal/tenants/fan-platform/consumer-members/{id}}
 * ({@code TenantClaimTokenCustomizer#customizeForPoolPrincipal} — token roles = the site
 * seed ∪ {@code siteRoles}); the roles GET (widened by TASK-BE-618 for refresh of a
 * pre-move session) is asserted too, because it is a path that emits a stored set
 * verbatim and is the reason FAN is stored at all (R__06 header).
 *
 * <p><b>Why not a stub.</b> {@code TenantClaimTokenCustomizerTest} pins the issuance
 * half with a stubbed lookup. What no test covered is whether anything ever <em>puts a
 * row there</em> for these accounts — the shape TASK-BE-579 was filed to close on the
 * neighbouring seam.
 *
 * <p><b>Why the seed can fail silently otherwise.</b> R__06 inserts across four tables
 * with FKs ({@code consumer_site_roles} → the membership, V0030) and uses
 * {@code INSERT IGNORE} for idempotence — which is exactly what turns an FK violation, a
 * column-order slip or a stale tenant slug into <em>silence</em> rather than a failed
 * boot. The demo would come up clean and the artist would simply 403 on publish.
 *
 * <h2>🔴 Why this executes the file instead of letting Flyway load it</h2>
 *
 * {@link com.example.testsupport.integration.AbstractIntegrationTest} starts ONE MySQL
 * per JVM and every integration class shares it. Loading the dev seeds through Flyway
 * would write R__01..R__06 into that shared {@code flyway_schema_history}, and the next
 * Spring context configured with the production locations alone would find applied
 * migrations it cannot resolve — a Flyway validation failure in a class that changed
 * nothing. So this reads the migration file, runs its statements directly, then removes
 * its rows. It proves the SQL is valid against real MySQL, the FK order holds, and the
 * rows are readable through the production queries; it makes no claim about Flyway's own
 * wiring (naming, checksum, ordering).
 *
 * <p>Extends {@link AbstractConsumerPoolIntegrationTest} (pool flag on, the shared
 * context) rather than declaring its own context — each extra cached context holds a
 * connection pool against the one MySQL (TASK-BE-615's «Too many connections»).
 */
@DisplayName("TASK-MONO-512 / TASK-MONO-744 fan artist ARTIST-role seed (consumer pool)")
class FanArtistRoleSeedIntegrationTest extends AbstractConsumerPoolIntegrationTest {

    private static final String FAN_TENANT = "fan-platform";
    private static final String POOL_TENANT = "consumer-pool";

    /** The artifact under test — the same file the e2e profile hands to Flyway. */
    private static final Path SEED = Path.of("src", "main", "resources", "db", "migration-dev",
            "R__06_seed_fan_artist_accounts_and_artist_role.sql");

    /** The six artist accounts — same ids as artists.id in infra/demo/seed/seed-fan.sh. */
    private static final List<String> ARTIST_ACCOUNT_IDS = List.of(
            "0199de80-0000-7000-8000-00000000a001",
            "0199de80-0000-7000-8000-00000000a002",
            "0199de80-0000-7000-8000-00000000a003",
            "0199de80-0000-7000-8000-00000000a004",
            "0199de80-0000-7000-8000-00000000a005",
            "0199de80-0000-7000-8000-00000000a006");

    private static final List<String> ARTIST_IDENTITY_IDS = List.of(
            "0199de82-0000-7000-8000-00000000a001",
            "0199de82-0000-7000-8000-00000000a002",
            "0199de82-0000-7000-8000-00000000a003",
            "0199de82-0000-7000-8000-00000000a004",
            "0199de82-0000-7000-8000-00000000a005",
            "0199de82-0000-7000-8000-00000000a006");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void applySeed() throws IOException {
        assertThat(SEED).as("the R__06 seed must be resolvable from this module").exists();
        String sql = Files.readString(SEED, StandardCharsets.UTF_8);

        // Strip line comments before splitting: the header is prose and must never be
        // executed. The file's data carries no ';' (uuids, slugs, emails, role names).
        String statements = Arrays.stream(sql.split("\\R"))
                .filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b);

        int executed = 0;
        for (String statement : statements.split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement);
                executed++;
            }
        }
        // identities · accounts · consumer_site_memberships · consumer_site_roles.
        // A parser that silently matched nothing would leave every assertion below to
        // fail as "no rows", pointing at the seed instead of at this harness.
        assertThat(executed).as("the seed file must contribute executable statements").isEqualTo(4);
    }

    @AfterEach
    void removeSeed() {
        // The MySQL container is shared by every integration class in this JVM, so rows
        // left here become invisible inputs to unrelated suites. Reverse FK order (the
        // membership and its roles also cascade on accounts).
        for (String accountId : ARTIST_ACCOUNT_IDS) {
            jdbc.update("DELETE FROM consumer_site_roles WHERE account_id = ?", accountId);
            jdbc.update("DELETE FROM consumer_site_memberships WHERE account_id = ?", accountId);
            jdbc.update("DELETE FROM accounts WHERE id = ?", accountId);
        }
        for (String identityId : ARTIST_IDENTITY_IDS) {
            jdbc.update("DELETE FROM identities WHERE identity_id = ?", identityId);
        }
    }

    private JsonNode consumerMember(String site, String accountId) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/internal/tenants/{tenantId}/consumer-members/{accountId}", site, accountId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> textList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    /** The roles GET — what a verbatim (non-pool) reader such as a pre-move session's refresh gets. */
    private List<String> rolesOf(String accountId) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/internal/tenants/{tenantId}/accounts/{accountId}/roles",
                                FAN_TENANT, accountId)
                                .header("X-Tenant-Id", FAN_TENANT))
                .andExpect(status().isOk())
                .andReturn();
        return textList(objectMapper.readTree(result.getResponse().getContentAsString()).path("roles"));
    }

    @Test
    @DisplayName("the seed lands — six artist accounts and their identities exist in the consumer pool")
    void seedCreatedTheArtistPoolAccounts() {
        // Asserted separately from the role checks so a seed that never applied fails
        // HERE, naming the cause, instead of surfacing as "the roles list was empty".
        for (String accountId : ARTIST_ACCOUNT_IDS) {
            Integer pool = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM accounts WHERE id = ? AND tenant_id = ?",
                    Integer.class, accountId, POOL_TENANT);
            assertThat(pool)
                    .as("R__06 must have provisioned artist account %s in the pool", accountId)
                    .isEqualTo(1);
            Integer identity = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM identities i JOIN accounts a ON a.identity_id = i.identity_id "
                            + "WHERE a.id = ? AND i.tenant_id = ?",
                    Integer.class, accountId, POOL_TENANT);
            assertThat(identity)
                    .as("the pool account's identities row lives in the pool too (§ 3)")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("issuance read: every artist is an ACTIVE fan-platform member whose site roles are exactly [FAN, ARTIST]")
    void artistAccountsAreFanMembersWithBothSiteRoles() throws Exception {
        for (String accountId : ARTIST_ACCOUNT_IDS) {
            JsonNode member = consumerMember(FAN_TENANT, accountId);
            assertThat(member.path("membershipStatus").asText())
                    .as("no ACTIVE membership ⇒ the issuer mints NO fan token for %s (TASK-BE-615) — "
                            + "the artist could not log into the fan web at all", accountId)
                    .isEqualTo("ACTIVE");
            assertThat(textList(member.path("siteRoles")))
                    .as("account %s is what PublishPostUseCase's ARTIST gate admits", accountId)
                    .containsExactlyInAnyOrder("FAN", "ARTIST");
        }
    }

    @Test
    @DisplayName("verbatim read: the roles GET answers [FAN, ARTIST] too — FAN must be stored, not left to the seed")
    void rolesGetCarriesBothRoles() throws Exception {
        for (String accountId : ARTIST_ACCOUNT_IDS) {
            assertThat(rolesOf(accountId))
                    .as("the roles GET (TASK-BE-618 widening) answers consumer_site_roles to a "
                            + "reader that emits it VERBATIM — without a stored FAN that token "
                            + "would lose FAN for %s", accountId)
                    .containsExactlyInAnyOrder("FAN", "ARTIST");
        }
    }

    @Test
    @DisplayName("the membership is the fan site's only — the artists are not ecommerce members (no flattening)")
    void artistsAreNotStoreMembers() throws Exception {
        JsonNode store = consumerMember("ecommerce", ARTIST_ACCOUNT_IDS.get(0));
        // Control that the instrument answers for this account at all: consumerSite=true
        // means the read resolved the site; the absent status is then a fact about the seed.
        assertThat(store.path("consumerSite").asBoolean()).isTrue();
        assertThat(store.path("membershipStatus").isMissingNode() || store.path("membershipStatus").isNull())
                .as("R__06 joins the artists to fan-platform only")
                .isTrue();
        assertThat(textList(store.path("siteRoles"))).isEmpty();
    }

    @Test
    @DisplayName("re-running the seed is a no-op — INSERT IGNORE, not duplicate rows")
    void seedIsIdempotent() throws Exception {
        applySeed(); // second application, on top of @BeforeEach's

        for (String accountId : ARTIST_ACCOUNT_IDS) {
            assertThat(textList(consumerMember(FAN_TENANT, accountId).path("siteRoles")))
                    .containsExactlyInAnyOrder("FAN", "ARTIST");
        }
        String in = String.join(",", ARTIST_ACCOUNT_IDS.stream().map(id -> "'" + id + "'").toList());
        Integer accounts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE tenant_id = 'consumer-pool' AND id IN (" + in + ")",
                Integer.class);
        Integer memberships = jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumer_site_memberships WHERE account_id IN (" + in + ")",
                Integer.class);
        Integer roles = jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumer_site_roles WHERE account_id IN (" + in + ")",
                Integer.class);
        assertThat(accounts)
                .as("the demo seed is re-run whenever its checksum changes, so a non-idempotent "
                        + "statement here surfaces as a failed boot rather than as drift")
                .isEqualTo(6);
        assertThat(memberships).isEqualTo(6);
        assertThat(roles).isEqualTo(12);
    }

    @Test
    @DisplayName("a pool fan member who is not an artist is NOT granted ARTIST — the grant is targeted, not blanket")
    void aPlainPoolFanIsNotAnArtist() throws Exception {
        // Control that the instrument works: this account exists, is an ACTIVE fan-platform
        // member, and is read through the same query — so an empty site-role list is a fact
        // about the grant, not about the lookup.
        String plainFanAccountId = "0199de70-0000-7000-8000-0000000744ff";
        String identityId = "0199de71-0000-7000-8000-0000000744ff";
        jdbc.update("""
                INSERT IGNORE INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version)
                VALUES (?, 'consumer-pool', 'plain-fan-744@test.local', 'ACTIVE', NOW(6), NOW(6), 0)
                """, identityId);
        jdbc.update("""
                INSERT IGNORE INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version)
                VALUES (?, ?, 'consumer-pool', 'plain-fan-744@test.local', 'ACTIVE', NOW(6), NOW(6), 0)
                """, plainFanAccountId, identityId);
        jdbc.update("""
                INSERT IGNORE INTO consumer_site_memberships (account_id, site_tenant_id, status, consented_at)
                VALUES (?, 'fan-platform', 'ACTIVE', NOW(6))
                """, plainFanAccountId);
        try {
            JsonNode member = consumerMember(FAN_TENANT, plainFanAccountId);
            assertThat(member.path("membershipStatus").asText())
                    .as("the control account must be a member for its empty role list to mean anything")
                    .isEqualTo("ACTIVE");
            assertThat(textList(member.path("siteRoles")))
                    .as("if the seed granted ARTIST to fan members at large, the PublishPostUseCase "
                            + "gate would be open to every logged-in fan")
                    .doesNotContain("ARTIST");
        } finally {
            jdbc.update("DELETE FROM consumer_site_roles WHERE account_id = ?", plainFanAccountId);
            jdbc.update("DELETE FROM consumer_site_memberships WHERE account_id = ?", plainFanAccountId);
            jdbc.update("DELETE FROM accounts WHERE id = ?", plainFanAccountId);
            jdbc.update("DELETE FROM identities WHERE identity_id = ?", identityId);
        }
    }
}
