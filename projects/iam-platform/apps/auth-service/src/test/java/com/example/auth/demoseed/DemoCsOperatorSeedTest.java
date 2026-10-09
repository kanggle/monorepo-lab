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
 * TASK-MONO-781 — guards the CS 2nd-line demo operator ({@code cs@demo.com}), seeded by
 * owner decision 2026-10-09 UTC so the console's email-search-only «계정 운영» mode
 * (platform-console TASK-PC-FE-326 — SUPPORT_LOCK, no {@code account.read}) can be shown.
 *
 * <p>Same shape as {@link DemoViewerOperatorSeedTest} / {@link DemoPlatformOperatorSeedTest}
 * (a different pair of artifacts: this module's {@code R__seed_demo_cs_operator_credential.sql}
 * and admin-service's {@code R__seed_demo_cs_operator.sql}). What is pinned, and why each one
 * fails silently otherwise:
 * <ul>
 *   <li>the credential lives in tenant {@code iam} with its own email and account id —
 *       elsewhere the console's scoped lookup misses; a shared email is swallowed by
 *       {@code INSERT IGNORE};</li>
 *   <li>the hash verifies against the published demo password with the login path's own
 *       hasher — a stale hash only shows up as a failed live login;</li>
 *   <li>the operator's {@code oidc_subject} equals that credential's account id — a mismatch
 *       fail-closes to a console 401 that reads like a load timeout;</li>
 *   <li>the operator's home tenant AND its SUPPORT_LOCK grant are the SITE tenant
 *       {@code ecommerce} — a {@code '*'} grant would make every lock a whole-account lock
 *       across sites (TASK-BE-621), and any other home tenant cannot find the demo consumer
 *       (it is a member of {@code ecommerce} and {@code fan-platform} only);</li>
 *   <li>the operator is confined to {@code ecommerce} and holds no assignment row.</li>
 * </ul>
 * The disclosed domain-role trade-off of that home tenant is pinned separately, where the
 * package-private derivation table is reachable:
 * {@code com.example.auth.infrastructure.oauth2.DemoCsOperatorDerivedRolesTest}.
 */
@DisplayName("TASK-MONO-781 CS 2nd-line demo operator seed")
class DemoCsOperatorSeedTest {

    private static final String DEMO_PASSWORD = "Demo1234!";

    private static final String CS_CREDENTIAL_SEED =
            "db/migration-dev/R__seed_demo_cs_operator_credential.sql";
    private static final List<String> OTHER_CREDENTIAL_SEEDS = List.of(
            "db/migration-dev/R__01_seed_demo_single_identity_credentials.sql",
            "db/migration-dev/R__03_seed_demo_corp_new_hire_credential.sql",
            "db/migration-dev/R__seed_demo_second_operator_credential.sql",
            "db/migration-dev/R__seed_demo_viewer_operator_credential.sql",
            "db/migration-dev/R__seed_demo_platform_operator_credential.sql");
    /** Sibling module — resolved from this module's directory (Gradle test workingDir). */
    static final Path CS_OPERATOR_SEED = Path.of("..", "admin-service", "src", "main",
            "resources", "db", "migration-dev", "R__seed_demo_cs_operator.sql");

    /** ('<tenant>', '<accountId>', '<email>', '<hash>', ... — same shape as the siblings. */
    private static final Pattern CREDENTIAL_ROW = Pattern.compile(
            "\\(\\s*'([a-z-]+)'\\s*,\\s*'([0-9a-f-]{36})'\\s*,\\s*'([^']+)'\\s*,\\s*'(\\$argon2id\\$[^']+)'",
            Pattern.MULTILINE);

    /**
     * ('<operatorId>', '<tenantId>', '<email>', '<hash>', '<displayName>', 'ACTIVE',
     * [-- comment lines] '<oidcSubject>' — copied from {@link DemoViewerOperatorSeedTest}.
     */
    private static final Pattern OPERATOR_ROW = Pattern.compile(
            "'([a-z-]+)'\\s*,\\s*'([a-z*-]+)'\\s*,\\s*'([^']+)'\\s*,\\s*'\\$argon2id\\$[^']+'\\s*,"
                    + "\\s*'[^']*'\\s*,\\s*'ACTIVE'\\s*,\\s*(?:--[^\\n]*\\n\\s*)*'([0-9a-f-]{36})'",
            Pattern.MULTILINE);

    /** The role grant: {@code SELECT o.id, r.id, '<tenant>', ... r.name = '<role>' ... '<operatorId>'}. */
    private static final Pattern ROLE_GRANT = Pattern.compile(
            "INSERT\\s+IGNORE\\s+INTO\\s+admin_operator_roles[^;]*?SELECT\\s+o\\.id\\s*,\\s*r\\.id\\s*,\\s*"
                    + "'([a-z*-]+)'[^;]*?r\\.name\\s*=\\s*'([A-Z_]+)'[^;]*?o\\.operator_id\\s*=\\s*'([a-z-]+)'\\s*;",
            Pattern.CASE_INSENSITIVE);

    private final PasswordHasher hasher = new Argon2idPasswordHasher();

    private record SeededCredential(String tenantId, String accountId, String email, String hash) {}

    private record SeededOperator(String operatorId, String tenantId, String email,
                                  String oidcSubject) {}

    private static String readClasspath(String resource) throws IOException {
        try (var in = DemoCsOperatorSeedTest.class.getClassLoader()
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
        assertThat(CS_OPERATOR_SEED)
                .as("the admin-service CS operator seed must be resolvable from this module")
                .exists();
        return Files.readString(CS_OPERATOR_SEED, StandardCharsets.UTF_8);
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
    @DisplayName("the CS credential is one iam-tenant row with its own email and account id")
    void csCredentialIsScopedToIamAndDistinct() throws IOException {
        List<SeededCredential> cs = parseCredentials(CS_CREDENTIAL_SEED);
        assertThat(cs).hasSize(1);
        SeededCredential row = cs.get(0);
        assertThat(row.tenantId()).isEqualTo("iam");
        assertThat(row.email()).isEqualTo("cs@demo.com");

        List<SeededCredential> others = new ArrayList<>();
        for (String seed : OTHER_CREDENTIAL_SEEDS) {
            others.addAll(parseCredentials(seed));
        }
        // Control: the predicate reads the siblings (R__01 = 2, R__03 = 1, second = 1,
        // viewer = 1, platform = 1). Without it, a regex that matched nothing would make
        // both checks below pass vacuously.
        assertThat(others).hasSize(6);
        assertThat(others).extracting(SeededCredential::email).doesNotContain(row.email());
        assertThat(others).extracting(SeededCredential::accountId).doesNotContain(row.accountId());
    }

    @Test
    @DisplayName("the CS credential's hash verifies with the login path's hasher")
    void csCredentialHashVerifies() throws IOException {
        List<SeededCredential> cs = parseCredentials(CS_CREDENTIAL_SEED);
        assertThat(cs).isNotEmpty();
        assertThat(hasher.verify(DEMO_PASSWORD, cs.get(0).hash())).isTrue();
    }

    @Test
    @DisplayName("the CS operator's oidc_subject equals its iam credential's account_id")
    void csLinkKeyMatches() throws IOException {
        List<SeededOperator> operators = parseOperators(operatorSeed());
        assertThat(operators).hasSize(1);
        SeededOperator op = operators.get(0);

        SeededCredential credential = parseCredentials(CS_CREDENTIAL_SEED).get(0);
        assertThat(op.email()).isEqualTo(credential.email());
        assertThat(op.oidcSubject())
                .as("operator resolution is account_id-only (TASK-MONO-299): a mismatch "
                        + "fail-closes to a console 401 whose text is identical to a timeout's")
                .isEqualTo(credential.accountId());
    }

    @Test
    @DisplayName("the CS operator is a SITE operator: home ecommerce, exactly SUPPORT_LOCK@ecommerce, no assignment")
    void csOperatorIsSiteScopedSupportLock() throws IOException {
        String seed = operatorSeed();
        String statements = statementsOnly(seed);

        List<SeededOperator> operators = parseOperators(seed);
        assertThat(operators).hasSize(1);
        assertThat(operators.get(0).operatorId()).isEqualTo("demo-cs");
        assertThat(operators.get(0).tenantId())
                .as("the demo consumer is findable only under a site it belongs to — ecommerce")
                .isEqualTo("ecommerce");

        Matcher grant = ROLE_GRANT.matcher(statements);
        List<String> grants = new ArrayList<>();
        while (grant.find()) {
            grants.add(grant.group(2) + "@" + grant.group(1) + " → " + grant.group(3));
        }
        assertThat(grants)
                .as("a '*' grant turns every lock into a whole-account lock across sites (TASK-BE-621); "
                        + "any other role widens CS 2nd-line")
                .containsExactly("SUPPORT_LOCK@ecommerce → demo-cs");

        assertThat(statements).doesNotContainIgnoringCase("operator_tenant_assignment");
        // Control for statementsOnly(): the raw file DOES mention the table (in the comment
        // that explains its absence).
        assertThat(seed).contains("operator_tenant_assignment");
    }

    @Test
    @DisplayName("the CS operator is confined to ecommerce")
    void csOperatorIsConfinedToEcommerce() throws IOException {
        String statements = statementsOnly(operatorSeed());
        Matcher m = Pattern.compile(
                "'([0-9a-f-]{36})'\\s*,\\s*'([a-z-]+)'\\s*,\\s*NOW\\(6\\)").matcher(statements);
        assertThat(m.find()).as("VALUES must carry oidc_subject, confined_tenant_id, created_at").isTrue();
        assertThat(m.group(2)).isEqualTo("ecommerce");
        assertThat(statements).contains("confined_tenant_id = VALUES(confined_tenant_id)");
    }
}
