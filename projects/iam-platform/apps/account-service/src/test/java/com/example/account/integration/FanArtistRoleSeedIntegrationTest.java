package com.example.account.integration;

import com.example.account.application.port.AuthServicePort;
import com.example.account.infrastructure.outbox.AccountOutboxPublisher;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

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
 * the roles come back out of <b>the endpoint auth-service actually calls at token
 * issuance</b>.
 *
 * <p><b>Why not a stub.</b> {@code TenantClaimTokenCustomizerTest} already pins the other
 * half — stored {@code account_roles} are emitted verbatim into the {@code roles} claim,
 * and an empty stored set falls to {@code RoleSeedPolicy}. What no test covered is whether
 * anything ever <em>puts a row there</em> for these accounts. Stubbing
 * {@code listAccountRoles} to return {@code ["ARTIST"]} and watching the claim appear would
 * assert the assumption instead of the fact — the shape TASK-BE-579 was filed to close on
 * the neighbouring seam ({@code V0032}'s scope grant, verified only by consumers that
 * signed their own tokens).
 *
 * <p><b>Why the seed can fail silently otherwise.</b> V9006 inserts across three tables
 * with a composite FK ({@code account_roles} → {@code accounts} + {@code tenants}, V0013)
 * and uses {@code INSERT IGNORE} for idempotence — which is exactly what turns an FK
 * violation, a column-order slip or a stale tenant slug into <em>silence</em> rather than a
 * failed boot. The demo would come up clean and the artist would simply 403 on publish.
 *
 * <h2>🔴 Why this executes the file instead of letting Flyway load it</h2>
 *
 * The obvious shape — {@code spring.flyway.locations=db/migration,db/migration-dev} via
 * {@code @DynamicPropertySource} — is <b>wrong here, and it fails loudly for the next
 * class rather than for this one</b>. {@link AbstractIntegrationTest} starts ONE MySQL per
 * JVM and every integration class shares it. Loading the dev band would write V9001..V9006
 * into that shared {@code flyway_schema_history}, and the next Spring context configured
 * with the production locations alone would find applied migrations it cannot resolve —
 * Flyway validation failure, in a class that changed nothing.
 *
 * <p>So this reads the migration file and runs its statements directly, then removes its
 * rows. What that still proves: the SQL is valid against real MySQL, the FK order holds,
 * and the roles are readable through the production query. What it does NOT prove — and no
 * claim is made — is Flyway's own wiring (naming, checksum, ordering); that is the
 * version-band convention, covered by every other dev seed already in the directory.
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TASK-MONO-512 fan artist ARTIST-role seed")
class FanArtistRoleSeedIntegrationTest extends AbstractIntegrationTest {

    private static final String FAN_TENANT = "fan-platform";

    /** The artifact under test — the same file the e2e profile hands to Flyway. */
    private static final Path SEED = Path.of("src", "main", "resources", "db", "migration-dev",
            "V9006__seed_fan_artist_accounts_and_artist_role.sql");

    /** The three artist accounts — same ids as artists.id in infra/demo/seed/seed-fan.sh. */
    private static final List<String> ARTIST_ACCOUNT_IDS = List.of(
            "0199de80-0000-7000-8000-00000000a001",
            "0199de80-0000-7000-8000-00000000a002",
            "0199de80-0000-7000-8000-00000000a003");

    private static final List<String> ARTIST_IDENTITY_IDS = List.of(
            "0199de82-0000-7000-8000-00000000a001",
            "0199de82-0000-7000-8000-00000000a002",
            "0199de82-0000-7000-8000-00000000a003");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthServicePort authServicePort;
    @MockitoBean @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;
    @MockitoBean private AccountOutboxPublisher accountOutboxPublisher;

    @BeforeEach
    void applySeed() throws IOException {
        assertThat(SEED).as("the V9006 seed must be resolvable from this module").exists();
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
        // A parser that silently matched nothing would leave every assertion below to
        // fail as "no rows", pointing at the seed instead of at this harness.
        assertThat(executed).as("the seed file must contribute executable statements").isEqualTo(3);
    }

    @AfterEach
    void removeSeed() {
        // The MySQL container is shared by every integration class in this JVM
        // (AbstractIntegrationTest), so rows left here become invisible inputs to
        // unrelated suites. Reverse FK order; account_roles also cascades on accounts.
        for (String accountId : ARTIST_ACCOUNT_IDS) {
            jdbc.update("DELETE FROM account_roles WHERE account_id = ?", accountId);
            jdbc.update("DELETE FROM accounts WHERE id = ?", accountId);
        }
        for (String identityId : ARTIST_IDENTITY_IDS) {
            jdbc.update("DELETE FROM identities WHERE identity_id = ?", identityId);
        }
    }

