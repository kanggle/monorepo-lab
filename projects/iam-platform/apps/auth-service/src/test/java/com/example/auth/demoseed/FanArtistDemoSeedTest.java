package com.example.auth.demoseed;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.password.Argon2idPasswordHasher;
import com.example.security.password.PasswordHasher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TASK-MONO-512 (ADR-MONO-059 ACCEPTED — A) — guards the demo artists' identity.
 *
 * <p><b>Why this test exists at all.</b> The thing this ticket delivers is not a
 * code path; it is an <em>agreement between three files that no compiler, no
 * foreign key and no other test compares</em>:
 *
 * <ol>
 *   <li>{@code auth-service migration-dev V9002} — the credential (who can log in),</li>
 *   <li>{@code account-service migration-dev V9006} — the account + the {@code ARTIST}
 *       grant (what the token will say), and</li>
 *   <li>{@code infra/demo/seed/seed-fan.sh} — {@code artists.id} / {@code account_id}
 *       (who the feed join thinks the author is).</li>
 * </ol>
 *
 * <p>They live in two services and one shell script, in three different databases
 * (MySQL {@code auth_db}, MySQL {@code account_db}, Postgres {@code fanplatform_artist}),
 * so nothing can declare a constraint across them. If they drift, every service
 * still starts, every suite stays green, and the only symptom is that the demo's
 * artist posts stop reaching the follower feed — which reads as a feed bug, three
 * layers away from the cause. TASK-FAN-BE-045 hit the same shape once already
 * (a route keyed on the entity id while the follow was keyed on the account id,
 * invisible because the demo values happened to be equal).
 *
 * <p><b>It reads the artifacts, never a copy.</b> Following
 * {@link DemoSeedCredentialTest}: the ids, emails, hashes and role names below are
 * parsed out of the seed files themselves. A test that restated them as its own
 * constants would verify the restatement — the exact failure mode it is here to
 * prevent.
 */
@DisplayName("TASK-MONO-512 fan artist demo seed")
class FanArtistDemoSeedTest {

    private static final String DEMO_PASSWORD = "Demo1234!";
    private static final String FAN_TENANT = "fan-platform";

    private static final String CREDENTIAL_SEED =
            "db/migration-dev/V9002__seed_fan_artist_credentials.sql";

    /** Sibling module — resolved from this module's directory (Gradle test workingDir). */
    private static final Path ACCOUNT_SEED = Path.of("..", "account-service", "src", "main",
            "resources", "db", "migration-dev",
            "V9006__seed_fan_artist_accounts_and_artist_role.sql");

    /**
     * Repo root, four levels up (auth-service → apps → iam-platform → projects).
     * The demo seed is shared infrastructure; this test only reads it.
     */
    private static final Path FAN_DEMO_SEED = Path.of("..", "..", "..", "..",
            "infra", "demo", "seed", "seed-fan.sh");

    /** V9002 tuples: ('<tenant>', '<accountId>', '<email>', '<hash>', ... */
    private static final Pattern CREDENTIAL_ROW = Pattern.compile(
            "\\(\\s*'([a-z-]+)'\\s*,\\s*'([0-9a-f-]{36})'\\s*,\\s*'([^']+)'\\s*,\\s*'(\\$argon2id\\$[^']+)'",
            Pattern.MULTILINE);

    /** V9006 accounts tuples: ('<accountId>', '<identityId>', '<tenant>', '<email>', ... */
    private static final Pattern ACCOUNT_ROW = Pattern.compile(
            "\\(\\s*'([0-9a-f-]{36})'\\s*,\\s*'([0-9a-f-]{36})'\\s*,\\s*'([a-z-]+)'\\s*,\\s*'([^']+)'",
            Pattern.MULTILINE);

    /** V9006 account_roles tuples: ('<tenant>', '<accountId>', '<roleName>', NULL, ... */
    private static final Pattern ROLE_ROW = Pattern.compile(
            "\\(\\s*'([a-z-]+)'\\s*,\\s*'([0-9a-f-]{36})'\\s*,\\s*'([A-Z][A-Z0-9_]*)'\\s*,\\s*NULL",
            Pattern.MULTILINE);

    /** seed-fan.sh: ARTIST_A="0199de80-..." */
    private static final Pattern SEED_ARTIST_ID = Pattern.compile(
            "^ARTIST_([A-C])=\"([0-9a-f-]{36})\"", Pattern.MULTILINE);

    private final PasswordHasher hasher = new Argon2idPasswordHasher();

    private record SeededCredential(String tenantId, String accountId, String email, String hash) {}

    private record SeededAccount(String accountId, String identityId, String tenantId, String email) {}

