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
import java.util.List;
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
 * <p>What it pins:
 * <ol>
 *   <li>The demo password satisfies {@link PasswordPolicy} — so the credential is
 *       one a human could also set through the normal change-password path, and a
 *       future policy tightening turns this red instead of silently making the
 *       documented demo password unsettable.</li>
 *   <li>Every seeded hash verifies against the demo password with the SAME hasher
 *       the login path uses. A regenerated-but-unpasted hash is the failure this
 *       catches, and it is otherwise invisible until a live login fails.</li>
 *   <li>The three credentials sit in exactly the three tenants the three OIDC
 *       clients resolve to. The roles-claim seed only fires when the principal's
 *       tenant equals the client's platform, so a wrong tenant here silently
 *       costs the CUSTOMER / FAN claim rather than failing loudly.</li>
 *   <li><b>The cross-database link key.</b> {@code admin_operators.oidc_subject}
 *       must equal the {@code iam}-tenant credential's {@code account_id},
 *       because operator resolution is account_id-only since TASK-MONO-299. This
 *       is the single most likely way this seed breaks, it spans two services'
 *       migrations, and nothing else in the build compares them.</li>
 * </ol>
 */
@DisplayName("TASK-BE-571 demo single identity seed")
class DemoSeedCredentialTest {

    private static final String DEMO_EMAIL = "demo@demo.com";
    private static final String DEMO_PASSWORD = "Demo1234!";

    private static final String CREDENTIAL_SEED =
            "db/migration-dev/R__01_seed_demo_single_identity_credentials.sql";
    /** Sibling module — resolved from this module's directory (Gradle test workingDir). */
    private static final Path OPERATOR_SEED = Path.of("..", "admin-service", "src", "main",
            "resources", "db", "migration-dev", "R__seed_demo_operator.sql");

    /** Matches the seeded VALUES tuples: ('<tenant>', '<accountId>', '<email>', '<hash>', ... */
    private static final Pattern CREDENTIAL_ROW = Pattern.compile(
            "\\(\\s*'([a-z-]+)'\\s*,\\s*'([0-9a-f-]{36})'\\s*,\\s*'([^']+)'\\s*,\\s*'(\\$argon2id\\$[^']+)'",
            Pattern.MULTILINE);

    private final PasswordHasher hasher = new Argon2idPasswordHasher();

    private record SeededCredential(String tenantId, String accountId, String email, String hash) {}

    private static String readClasspath(String resource) throws IOException {
        try (var in = DemoSeedCredentialTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("seed migration %s must be on the classpath", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<SeededCredential> parseCredentials() throws IOException {
        Matcher m = CREDENTIAL_ROW.matcher(readClasspath(CREDENTIAL_SEED));
        List<SeededCredential> rows = new ArrayList<>();
        while (m.find()) {
            rows.add(new SeededCredential(m.group(1), m.group(2), m.group(3), m.group(4)));
        }
        return rows;
    }

    @Test
    @DisplayName("the demo password satisfies the production password policy")
    void demoPasswordSatisfiesPolicy() {
        assertThatCode(() -> PasswordPolicy.validate(DEMO_PASSWORD, DEMO_EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the seed carries one credential per surface, in the tenant that surface's client resolves to")
    void seedCoversTheThreeSurfaceTenants() throws IOException {
        List<SeededCredential> rows = parseCredentials();

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(SeededCredential::email).containsOnly(DEMO_EMAIL);
        assertThat(rows).extracting(SeededCredential::tenantId)
                .containsExactlyInAnyOrder("ecommerce", "fan-platform", "iam");
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
    @DisplayName("the demo credential seed is unreachable outside the e2e profile")
    void devSeedLocationIsProfileScoped() throws IOException {
        // The security invariant: db/migration-dev holds a known password for a known
        // email. It must be loadable ONLY under the demo/e2e profile. auth-service has
        // no application-prod.yml, so the DEFAULT profile IS what production runs —
        // pinning it to db/migration alone is what keeps the demo credentials out.
        assertThat(readClasspath("application.yml"))
                .as("the default (production) profile must NOT load db/migration-dev")
                .doesNotContain("migration-dev");
        assertThat(readClasspath("application-e2e.yml"))
                .as("the e2e/demo profile is the one place the seed is loaded")
                .contains("classpath:db/migration,classpath:db/migration-dev");
    }

    @Test
    @DisplayName("admin_operators.oidc_subject equals the iam-tenant credential's account_id")
    void operatorLinkKeyMatchesTheIamCredential() throws IOException {
        String iamAccountId = parseCredentials().stream()
                .filter(r -> "iam".equals(r.tenantId()))
                .map(SeededCredential::accountId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no iam-tenant credential in the seed"));

        assertThat(OPERATOR_SEED)
                .as("the admin-service operator seed must be resolvable from this module")
                .exists();
        String operatorSeed = Files.readString(OPERATOR_SEED, StandardCharsets.UTF_8);

        assertThat(operatorSeed)
                .as("operator resolution is account_id-only (TASK-MONO-299): a mismatch here "
                        + "fail-closes to 401 at the console, indistinguishable from a load timeout")
                .contains("'" + iamAccountId + "'");
    }
}
