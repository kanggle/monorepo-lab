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
 * TASK-BE-597 — guards the RESTRICTED demo operator ({@code viewer@demo.com}).
 *
 * <p>A separate class for the same reason {@link DemoSecondOperatorSeedTest} is one: it
 * pins a different pair of artifacts (this module's
 * {@code R__seed_demo_viewer_operator_credential.sql} and admin-service's
 * {@code R__seed_demo_viewer_operator.sql}) and a different invariant. Folding the viewer
 * into the existing operator seed would have broken that test's "exactly two demo-corp
 * operators" statement, which is still true about the ERP Separation-of-Duties pair.
 *
 * <p>What is pinned, and why each one fails silently otherwise:
 * <ul>
 *   <li>the credential lives in tenant {@code iam} with its own email and account id —
 *       elsewhere the console's scoped lookup misses; a shared email is swallowed by
 *       {@code INSERT IGNORE};</li>
 *   <li>the hash verifies against the published demo password with the login path's
 *       own hasher — a stale hash only shows up as a failed live login;</li>
 *   <li>the operator's {@code oidc_subject} equals that credential's account id —
 *       a mismatch fail-closes to a console 401 that reads like a load timeout;</li>
 *   <li>the operator seed grants NO role, its ONE tenant assignment is its own unregistered
 *       home slug {@code demo-viewer}, and that home is not {@code demo-corp} — each of those
 *       would quietly turn the account that exists to show «권한 부족» into one that does not.
 *       (TASK-MONO-784 S2: the home alone is no longer a membership, so the row is what keeps
 *       the login — exchange = ACTIVE ∧ membership ≥ 1.)</li>
 * </ul>
 *
 * <p>Every value is parsed out of the SQL; the admin-service file is a Gradle
 * {@code test} input (this module's build.gradle) so editing only that file re-runs this.
 */
@DisplayName("TASK-BE-597 restricted demo operator seed")
class DemoViewerOperatorSeedTest {

    private static final String DEMO_PASSWORD = "Demo1234!";

    private static final String VIEWER_CREDENTIAL_SEED =
            "db/migration-dev/R__seed_demo_viewer_operator_credential.sql";
    private static final List<String> OTHER_CREDENTIAL_SEEDS = List.of(
            "db/migration-dev/R__01_seed_demo_single_identity_credentials.sql",
            "db/migration-dev/R__seed_demo_second_operator_credential.sql");
    /** Sibling module — resolved from this module's directory (Gradle test workingDir). */
    private static final Path VIEWER_OPERATOR_SEED = Path.of("..", "admin-service", "src", "main",
            "resources", "db", "migration-dev", "R__seed_demo_viewer_operator.sql");

    /** ('<tenant>', '<accountId>', '<email>', '<hash>', ... — same shape as the siblings. */
    private static final Pattern CREDENTIAL_ROW = Pattern.compile(
            "\\(\\s*'([a-z-]+)'\\s*,\\s*'([0-9a-f-]{36})'\\s*,\\s*'([^']+)'\\s*,\\s*'(\\$argon2id\\$[^']+)'",
            Pattern.MULTILINE);

    /**
     * ('<operatorId>', '<tenantId>', '<email>', '<hash>', '<displayName>', 'ACTIVE',
     * [-- comment lines] '<oidcSubject>' — copied from {@link DemoSecondOperatorSeedTest}.
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
        try (var in = DemoViewerOperatorSeedTest.class.getClassLoader()
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

    private static String operatorSeed() throws IOException {
        assertThat(VIEWER_OPERATOR_SEED)
                .as("the admin-service viewer seed must be resolvable from this module")
                .exists();
        return Files.readString(VIEWER_OPERATOR_SEED, StandardCharsets.UTF_8);
    }

    private static List<SeededOperator> parseOperators(String seed) {
        Matcher m = OPERATOR_ROW.matcher(seed);
        List<SeededOperator> rows = new ArrayList<>();
        while (m.find()) {
            rows.add(new SeededOperator(m.group(1), m.group(2), m.group(3), m.group(4)));
        }
        return rows;
    }

    /** SQL with `--` comment lines removed, so prose mentioning a table is not a statement. */
    private static String statementsOnly(String sql) {
        return sql.replaceAll("(?m)^\\s*--.*$", "");
    }

    @Test
    @DisplayName("the viewer credential is one iam-tenant row with its own email and account id")
    void viewerCredentialIsScopedToIamAndDistinct() throws IOException {
        List<SeededCredential> viewer = parseCredentials(VIEWER_CREDENTIAL_SEED);
        assertThat(viewer).hasSize(1);
        SeededCredential row = viewer.get(0);
        assertThat(row.tenantId()).isEqualTo("iam");

        List<SeededCredential> others = new ArrayList<>();
        for (String seed : OTHER_CREDENTIAL_SEEDS) {
            others.addAll(parseCredentials(seed));
        }
        // Control: the predicate reads the siblings (R__01 = 2 rows since TASK-MONO-744 —
        // consumer-pool + iam — second = 1). Without it, a regex that matched nothing would
        // make both checks below pass vacuously.
        assertThat(others).hasSize(3);
        assertThat(others).extracting(SeededCredential::email).doesNotContain(row.email());
        assertThat(others).extracting(SeededCredential::accountId).doesNotContain(row.accountId());
    }

    @Test
    @DisplayName("the viewer credential's hash verifies with the login path's hasher")
    void viewerCredentialHashVerifies() throws IOException {
        List<SeededCredential> viewer = parseCredentials(VIEWER_CREDENTIAL_SEED);
        assertThat(viewer).isNotEmpty();
        assertThat(hasher.verify(DEMO_PASSWORD, viewer.get(0).hash())).isTrue();
    }

    @Test
    @DisplayName("the viewer operator's oidc_subject equals its iam credential's account_id")
    void viewerLinkKeyMatches() throws IOException {
        List<SeededOperator> operators = parseOperators(operatorSeed());
        assertThat(operators).hasSize(1);
        SeededOperator op = operators.get(0);

        SeededCredential credential = parseCredentials(VIEWER_CREDENTIAL_SEED).get(0);
        assertThat(op.email()).isEqualTo(credential.email());
        assertThat(op.oidcSubject())
                .as("operator resolution is account_id-only (TASK-MONO-299): a mismatch "
                        + "fail-closes to a console 401 whose text is identical to a timeout's")
                .isEqualTo(credential.accountId());
    }

    @Test
    @DisplayName("the viewer operator is minimal: no role, membership exactly its unregistered home, home not demo-corp")
    void viewerIsMinimal() throws IOException {
        String seed = operatorSeed();
        String statements = statementsOnly(seed);

        // A role or a membership in a real tenant is the only way this seed could widen the account.
        assertThat(statements).doesNotContainIgnoringCase("admin_operator_roles");
        // Control for statementsOnly(): the raw file DOES mention the table (in the
        // comment that explains its absence). If this fails, the comment stripping is
        // no longer what makes the check above meaningful.
        assertThat(seed).contains("admin_operator_roles");

        // TASK-MONO-784 S2 (ADR-MONO-082 b · R1 · D-2 · D-10): the assumable tenant is the membership
        // (assignment rows only — the home is no longer added). Exactly one, and it is the operator's
        // own unregistered home slug: the login survives (exchange needs ≥ 1) and nothing real opens.
        List<SeededOperator> operators = parseOperators(seed);
        assertThat(operators).hasSize(1);
        Matcher membership = DemoCsOperatorSeedTest.MEMBERSHIP.matcher(statements);
        List<String> memberships = new ArrayList<>();
        while (membership.find()) {
            memberships.add(membership.group(1) + " → " + membership.group(2));
        }
        assertThat(memberships).containsExactly(operators.get(0).tenantId() + " → demo-viewer");
        // demo-corp would derive all five domain OPERATOR roles at assume time.
        assertThat(operators.get(0).tenantId()).isEqualTo("demo-viewer").isNotEqualTo("demo-corp");
    }
}
