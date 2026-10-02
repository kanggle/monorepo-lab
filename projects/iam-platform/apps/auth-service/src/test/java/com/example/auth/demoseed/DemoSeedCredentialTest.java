package com.example.auth.demoseed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.auth.domain.credentials.PasswordPolicy;
import com.example.security.password.Argon2idPasswordHasher;
import com.example.security.password.PasswordHasher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TASK-BE-571 — guards the portfolio-demo single identity.
 *
 * <p><b>This test reads the seed files themselves.</b> It deliberately does not
 * restate the hash, the tenants or the account ids as its own constants: a test
 * that asserted against a copy would verify the copy, not the artifact the
 * database actually receives. Every value below is parsed out of the migration
 * SQL, so drift between the seed and this guard is not expressible.
 *
 * <p>TASK-MONO-744 (ADR-MONO-078 A) — the two consumer credentials ({@code ecommerce} ·
 * {@code fan-platform}) became ONE consumer-pool credential; the console ({@code iam})
 * row is unchanged (D1). What it pins:
 * <ol>
 *   <li>The demo password satisfies {@link PasswordPolicy} — so the credential is
 *       one a human could also set through the normal change-password path, and a
 *       future policy tightening turns this red instead of silently making the
 *       documented demo password unsettable.</li>
 *   <li>Every seeded hash verifies against the demo password with the SAME hasher
 *       the login path uses. A regenerated-but-unpasted hash is the failure this
 *       catches, and it is otherwise invisible until a live login fails.</li>
 *   <li>Exactly one pool row and one console row, and no consumer-SITE row: a site
 *       credential beside the pool one for the same email is the coexistence
 *       multi-tenancy.md § 소비자 계정 풀 § 2 forbids (the pool one wins the form, the
 *       site one becomes unreachable).</li>
 *   <li><b>The cross-database link keys.</b> {@code admin_operators.oidc_subject}
 *       must equal the {@code iam}-tenant credential's {@code account_id} (operator
 *       resolution is account_id-only since TASK-MONO-299); the pool credential's
 *       {@code account_id} must equal account-service R__05's pool account (that
 *       value is the {@code sub} on both consumer sites), which must be an ACTIVE
 *       member of both sites (else the first-visit consent screen, or no token);
 *       and seed-fan.sh's default demo {@code sub} must be that same id. They live
 *       in three trees and nothing else in the build compares them.</li>
 * </ol>
 */
@DisplayName("TASK-BE-571 / TASK-MONO-744 demo single identity seed")
class DemoSeedCredentialTest {

    private static final String DEMO_EMAIL = "demo@demo.com";
    private static final String DEMO_PASSWORD = "Demo1234!";
    private static final String POOL_TENANT = "consumer-pool";
    private static final String CONSOLE_TENANT = "iam";

    private static final String CREDENTIAL_SEED =
            "db/migration-dev/R__01_seed_demo_single_identity_credentials.sql";
    /** Sibling module — resolved from this module's directory (Gradle test workingDir). */
    private static final Path OPERATOR_SEED = Path.of("..", "admin-service", "src", "main",
            "resources", "db", "migration-dev", "R__seed_demo_operator.sql");
    /** Sibling module — the account side of the pool credential (a declared Gradle test input). */
    private static final Path ACCOUNT_SEED = Path.of("..", "account-service", "src", "main",
            "resources", "db", "migration-dev",
            "R__05_seed_demo_corp_tenant_and_consumer_accounts.sql");
    /** Repo root, four levels up — the fan demo seed (a declared Gradle test input). */
    private static final Path FAN_DEMO_SEED = Path.of("..", "..", "..", "..",
            "infra", "demo", "seed", "seed-fan.sh");

    /** Matches the seeded VALUES tuples: ('<tenant>', '<accountId>', '<email>', '<hash>', ... */
    private static final Pattern CREDENTIAL_ROW = Pattern.compile(
            "\\(\\s*'([a-z-]+)'\\s*,\\s*'([0-9a-f-]{36})'\\s*,\\s*'([^']+)'\\s*,\\s*'(\\$argon2id\\$[^']+)'",
            Pattern.MULTILINE);

    /** R__05 accounts tuples: ('<accountId>', '<identityId>', '<tenant>', '<email>', ... */
    private static final Pattern ACCOUNT_ROW = Pattern.compile(
            "\\(\\s*'([0-9a-f-]{36})'\\s*,\\s*'([0-9a-f-]{36})'\\s*,\\s*'([a-z-]+)'\\s*,\\s*'([^']+)'",
            Pattern.MULTILINE);

    /** seed-fan.sh: DEMO_SUB="${DEMO_FAN_SUB:-<uuid>}" */
    private static final Pattern FAN_DEMO_SUB = Pattern.compile(
            "^DEMO_SUB=\"\\$\\{DEMO_FAN_SUB:-([0-9a-f-]{36})}\"", Pattern.MULTILINE);

    private final PasswordHasher hasher = new Argon2idPasswordHasher();

    private record SeededCredential(String tenantId, String accountId, String email, String hash) {}

    private record SeededAccount(String accountId, String identityId, String tenantId, String email) {}

