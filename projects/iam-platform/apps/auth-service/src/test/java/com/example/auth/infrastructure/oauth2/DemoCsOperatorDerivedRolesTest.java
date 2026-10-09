package com.example.auth.infrastructure.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TASK-MONO-781 — pins the DISCLOSED trade-off of the CS 2nd-line demo operator
 * ({@code cs@demo.com}, owner decision A 2026-10-09 UTC).
 *
 * <p><b>The trade-off.</b> The console always assumes the tenant it selects, and the assumed
 * token's {@code roles} are DERIVED from that tenant's ACTIVE subscriptions
 * ({@link TenantClaimTokenCustomizer} → {@link OperatorRoleDerivation}, ADR-MONO-035) — not
 * from the operator's SUPPORT_LOCK grant. The CS operator's home (and only) tenant is
 * {@code ecommerce}, so it also receives that tenant's domain operator roles. That fact is
 * written in front of people (seed header, multi-tenancy.md, the console guide row), and a
 * written fact nothing can fail on drifts: add a subscription to {@code ecommerce}, or widen
 * the derivation table, and the disclosure becomes false in silence.
 *
 * <p><b>What this test does instead.</b> It recomputes the role set from the same three
 * sources production uses, with nothing hard-coded on the input side:
 * <ol>
 *   <li>the CS operator's tenant — read out of admin-service's
 *       {@code R__seed_demo_cs_operator.sql} (home tenant and confinement);</li>
 *   <li>that tenant's ACTIVE subscriptions — parsed out of EVERY account-service migration
 *       ({@code db/migration} and {@code db/migration-dev}) that writes
 *       {@code tenant_domain_subscription}. 🔴 A statement in a shape this parser does not
 *       know FAILS the test rather than being skipped — a skipped statement is exactly how a
 *       new subscription would slip past;</li>
 *   <li>{@link OperatorRoleDerivation#fromEntitledDomains} — the production derivation.</li>
 * </ol>
 * and compares the result with the role list the disclosure names. Controls prove each
 * parser shape matched something real (a parser that matches nothing would make the
 * comparison vacuous).
 *
 * <p>Limit (recorded, not testable here): subscriptions can also change at RUNTIME through
 * the admin API on the public demo. This test pins the seeded state.
 *
 * <p>Lives in this package because {@link OperatorRoleDerivation} is package-private. The
 * files it reads are declared as Gradle {@code test} inputs in this module's build.gradle.
 */
@DisplayName("TASK-MONO-781 CS demo operator — derived domain roles (disclosed trade-off)")
class DemoCsOperatorDerivedRolesTest {

    private static final Path CS_OPERATOR_SEED = Path.of("..", "admin-service", "src", "main",
            "resources", "db", "migration-dev", "R__seed_demo_cs_operator.sql");
    private static final List<Path> ACCOUNT_MIGRATION_DIRS = List.of(
            Path.of("..", "account-service", "src", "main", "resources", "db", "migration"),
            Path.of("..", "account-service", "src", "main", "resources", "db", "migration-dev"));

    /** What the disclosure says: ECOMMERCE_OPERATOR + the WMS operator tier (writes included). */
    private static final List<String> DISCLOSED_ROLES = List.of(
            "ECOMMERCE_OPERATOR",
            "WMS_OPERATOR",
            "OUTBOUND_READ", "OUTBOUND_WRITE",
            "INBOUND_READ", "INBOUND_WRITE",
            "INVENTORY_READ", "INVENTORY_WRITE",
            "MASTER_READ");

    private static final String TABLE = "tenant_domain_subscription";

    /** ('<tenant>', '<domain>', '<STATUS>', ... — one VALUES tuple. */
    private static final Pattern VALUES_TUPLE = Pattern.compile(
            "\\(\\s*'([a-z0-9-]+)'\\s*,\\s*'([a-z-]+)'\\s*,\\s*'([A-Z]+)'");
    /** SELECT t.tenant_id, '<domain>', '<STATUS>' ... WHERE t.tenant_id = '<tenant>'. */
    private static final Pattern SELECT_ONE = Pattern.compile(
            "SELECT\\s+t\\.tenant_id\\s*,\\s*'([a-z-]+)'\\s*,\\s*'([A-Z]+)'.*?"
                    + "WHERE\\s+t\\.tenant_id\\s*=\\s*'([a-z0-9-]+)'\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** SELECT t.tenant_id, t.tenant_id, '<STATUS>' ... WHERE t.tenant_id IN (...) — self-subscription. */
    private static final Pattern SELECT_SELF = Pattern.compile(
            "SELECT\\s+t\\.tenant_id\\s*,\\s*t\\.tenant_id\\s*,\\s*'([A-Z]+)'.*?"
                    + "WHERE\\s+t\\.tenant_id\\s+IN\\s*\\(([^)]*)\\)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private record Parsed(Map<String, Set<String>> activeByTenant, List<String> unrecognized,
                          int valuesRows, int selectOneRows, int selectSelfRows) {}

    private static String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("--[^\\n]*", "");
    }

    private static Parsed parseSubscriptions() throws IOException {
        Map<String, Set<String>> active = new TreeMap<>();
        List<String> unrecognized = new ArrayList<>();
        int values = 0;
        int selectOne = 0;
        int selectSelf = 0;
        for (Path dir : ACCOUNT_MIGRATION_DIRS) {
            assertThat(dir).as("account-service migrations must be resolvable from this module").isDirectory();
            List<Path> files;
            try (Stream<Path> s = Files.list(dir)) {
                files = s.filter(p -> p.getFileName().toString().endsWith(".sql")).sorted().toList();
            }
            for (Path file : files) {
                String sql = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                for (String raw : sql.split(";")) {
                    String stmt = raw.strip();
                    if (!stmt.toLowerCase().contains(TABLE)) {
                        continue;
                    }
                    String lower = stmt.toLowerCase();
                    if (lower.startsWith("create table") || lower.startsWith("alter table")) {
                        continue; // DDL — writes no subscription row
                    }
                    if (!lower.matches("(?s)insert\\s+(ignore\\s+)?into\\s+" + TABLE + "\\b.*")) {
                        unrecognized.add(file.getFileName() + ": " + firstLine(stmt));
                        continue;
                    }
                    int valuesAt = lower.indexOf("values");
                    if (valuesAt >= 0 && !lower.contains("select")) {
                        Matcher m = VALUES_TUPLE.matcher(stmt.substring(valuesAt));
                        int n = 0;
                        while (m.find()) {
                            n++;
                            if ("ACTIVE".equals(m.group(3))) {
                                active.computeIfAbsent(m.group(1), k -> new TreeSet<>()).add(m.group(2));
                            }
                        }
                        if (n == 0) {
                            unrecognized.add(file.getFileName() + ": " + firstLine(stmt));
                        }
                        values += n;
                        continue;
                    }
                    Matcher one = SELECT_ONE.matcher(stmt);
                    if (one.find()) {
                        selectOne++;
                        if ("ACTIVE".equals(one.group(2))) {
                            active.computeIfAbsent(one.group(3), k -> new TreeSet<>()).add(one.group(1));
                        }
                        continue;
                    }
                    Matcher self = SELECT_SELF.matcher(stmt);
                    if (self.find()) {
                        for (String t : self.group(2).split(",")) {
                            String tenant = t.strip().replace("'", "");
                            if (tenant.isEmpty()) {
                                continue;
                            }
                            selectSelf++;
                            if ("ACTIVE".equals(self.group(1))) {
                                active.computeIfAbsent(tenant, k -> new TreeSet<>()).add(tenant);
                            }
                        }
                        continue;
                    }
                    unrecognized.add(file.getFileName() + ": " + firstLine(stmt));
                }
            }
        }
        return new Parsed(active, unrecognized, values, selectOne, selectSelf);
    }

    private static String firstLine(String stmt) {
        int nl = stmt.indexOf('\n');
        return nl < 0 ? stmt : stmt.substring(0, nl);
    }

    /** The CS operator's home tenant, which must equal its confinement. */
    private static String csTenant() throws IOException {
        assertThat(CS_OPERATOR_SEED).as("the admin-service CS operator seed must be resolvable").exists();
        String seed = stripComments(Files.readString(CS_OPERATOR_SEED, StandardCharsets.UTF_8));
        Matcher home = Pattern.compile("'demo-cs'\\s*,\\s*'([a-z0-9-]+)'\\s*,\\s*'cs@demo\\.com'").matcher(seed);
        assertThat(home.find()).as("the demo-cs VALUES row must be parseable").isTrue();
        Matcher confined = Pattern.compile("'[0-9a-f-]{36}'\\s*,\\s*'([a-z0-9-]+)'\\s*,\\s*NOW\\(6\\)").matcher(seed);
        assertThat(confined.find()).as("the confined_tenant_id value must be parseable").isTrue();
        assertThat(confined.group(1))
                .as("the confinement must equal the home tenant — otherwise the operator could assume a "
                        + "second tenant whose subscriptions are not the ones disclosed")
                .isEqualTo(home.group(1));
        return home.group(1);
    }

    @Test
    @DisplayName("every account-service write to tenant_domain_subscription is in a shape this test understands")
    void everySubscriptionStatementIsRecognised() throws IOException {
        Parsed p = parseSubscriptions();
        assertThat(p.unrecognized())
                .as("an unparsed statement could add or cancel a subscription without this test seeing it — "
                        + "teach the parser the new shape")
                .isEmpty();
        // Controls — each parser shape matched real, known rows (non-vacuity):
        //   VALUES   → acme-corp (V0020) = {finance, wms}
        //   SELECT=  → fan-platform (V0031) = {fan}
        //   SELECT IN self-subscription → wms (V0019) = {wms}
        assertThat(p.valuesRows()).isPositive();
        assertThat(p.selectOneRows()).isPositive();
        assertThat(p.selectSelfRows()).isPositive();
        assertThat(p.activeByTenant().get("acme-corp")).containsExactlyInAnyOrder("finance", "wms");
        assertThat(p.activeByTenant().get("fan-platform")).containsExactly("fan");
        assertThat(p.activeByTenant().get("wms")).containsExactly("wms");
    }

    @Test
    @DisplayName("the CS operator's tenant subscribes to exactly {ecommerce, wms}")
    void csTenantSubscriptionsAreExactlyTheDisclosedOnes() throws IOException {
        String tenant = csTenant();
        assertThat(tenant).isEqualTo("ecommerce");
        assertThat(parseSubscriptions().activeByTenant().get(tenant))
                .as("the disclosure names the ecommerce + WMS operator roles; a new subscription on "
                        + "`%s` widens what cs@demo.com receives — update the disclosure (seed header, "
                        + "multi-tenancy.md, the console guide row) in the same change", tenant)
                .containsExactlyInAnyOrder("ecommerce", "wms");
    }

    @Test
    @DisplayName("assuming that tenant derives exactly the disclosed role set (ECOMMERCE_OPERATOR + WMS operator tier)")
    void derivedRolesAreExactlyTheDisclosedOnes() throws IOException {
        String tenant = csTenant();
        List<String> domains = new ArrayList<>(parseSubscriptions().activeByTenant().get(tenant));

        List<String> derived = OperatorRoleDerivation.fromEntitledDomains(domains);

        assertThat(derived)
                .as("TenantClaimTokenCustomizer derives the assume-tenant roles from the selected tenant's "
                        + "ACTIVE subscriptions — this is what cs@demo.com holds in `%s` besides SUPPORT_LOCK", tenant)
                .containsExactlyInAnyOrderElementsOf(DISCLOSED_ROLES);
        // The disclosure says «쓰기 포함» — keep that sentence honest in both directions.
        assertThat(derived).contains("OUTBOUND_WRITE", "INBOUND_WRITE", "INVENTORY_WRITE");
    }
}