    /** Reads roles through the exact endpoint auth-service's AccountServiceClient calls. */
    private List<String> rolesOf(String accountId) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/internal/tenants/{tenantId}/accounts/{accountId}/roles",
                                FAN_TENANT, accountId)
                                .header("X-Tenant-Id", FAN_TENANT))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> roles = new ArrayList<>();
        body.path("roles").forEach(node -> roles.add(node.asText()));
        return roles;
    }

    @Test
    @DisplayName("the seed lands — three artist accounts exist in the fan tenant")
    void seedCreatedTheArtistAccounts() {
        // Asserted separately from the role checks so a seed that never applied fails
        // HERE, naming the cause, instead of surfacing as "the roles list was empty".
        for (String accountId : ARTIST_ACCOUNT_IDS) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM accounts WHERE id = ? AND tenant_id = ?",
                    Integer.class, accountId, FAN_TENANT);
            assertThat(count)
                    .as("V9006 must have provisioned artist account %s", accountId)
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("every artist account resolves to exactly [FAN, ARTIST] through the issuance query")
    void artistAccountsCarryBothRoles() throws Exception {
        for (String accountId : ARTIST_ACCOUNT_IDS) {
            assertThat(rolesOf(accountId))
                    .as("account %s is what PublishPostUseCase's ARTIST gate admits — and "
                            + "FAN must ride along, because the stored set REPLACES the "
                            + "RoleSeedPolicy [FAN] default rather than extending it", accountId)
                    .containsExactlyInAnyOrder("FAN", "ARTIST");
        }
    }

    @Test
    @DisplayName("re-running the seed is a no-op — INSERT IGNORE, not duplicate rows")
    void seedIsIdempotent() throws Exception {
        applySeed(); // second application, on top of @BeforeEach's

        for (String accountId : ARTIST_ACCOUNT_IDS) {
            assertThat(rolesOf(accountId)).containsExactlyInAnyOrder("FAN", "ARTIST");
        }
        Integer accounts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE tenant_id = ? AND id IN (?, ?, ?)",
                Integer.class, FAN_TENANT,
                ARTIST_ACCOUNT_IDS.get(0), ARTIST_ACCOUNT_IDS.get(1), ARTIST_ACCOUNT_IDS.get(2));
        assertThat(accounts)
                .as("the demo seed is re-run on every `demo-up`, so a non-idempotent "
                        + "statement here surfaces as a failed boot rather than as drift")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("a non-artist fan account is NOT granted ARTIST — the grant is targeted, not blanket")
    void aPlainFanAccountIsNotAnArtist() throws Exception {
        // Control that the instrument works: this account exists and is reachable through
        // the same query, so an empty list is a fact about the grant, not about the lookup.
        String plainFanAccountId = "0199de70-0000-7000-8000-0000000512ff";
        String identityId = "0199de71-0000-7000-8000-0000000512ff";
        jdbc.update("""
                INSERT IGNORE INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version)
                VALUES (?, ?, 'plain-fan-512@test.local', 'ACTIVE', NOW(6), NOW(6), 0)
                """, identityId, FAN_TENANT);
        jdbc.update("""
                INSERT IGNORE INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version)
                VALUES (?, ?, ?, 'plain-fan-512@test.local', 'ACTIVE', NOW(6), NOW(6), 0)
                """, plainFanAccountId, identityId, FAN_TENANT);
        try {
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM accounts WHERE id = ? AND tenant_id = ?",
                    Integer.class, plainFanAccountId, FAN_TENANT);
            assertThat(exists).as("the control account must exist for its empty role list to mean anything")
                    .isEqualTo(1);

            assertThat(rolesOf(plainFanAccountId))
                    .as("if the seed granted ARTIST to fan-platform accounts at large, the "
                            + "PublishPostUseCase gate would be open to every logged-in fan "
                            + "and AC-4's negative control would be vacuous")
                    .doesNotContain("ARTIST");
        } finally {
            jdbc.update("DELETE FROM account_roles WHERE account_id = ?", plainFanAccountId);
            jdbc.update("DELETE FROM accounts WHERE id = ?", plainFanAccountId);
            jdbc.update("DELETE FROM identities WHERE identity_id = ?", identityId);
        }
    }
}