    private static String readClasspath(String resource) throws IOException {
        try (var in = FanArtistDemoSeedTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("seed migration %s must be on the classpath", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readSibling(Path path, String what) throws IOException {
        assertThat(path).as("%s must be resolvable from this module", what).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static List<SeededCredential> parseCredentials() throws IOException {
        Matcher m = CREDENTIAL_ROW.matcher(readClasspath(CREDENTIAL_SEED));
        List<SeededCredential> rows = new ArrayList<>();
        while (m.find()) {
            rows.add(new SeededCredential(m.group(1), m.group(2), m.group(3), m.group(4)));
        }
        return rows;
    }

    private static List<SeededAccount> parseAccounts() throws IOException {
        Matcher m = ACCOUNT_ROW.matcher(readSibling(ACCOUNT_SEED, "the account-service artist seed"));
        List<SeededAccount> rows = new ArrayList<>();
        while (m.find()) {
            rows.add(new SeededAccount(m.group(1), m.group(2), m.group(3), m.group(4)));
        }
        return rows;
    }

    /** accountId → the role names granted to it, in file order. */
    private static Map<String, List<String>> parseGrants() throws IOException {
        Matcher m = ROLE_ROW.matcher(readSibling(ACCOUNT_SEED, "the account-service artist seed"));
        Map<String, List<String>> grants = new LinkedHashMap<>();
        while (m.find()) {
            assertThat(m.group(1)).as("artist roles are granted in the fan tenant").isEqualTo(FAN_TENANT);
            grants.computeIfAbsent(m.group(2), k -> new ArrayList<>()).add(m.group(3));
        }
        return grants;
    }

    private static List<String> parseDemoSeedArtistIds() throws IOException {
        Matcher m = SEED_ARTIST_ID.matcher(readSibling(FAN_DEMO_SEED, "the fan demo seed script"));
        List<String> ids = new ArrayList<>();
        while (m.find()) {
            ids.add(m.group(2));
        }
        return ids;
    }

    @Test
    @DisplayName("three artist credentials, all in the fan tenant, each with its own email")
    void credentialsCoverTheThreeDemoArtists() throws IOException {
        List<SeededCredential> rows = parseCredentials();

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(SeededCredential::tenantId).containsOnly(FAN_TENANT);
        // UNIQUE (tenant_id, email) since V0007 — and a shared email in one tenant would
        // also collide with the demo consumer credential V9001 already seeds there.
        assertThat(rows).extracting(SeededCredential::email).doesNotHaveDuplicates();
        // credentials.account_id carries a GLOBAL unique index (V0001).
        assertThat(rows).extracting(SeededCredential::accountId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("every seeded artist hash verifies against the demo password with the login path's hasher")
    void seededHashesVerify() throws IOException {
        List<SeededCredential> rows = parseCredentials();
        assertThat(rows).isNotEmpty();

        for (SeededCredential row : rows) {
            assertThat(hasher.verify(DEMO_PASSWORD, row.hash()))
                    .as("seeded hash for '%s' must verify against the demo password", row.email())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("the credential account_id is the account-service account id, email for email")
    void credentialsAndAccountsAgree() throws IOException {
        Map<String, String> credentialsByEmail = new LinkedHashMap<>();
        for (SeededCredential c : parseCredentials()) {
            credentialsByEmail.put(c.email(), c.accountId());
        }
        List<SeededAccount> accounts = parseAccounts();

        assertThat(accounts).hasSize(credentialsByEmail.size());
        for (SeededAccount a : accounts) {
            assertThat(a.tenantId()).isEqualTo(FAN_TENANT);
            assertThat(credentialsByEmail)
                    .as("account-service seeds an artist account for an email auth-service "
                            + "has no credential for — that account can never be logged into")
                    .containsKey(a.email());
            assertThat(a.accountId())
                    .as("account_id is the OIDC `sub`: a mismatch mints a token for an id "
                            + "no artists row points at, and the feed join silently empties")
                    .isEqualTo(credentialsByEmail.get(a.email()));
            // V9005's rule, restated here because it is easy to "simplify" away: the
            // identity is a separate registry row, never the account id reused.
            assertThat(a.identityId()).isNotEqualTo(a.accountId());
        }
    }

    @Test
    @DisplayName("every artist account is granted ARTIST *and* FAN — the stored set replaces the seed, it does not extend it")
    void everyArtistAccountHoldsBothRoles() throws IOException {
        List<String> accountIds = parseAccounts().stream().map(SeededAccount::accountId).toList();
        Map<String, List<String>> grants = parseGrants();

        assertThat(accountIds).isNotEmpty();
        assertThat(grants.keySet())
                .as("a seeded artist account with no role grant gets the RoleSeedPolicy default "
                        + "[FAN] and still cannot publish an ARTIST_POST")
                .containsExactlyInAnyOrderElementsOf(accountIds);

        for (String accountId : accountIds) {
            assertThat(grants.get(accountId))
                    .as("TenantClaimTokenCustomizer#populateRoles emits stored account_roles "
                            + "VERBATIM and falls to RoleSeedPolicy only when the stored set is "
                            + "EMPTY — so granting ARTIST alone does not add a role, it REPLACES "
                            + "the fan-platform FAN seed. Both must be stored explicitly.")
                    .containsExactlyInAnyOrder("FAN", "ARTIST");
        }
    }

    @Test
    @DisplayName("the artist account ids are the demo seed's artist entity ids")
    void accountIdsMatchTheFanDemoSeedArtistIds() throws IOException {
        List<String> accountIds = parseAccounts().stream().map(SeededAccount::accountId).toList();
        List<String> demoSeedIds = parseDemoSeedArtistIds();

        assertThat(demoSeedIds)
                .as("seed-fan.sh must still declare ARTIST_A/B/C as literal ids — if that "
                        + "changed, this guard is measuring nothing (0 parsed rows would "
                        + "otherwise pass a containsAll against an empty list)")
                .hasSize(3);
        assertThat(accountIds)
                .as("TASK-FAN-BE-045 backfilled artists.account_id := artists.id, and this "
                        + "ticket makes that value a REAL IAM subject by provisioning the "
                        + "account AT that id. Break the equality and the demo splits in two: "
                        + "the artist logs in as one id while follows/posts point at another.")
                .containsExactlyInAnyOrderElementsOf(demoSeedIds);
    }
}