    private static String readClasspath(String resource) throws IOException {
        try (var in = DemoSeedCredentialTest.class.getClassLoader().getResourceAsStream(resource)) {
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

    private static SeededCredential poolCredential() throws IOException {
        return parseCredentials().stream()
                .filter(r -> POOL_TENANT.equals(r.tenantId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no consumer-pool credential in the seed"));
    }

    @Test
    @DisplayName("the demo password satisfies the production password policy")
    void demoPasswordSatisfiesPolicy() {
        assertThatCode(() -> PasswordPolicy.validate(DEMO_PASSWORD, DEMO_EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the seed carries one consumer-pool credential and one console credential — no consumer-site row")
    void seedIsOnePoolRowAndOneConsoleRow() throws IOException {
        List<SeededCredential> rows = parseCredentials();

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(SeededCredential::email).containsOnly(DEMO_EMAIL);
        assertThat(rows).extracting(SeededCredential::tenantId)
                .as("an `ecommerce` or `fan-platform` row beside the pool row is the § 2 coexistence: "
                        + "the consumer form picks the pool credential first and the site one is dead")
                .containsExactlyInAnyOrder(POOL_TENANT, CONSOLE_TENANT);
        // credentials.account_id carries a GLOBAL unique index (V0001).
        assertThat(rows).extracting(SeededCredential::accountId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("every seeded hash verifies against the demo password with the login path's hasher")
    void seededHashesVerify() throws IOException {
        List<SeededCredential> rows = parseCredentials();
        assertThat(rows).isNotEmpty();

        for (SeededCredential row : rows) {
            assertThat(hasher.verify(DEMO_PASSWORD, row.hash()))
                    .as("seeded hash for tenant '%s' must verify against the demo password",
                            row.tenantId())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("admin_operators.oidc_subject equals the iam-tenant credential's account_id")
    void operatorLinkKeyMatchesTheIamCredential() throws IOException {
        String iamAccountId = parseCredentials().stream()
                .filter(r -> CONSOLE_TENANT.equals(r.tenantId()))
                .map(SeededCredential::accountId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no iam-tenant credential in the seed"));

        String operatorSeed = readSibling(OPERATOR_SEED, "the admin-service operator seed");

        assertThat(operatorSeed)
                .as("operator resolution is account_id-only (TASK-MONO-299): a mismatch here "
                        + "fail-closes to 401 at the console, indistinguishable from a load timeout")
                .contains("'" + iamAccountId + "'");
    }

    @Test
    @DisplayName("the pool credential's account_id is account-service R__05's one pool account for the demo email")
    void poolCredentialMatchesThePoolAccount() throws IOException {
        SeededCredential pool = poolCredential();

        Matcher m = ACCOUNT_ROW.matcher(readSibling(ACCOUNT_SEED, "the account-service demo seed"));
        List<SeededAccount> accounts = new ArrayList<>();
        while (m.find()) {
            accounts.add(new SeededAccount(m.group(1), m.group(2), m.group(3), m.group(4)));
        }
        assertThat(accounts)
                .as("R__05 seeds exactly one account for the demo email — a second (site) account "
                        + "beside the pool one is the § 2 coexistence")
                .hasSize(1);
        SeededAccount account = accounts.get(0);
        assertThat(account.tenantId()).isEqualTo(POOL_TENANT);
        assertThat(account.email()).isEqualTo(DEMO_EMAIL);
        assertThat(account.accountId())
                .as("credentials.account_id is the OIDC `sub` on both consumer sites: a mismatch "
                        + "mints a token for an id account-service has no row for")
                .isEqualTo(pool.accountId());
        // R__05's rule: the identity is a separate registry row, never the account id reused.
        assertThat(account.identityId()).isNotEqualTo(account.accountId());
    }

    @Test
    @DisplayName("R__05 makes the pool account an ACTIVE member of BOTH consumer sites, guarded on consumer-pool")
    void poolAccountIsPreJoinedToBothSites() throws IOException {
        String poolAccountId = poolCredential().accountId();
        // Comment lines removed so the header prose (which names the table) is not a statement.
        String statements = readSibling(ACCOUNT_SEED, "the account-service demo seed")
                .replaceAll("(?m)^\\s*--.*$", "");

        Matcher stmt = Pattern.compile(
                "INSERT\\s+IGNORE\\s+INTO\\s+consumer_site_memberships\\b[^;]*;").matcher(statements);
        Set<String> activeSites = new LinkedHashSet<>();
        int seen = 0;
        while (stmt.find()) {
            seen++;
            String s = stmt.group();
            Matcher site = Pattern.compile(
                    "SELECT\\s+id\\s*,\\s*'([a-z-]+)'\\s*,\\s*'ACTIVE'").matcher(s);
            assertThat(site.find()).as("membership statement shape: %s", s).isTrue();
            assertThat(s)
                    .as("the membership must be written only onto a POOL account — on an existing "
                            + "volume …ec01 can still be an ecommerce site account, and a membership "
                            + "there makes TASK-BE-618's mover fail for it")
                    .containsPattern("tenant_id\\s*=\\s*'consumer-pool'");
            if (s.contains("'" + poolAccountId + "'")) {
                activeSites.add(site.group(1));
            }
        }
        assertThat(seen).as("R__05 must still contain membership statements to measure").isPositive();
        assertThat(activeSites)
                .as("AC-1 (R3): the demo account is PRE-JOINED — a missing site means the consent "
                        + "screen on that site instead of the one-login demo")
                .containsExactlyInAnyOrder("fan-platform", "ecommerce");
    }

    @Test
    @DisplayName("seed-fan.sh's default demo sub is the pool account id")
    void fanDemoSeedDefaultSubIsThePoolAccount() throws IOException {
        Matcher m = FAN_DEMO_SUB.matcher(readSibling(FAN_DEMO_SEED, "the fan demo seed script"));
        assertThat(m.find())
                .as("seed-fan.sh must still declare DEMO_SUB=\"${DEMO_FAN_SUB:-<uuid>}\" — otherwise "
                        + "this cell measures nothing")
                .isTrue();
        assertThat(m.group(1))
                .as("the fan and store data of the demo account must hang off the SAME sub "
                        + "(TASK-MONO-744 AC-2). seed-fan.sh does fall back to the token's sub, but "
                        + "a stale default is a warning on every run and a trap for the next reader")
                .isEqualTo(poolCredential().accountId());
    }
}
