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
 * TASK-MONO-751 — guards the PLATFORM demo operator ({@code platform@demo.com}), seeded by
 * owner decision 2026-10-03 so the console's fan-directory screens (ADR-MONO-079 D4-A,
 * platform operators only — rider R3) can be shown on the demo.
 *
 * <p>Same shape as {@link DemoViewerOperatorSeedTest} (a different pair of artifacts: this
 * module's {@code R__seed_demo_platform_operator_credential.sql} and admin-service's
 * {@code R__seed_demo_platform_operator.sql}). What is pinned, and why each one fails
 * silently otherwise:
 * <ul>
 *   <li>the credential lives in tenant {@code iam} with its own email and account id —
 *       elsewhere the console's scoped lookup misses; a shared email is swallowed by
 *       {@code INSERT IGNORE};</li>
 *   <li>the hash verifies against the published demo password with the login path's own
 *       hasher — a stale hash only shows up as a failed live login;</li>
 *   <li>the operator's {@code oidc_subject} equals that credential's account id — a
 *       mismatch fail-closes to a console 401 that reads like a load timeout;</li>
 *   <li>the operator's home tenant is the platform sentinel {@code '*'} — anything else is
 *       a customer operator, which R3 refuses at the {@code fan-platform} assume gate, so the
 *       identity would log in and then never see the screens it exists for;</li>
 *   <li>the seed grants NO role and NO tenant assignment — a role row would turn a
 *       fan-directory demo login into a second platform administrator.</li>
 * </ul>
 */
@DisplayName("TASK-MONO-751 platform demo operator seed")
class DemoPlatformOperatorSeedTest {

    private static final String DEMO_PASSWORD = "Demo1234!";

    private static final String PLATFORM_CREDENTIAL_SEED =
            "db/migration-dev/R__seed_demo_platform_operator_credential.sql";
    private static final List<String> OTHER_CREDENTIAL_SEEDS = List.of(
            "db/migration-dev/R__01_seed_demo_single_identity_credentials.sql",
            "db/migration-dev/R__seed_demo_second_operator_credential.sql",
            "db/migration-dev/R__seed_demo_viewer_operator_credential.sql");
    /** Sibling module — resolved from this module's directory (Gradle test workingDir). */
    private static final Path PLATFORM_OPERATOR_SEED = Path.of("..", "admin-service", "src", "main",
            "resources", "db", "migration-dev", "R__seed_demo_platform_operator.sql");

    /** ('<tenant>', '<accountId>', '<email>', '<hash>', ... — same shape as the siblings. */
    private static final Pattern CREDENTIAL_ROW = Pattern.compile(
            "\\(\\s*'([a-z-]+)'\\s*,\\s*'([0-9a-f-]{36})'\\s*,\\s*'([^']+)'\\s*,\\s*'(\\$argon2id\\$[^']+)'",
            Pattern.MULTILINE);

    /**
     * ('<operatorId>', '<tenantId>', '<email>', '<hash>', '<displayName>', 'ACTIVE',
     * [-- comment lines] '<oidcSubject>' — the viewer pattern with {@code *} admitted in the
     * tenant position (the platform sentinel this seed exists to carry).
     */
    private static final Pattern OPERATOR_ROW = Pattern.compile(
            "'([a-z-]+)'\\s*,\\s*'([a-z*-]+)'\\s*,\\s*'([^']+)'\\s*,\\s*'\\$argon2id\\$[^']+'\\s*,"
                    + "\\s*'[^']*'\\s*,\\s*'ACTIVE'\\s*,\\s*(?:--[^\\n]*\\n\\s*)*'([0-9a-f-]{36})'",
            Pattern.MULTILINE);

    private final PasswordHasher hasher = new Argon2idPasswordHasher();

    private record SeededCredential(String tenantId, String accountId, String email, String hash) {}

    private record SeededOperator(String operatorId, String tenantId, String email,
                                  String oidcSubject) {}

    private static String readClasspath(String resource) throws IOException {
        try (var in = DemoPlatformOperatorSeedTest.class.getClassLoader()
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
        assertThat(PLATFORM_OPERATOR_SEED)
                .as("the admin-service platform operator seed must be resolvable from this module")
                .exists();
        return Files.readString(PLATFORM_OPERATOR_SEED, StandardCharsets.UTF_8);
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
    @DisplayName("the platform credential is one iam-tenant row with its own email and account id")
    void platformCredentialIsScopedToIamAndDistinct() throws IOException {
        List<SeededCredential> platform = parseCredentials(PLATFORM_CREDENTIAL_SEED);
        assertThat(platform).hasSize(1);
        SeededCredential row = platform.get(0);
        assertThat(row.tenantId()).isEqualTo("iam");

        List<SeededCredential> others = new ArrayList<>();
        for (String seed : OTHER_CREDENTIAL_SEEDS) {
            others.addAll(parseCredentials(seed));
        }
        // Control: the predicate reads the siblings (R__01 = 2 rows, second = 1, viewer = 1).
        // Without it, a regex that matched nothing would make both checks below pass vacuously.
        assertThat(others).hasSize(4);
        assertThat(others).extracting(SeededCredential::email).doesNotContain(row.email());
        assertThat(others).extracting(SeededCredential::accountId).doesNotContain(row.accountId());
    }

    @Test
    @DisplayName("the platform credential's hash verifies with the login path's hasher")
    void platformCredentialHashVerifies() throws IOException {
        List<SeededCredential> platform = parseCredentials(PLATFORM_CREDENTIAL_SEED);
        assertThat(platform).isNotEmpty();
        assertThat(hasher.verify(DEMO_PASSWORD, platform.get(0).hash())).isTrue();
    }

    @Test
    @DisplayName("the platform operator's oidc_subject equals its iam credential's account_id")
    void platformLinkKeyMatches() throws IOException {
        List<SeededOperator> operators = parseOperators(operatorSeed());
        assertThat(operators).hasSize(1);
        SeededOperator op = operators.get(0);

        SeededCredential credential = parseCredentials(PLATFORM_CREDENTIAL_SEED).get(0);
        assertThat(op.email()).isEqualTo(credential.email());
        assertThat(op.oidcSubject())
                .as("operator resolution is account_id-only (TASK-MONO-299): a mismatch "
                        + "fail-closes to a console 401 whose text is identical to a timeout's")
                .isEqualTo(credential.accountId());
    }

    @Test
    @DisplayName("the platform operator is platform-scope ('*') with no role and no assignment")
    void platformOperatorIsPlatformScopeAndRoleless() throws IOException {
        String seed = operatorSeed();
        String statements = statementsOnly(seed);

        List<SeededOperator> operators = parseOperators(seed);
        assertThat(operators).hasSize(1);
        assertThat(operators.get(0).tenantId())
                .as("R3: only a '*' operator may assume fan-platform — any other home tenant is a "
                        + "customer operator and never sees the fan screens")
                .isEqualTo("*");

        assertThat(statements).doesNotContainIgnoringCase("admin_operator_roles");
        assertThat(statements).doesNotContainIgnoringCase("operator_tenant_assignment");
        // Control for statementsOnly(): the raw file DOES mention both tables (in the comment
        // that explains their absence).
        assertThat(seed).contains("admin_operator_roles").contains("operator_tenant_assignment");
    }
}
