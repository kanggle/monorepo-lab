package com.example.auth.demoseed;

import static org.assertj.core.api.Assertions.assertThat;

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
 * TASK-MONO-519 — guards the SECOND demo operator identity.
 *
 * <p><b>Why a separate class rather than more cases in {@link DemoSeedCredentialTest}.</b>
 * That test pins the V9001 file: it asserts exactly three rows in exactly three
 * tenants, which is a correct statement about the single-identity seed and must
 * stay that way. This one pins a different artifact (V9003) and a different
 * invariant (two operators, two link keys, no collision between them).
 *
 * <p><b>Why it exists at all.</b> {@code DemoSeedCredentialTest} already compares
 * one {@code oidc_subject} against one {@code account_id}, and a pin with no sibling
 * silently stops covering the moment a second instance of the same relationship
 * appears. A typo in the second operator's {@code oidc_subject} does not degrade —
 * it fail-closes to a console 401 that the UI renders as
 * {@code operator_exchange_unavailable}, character-for-character what a 5s load
 * timeout renders, so the live symptom points at the wrong layer.
 *
 * <p>Like its sibling it restates no value of its own: every id, tenant and hash
 * below is parsed out of the migration SQL, so drift between guard and seed is not
 * expressible. The admin-service seed is declared as a Gradle {@code test} input
 * (see this module's build.gradle), without which editing only the sibling file
 * leaves this task UP-TO-DATE and green over exactly the drift it detects.
 */
@DisplayName("TASK-MONO-519 second demo operator seed")
class DemoSecondOperatorSeedTest {

    private static final String DEMO_PASSWORD = "Demo1234!";

    private static final String FIRST_CREDENTIAL_SEED =
            "db/migration-dev/V9001__seed_demo_single_identity_credentials.sql";
    private static final String SECOND_CREDENTIAL_SEED =
            "db/migration-dev/R__seed_demo_second_operator_credential.sql";
    /** Sibling module — resolved from this module's directory (Gradle test workingDir). */
    private static final Path OPERATOR_SEED = Path.of("..", "admin-service", "src", "main",
            "resources", "db", "migration-dev", "R__seed_demo_operator.sql");

    /** Matches the seeded VALUES tuples: ('<tenant>', '<accountId>', '<email>', '<hash>', ... */
    private static final Pattern CREDENTIAL_ROW = Pattern.compile(
            "\\(\\s*'([a-z-]+)'\\s*,\\s*'([0-9a-f-]{36})'\\s*,\\s*'([^']+)'\\s*,\\s*'(\\$argon2id\\$[^']+)'",
            Pattern.MULTILINE);

    /**
     * Matches an {@code admin_operators} VALUES tuple far enough to reach the
     * {@code oidc_subject} column: ('<operatorId>', '<tenantId>', '<email>', '<hash>',
     * '<displayName>', '<status>', ... '<oidcSubject>'. The hash literal contains
     * {@code $} and commas, so the columns are taken one quoted literal at a time
     * rather than by a single greedy span.
     */
    private static final Pattern OPERATOR_ROW = Pattern.compile(
            "'([a-z-]+)'\\s*,\\s*'([a-z-]+)'\\s*,\\s*'([^']+)'\\s*,\\s*'\\$argon2id\\$[^']+'\\s*,"
                    + "\\s*'[^']*'\\s*,\\s*'ACTIVE'\\s*,\\s*(?:--[^\\n]*\\n\\s*)*'([0-9a-f-]{36})'",
            Pattern.MULTILINE);

    private final PasswordHasher hasher = new Argon2idPasswordHasher();

    private record SeededCredential(String tenantId, String accountId, String email, String hash) {}

    private record SeededOperator(String operatorId, String tenantId, String email,
                                  String oidcSubject) {}

    private static String readClasspath(String resource) throws IOException {
        try (var in = DemoSecondOperatorSeedTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(in).as("seed migration %s must be on the classpath", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<SeededCredential> parseCredentials(String resource) throws IOException {
        Matcher m = CREDENTIAL_ROW.matcher(readClasspath(resource));
        List<SeededCredential> rows = new ArrayList<>();
        while (m.find()) {
            rows.add(new SeededCredential(m.group(1), m.group(2), m.group(3), m.group(4)));
        }
        return rows;
    }

    /**
     * Tenants assigned to {@code operatorId} by the {@code operator_tenant_assignment}
     * statements — one entry per statement, in file order.
     */
    private static List<String> assignedTenants(String seed, String operatorId) {
        // 🔴 `[^;]*?` and not `.*?`: the gap must not be allowed to cross a statement
        // terminator. With a dot the leftmost match would start at the FIRST operator's
        // SELECT and run all the way to the SECOND operator's WHERE, so asking for
        // `demo-requester` would return the other operator's tenants — and the
        // ecommerce assertion below would fail for a reason that has nothing to do
        // with the seed. A character class matches newlines without DOTALL.
        Matcher m = Pattern.compile(
                "SELECT\\s+o\\.id\\s*,\\s*'([a-z-]+)'[^;]*?WHERE\\s+o\\.operator_id\\s*=\\s*'"
                        + Pattern.quote(operatorId) + "'").matcher(seed);
        List<String> tenants = new ArrayList<>();
        while (m.find()) {
            tenants.add(m.group(1));
        }
        return tenants;
    }

    private static List<SeededOperator> parseOperators() throws IOException {
        assertThat(OPERATOR_SEED)
                .as("the admin-service operator seed must be resolvable from this module")
                .exists();
        Matcher m = OPERATOR_ROW.matcher(Files.readString(OPERATOR_SEED, StandardCharsets.UTF_8));
        List<SeededOperator> rows = new ArrayList<>();
        while (m.find()) {
            rows.add(new SeededOperator(m.group(1), m.group(2), m.group(3), m.group(4)));
        }
        return rows;
    }

    @Test
    @DisplayName("the second credential is one iam-tenant row with its own email and account id")
    void secondCredentialIsScopedToIam() throws IOException {
        List<SeededCredential> second = parseCredentials(SECOND_CREDENTIAL_SEED);

        assertThat(second).hasSize(1);
        SeededCredential row = second.get(0);
        // The console client `platform-console-web` resolves to tenant `iam`; a row
        // seeded anywhere else would miss the scoped lookup, reach the cross-tenant
        // fallback, and fail closed rather than log in.
        assertThat(row.tenantId()).isEqualTo("iam");

        List<SeededCredential> first = parseCredentials(FIRST_CREDENTIAL_SEED);
        // UNIQUE (tenant_id, email) since V0007 — a shared email in `iam` would make
        // the migration itself a no-op under INSERT IGNORE, i.e. a silently absent
        // second identity rather than a failure.
        assertThat(first).extracting(SeededCredential::email).doesNotContain(row.email());
        // credentials.account_id carries a GLOBAL unique index (V0001).
        assertThat(first).extracting(SeededCredential::accountId).doesNotContain(row.accountId());
    }

    @Test
    @DisplayName("the second credential's hash verifies with the login path's hasher")
    void secondCredentialHashVerifies() throws IOException {
        List<SeededCredential> second = parseCredentials(SECOND_CREDENTIAL_SEED);
        assertThat(second).isNotEmpty();

        assertThat(hasher.verify(DEMO_PASSWORD, second.get(0).hash()))
                .as("a regenerated-but-unpasted hash is invisible until a live login fails")
                .isTrue();
    }

    @Test
    @DisplayName("both operators' oidc_subject equal their own iam credential's account_id")
    void everyOperatorLinkKeyMatchesAnIamCredential() throws IOException {
        List<SeededOperator> operators = parseOperators();

        // Two, not "at least one": the approval loop's Separation-of-Duties gate needs
        // two distinct actors in demo-corp, and dropping back to one is the exact
        // regression TASK-MONO-519 closed.
        assertThat(operators).hasSize(2);
        assertThat(operators).extracting(SeededOperator::tenantId).containsOnly("demo-corp");
        assertThat(operators).extracting(SeededOperator::oidcSubject).doesNotHaveDuplicates();

        List<SeededCredential> iamCredentials = new ArrayList<>();
        iamCredentials.addAll(parseCredentials(FIRST_CREDENTIAL_SEED));
        iamCredentials.addAll(parseCredentials(SECOND_CREDENTIAL_SEED));
        iamCredentials.removeIf(c -> !"iam".equals(c.tenantId()));

        for (SeededOperator operator : operators) {
            SeededCredential match = iamCredentials.stream()
                    .filter(c -> c.email().equals(operator.email()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "operator '" + operator.operatorId() + "' has email '"
                                    + operator.email() + "', which no iam-tenant credential "
                                    + "carries — that operator can never log in"));
            assertThat(operator.oidcSubject())
                    .as("operator resolution is account_id-only (TASK-MONO-299): a mismatch "
                            + "for '%s' fail-closes to a console 401 whose text is identical "
                            + "to a load timeout's", operator.operatorId())
                    .isEqualTo(match.accountId());
        }
    }

    @Test
    @DisplayName("the second operator is assigned demo-corp and NOT ecommerce")
    void secondOperatorAssignmentsAreTheDocumentedOnes() throws IOException {
        String seed = Files.readString(OPERATOR_SEED, StandardCharsets.UTF_8);

        // Matched by regex, not by a literal substring: the assignment statement spans
        // two lines, so a literal would also be asserting this repository's line
        // endings and would go red on a CRLF checkout for a reason unrelated to seeds.
        assertThat(assignedTenants(seed, "demo-requester"))
                // Without the demo-corp assignment the token exchange succeeds and the
                // assume fails with invalid_grant "operator is not assigned to the
                // selected tenant" — a different layer from where the symptom points.
                .contains("demo-corp")
                // The asymmetry is deliberate and documented in §5; pinned here so that
                // "adding ecommerce for symmetry" argues with a red test, not a comment.
                .doesNotContain("ecommerce");
        // Control: the same predicate finds BOTH of the first operator's assignments.
        // Without this, a broken regex would return an empty list and the assertion
        // above would pass by finding nothing — the control is the predicate.
        assertThat(assignedTenants(seed, "demo-operator"))
                .containsExactlyInAnyOrder("demo-corp", "ecommerce");
    }
}
