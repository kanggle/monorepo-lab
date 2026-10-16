package com.example.admin.infrastructure.persistence;

import com.example.admin.domain.rbac.DomainPermissionSetPresets;
import com.example.admin.domain.rbac.DomainRoleCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-631 (ADR-MONO-083 P2 · AC-5) — non-Docker shape-pin for
 * {@code V0052__create_domain_permission_sets_and_backfill.sql} (the V0042 / V0044 / V0051 form). What a green
 * Testcontainers boot on a FRESH database cannot catch, and this pins:
 * <ol>
 *   <li><b>The source is in the primary key</b> of {@code operator_domain_set_grant} (ADR-083 D4 — the
 *       {@code group_origin}-outside-the-key shape is BE-631 Failure Scenario 1).</li>
 *   <li><b>The group_origin ↔ source_key invariant.</b> MySQL refuses a CHECK on a column that has an FK
 *       referential action (ER 3823), so the data-model CHECK is split: the half without FK columns is a CHECK, the
 *       other half is the application's — and this test pins that the CHECK names no FK column.</li>
 *   <li><b>Presets</b>: exactly {@link DomainPermissionSetPresets#SYSTEM_KEYS}, fixed set ids, and role rows that
 *       agree with {@link DomainRoleCatalog} (a single-domain preset ⊆ its domain, {@code <domain>-full} built from
 *       them, {@code all-domains-full} and {@code legacy-compat} = every {@code <domain>-full}).</li>
 *   <li><b>The backfill</b>: both statements are NOT-EXISTS guarded on (operator, tenant, set, MIGRATION) — a
 *       re-run inserts 0; (a) excludes '*' operators and '*' memberships; (b) is '*' operators × '*'; plain INSERT
 *       (no IGNORE) so a swallowed error never stands in for the guard; nothing is UPDATEd / DELETEd.</li>
 *   <li>{@code domain_access.manage} → SUPER_ADMIN + TENANT_ADMIN only.</li>
 * </ol>
 * The behavioural half (re-running the file's own statements on real MySQL) is
 * {@code V0052DomainSetBackfillIntegrationTest}.
 */
class V0052MigrationShapeTest {

    static final String MIGRATION_RESOURCE = "/db/migration/V0052__create_domain_permission_sets_and_backfill.sql";

    /**
     * TASK-MONO-792 — V0052 is frozen (Flyway checksum), the catalog and the preset list are not: later migrations
     * (V0056 = P5 SCM) add presets and retire / add catalog rows. So this test pins V0052 against the catalog AS IT WAS
     * when V0052 shipped: the first 15 preset keys, and its role literals = today's catalog minus what was added
     * later, plus what was retired later. Each later phase extends the two sets below and pins its own file
     * (V0056MigrationShapeTest).
     */
    static final List<String> V0052_SYSTEM_KEYS = DomainPermissionSetPresets.SYSTEM_KEYS.stream()
            .filter(k -> !k.startsWith("scm-") || k.equals("scm-full"))
            // TASK-MONO-793 (V0057 = P6 ERP)
            .filter(k -> !k.startsWith("erp-") || k.equals("erp-full"))
            .toList();
    static final Set<String> RETIRED_AFTER_V0052 = Set.of("SCM_OPERATOR", "ERP_OPERATOR");
    static final Set<String> ADDED_AFTER_V0052 = Set.of(
            "SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE", "SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE",
            "SCM_LOGISTICS_READ", "SCM_LOGISTICS_WRITE", "SCM_INVENTORY_VISIBILITY_READ",
            "SCM_INVENTORY_VISIBILITY_WRITE",
            "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE");

    /** The catalog's role names as of V0052 (= today's, minus the later additions, plus the later retirements). */
    static Set<String> catalogAtV0052() {
        Set<String> out = new TreeSet<>(DomainRoleCatalog.roleNames());
        out.removeAll(ADDED_AFTER_V0052);
        out.addAll(RETIRED_AFTER_V0052);
        return out;
    }

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = V0052MigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in).as("V0052 migration must be on the classpath at " + MIGRATION_RESOURCE).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Statements only — whole-line comments AND trailing {@code --} comments stripped. */
    static String body(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n")) {
            if (line.stripLeading().startsWith("--")) {
                continue;
            }
            int trailing = line.indexOf(" -- ");
            sb.append(trailing >= 0 ? line.substring(0, trailing) : line).append('\n');
        }
        return sb.toString();
    }

    private static String flat() {
        return body(sql).replaceAll("\\s+", " ").toUpperCase();
    }

    /** The statement (whitespace-collapsed, upper-cased) that starts with {@code prefix}. */
    private static List<String> statementsStartingWith(String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : body(sql).split(";")) {
            String f = s.replaceAll("\\s+", " ").trim().toUpperCase();
            if (f.startsWith(prefix.toUpperCase())) {
                out.add(f);
            }
        }
        return out;
    }

    @Test
    @DisplayName("four tables, each created IF NOT EXISTS")
    void fourTables() {
        String flat = flat();
        for (String table : List.of("DOMAIN_PERMISSION_SET", "DOMAIN_PERMISSION_SET_ROLE",
                "OPERATOR_DOMAIN_SET_GRANT", "OPERATOR_GROUP_DOMAIN_SET_GRANT")) {
            assertThat(flat).contains("CREATE TABLE IF NOT EXISTS " + table + " (");
        }
    }

    @Test
    @DisplayName("🔴 operator_domain_set_grant PK = (operator_id, tenant_id, set_id, source_key) — the source is IN the key")
    void grantPrimaryKeyCarriesTheSource() {
        String grant = statementsStartingWith("CREATE TABLE IF NOT EXISTS operator_domain_set_grant").get(0);
        assertThat(grant).contains("PRIMARY KEY (OPERATOR_ID, TENANT_ID, SET_ID, SOURCE_KEY)");
        assertThat(grant).contains("SOURCE_KEY VARCHAR(48) NOT NULL");
        assertThat(grant).contains("GROUP_ORIGIN BIGINT NULL");
        // FKs: operator CASCADE · set RESTRICT · group CASCADE (the group-delete safety net) · granter SET NULL
        assertThat(grant).contains("FOREIGN KEY (OPERATOR_ID) REFERENCES ADMIN_OPERATORS(ID) ON DELETE CASCADE");
        assertThat(grant).contains("FOREIGN KEY (SET_ID) REFERENCES DOMAIN_PERMISSION_SET(ID) ON DELETE RESTRICT");
        assertThat(grant).contains("FOREIGN KEY (GROUP_ORIGIN) REFERENCES OPERATOR_GROUP(ID) ON DELETE CASCADE");
        assertThat(grant).contains("FOREIGN KEY (GRANTED_BY) REFERENCES ADMIN_OPERATORS(ID) ON DELETE SET NULL");
        assertThat(grant).contains("KEY IDX_OPERATOR_DOMAIN_SET_GRANT_TENANT_SET (TENANT_ID, SET_ID)");
        assertThat(grant).contains("KEY IDX_OPERATOR_DOMAIN_SET_GRANT_GROUP (GROUP_ORIGIN)");
    }

    @Test
    @DisplayName("grant CHECK: source vocabulary + «a GROUP row is never '*'», and it names NO FK column (ER 3823)")
    void grantCheckAvoidsFkColumns() {
        String grant = statementsStartingWith("CREATE TABLE IF NOT EXISTS operator_domain_set_grant").get(0);
        Matcher m = Pattern.compile("CONSTRAINT CK_OPERATOR_DOMAIN_SET_GRANT_SOURCE CHECK \\((.*?)\\)\\s*,\\s*CONSTRAINT")
                .matcher(grant);
        assertThat(m.find()).as("the source CHECK is present").isTrue();
        String check = m.group(1);
        assertThat(check).contains("SOURCE_KEY IN ('DIRECT', 'MIGRATION')");
        assertThat(check).contains("SOURCE_KEY LIKE 'GROUP:%' AND TENANT_ID <> '*'");
        for (String fkColumn : List.of("GROUP_ORIGIN", "OPERATOR_ID", "SET_ID", "GRANTED_BY")) {
            assertThat(check).as("a CHECK on FK column %s fails on MySQL (ER 3823)", fkColumn).doesNotContain(fkColumn);
        }
    }

    @Test
    @DisplayName("domain_permission_set: '*' never owns a set · preset ⟺ no tenant · set_id / system_key unique")
    void setTableChecks() {
        String set = statementsStartingWith("CREATE TABLE IF NOT EXISTS domain_permission_set (").get(0);
        assertThat(set).contains("CHECK (TENANT_ID IS NULL OR TENANT_ID <> '*')");
        assertThat(set).contains("CHECK ((TENANT_ID IS NULL) = (SYSTEM_KEY IS NOT NULL))");
        assertThat(set).contains("UNIQUE KEY UK_DOMAIN_PERMISSION_SET_SET_ID (SET_ID)");
        assertThat(set).contains("UNIQUE KEY UK_DOMAIN_PERMISSION_SET_SYSTEM_KEY (SYSTEM_KEY)");
        String role = statementsStartingWith("CREATE TABLE IF NOT EXISTS domain_permission_set_role").get(0);
        assertThat(role).contains("ROLE_NAME VARCHAR(40) NOT NULL");
        assertThat(role).contains("PRIMARY KEY (SET_ID, DOMAIN_KEY, ROLE_NAME)");
        assertThat(role).contains("REFERENCES DOMAIN_PERMISSION_SET(ID) ON DELETE CASCADE");
        String group = statementsStartingWith("CREATE TABLE IF NOT EXISTS operator_group_domain_set_grant").get(0);
        assertThat(group).contains("PRIMARY KEY (GROUP_ID, SET_ID)");
        assertThat(group).contains("REFERENCES DOMAIN_PERMISSION_SET(ID) ON DELETE RESTRICT");
    }

    @Test
    @DisplayName("presets: exactly DomainPermissionSetPresets.SYSTEM_KEYS, each with a fixed set id, tenant NULL")
    void presetKeysAndFixedIds() {
        String insert = statementsStartingWith("INSERT IGNORE INTO domain_permission_set (").get(0);
        Matcher m = Pattern.compile("\\('([0-9A-F-]{36})', NULL, '([A-Z0-9-]+)'").matcher(insert);
        List<String> keys = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        while (m.find()) {
            ids.add(m.group(1));
            keys.add(m.group(2).toLowerCase());
        }
        assertThat(keys).containsExactlyElementsOf(V0052_SYSTEM_KEYS);
        assertThat(keys).hasSize(15);
        assertThat(ids).as("one fixed, distinct id per preset").hasSize(V0052_SYSTEM_KEYS.size());
    }

    @Test
    @DisplayName("preset roles agree with DomainRoleCatalog — every literal is a catalog row; each domain-full covers its domain")
    void presetRolesMatchCatalog() {
        String singles = statementsStartingWith("INSERT IGNORE INTO domain_permission_set_role").get(0);
        Matcher m = Pattern.compile("'([A-Z-]+)'(?: AS [A-Z_]+)?, *'([A-Z]+)'(?: AS [A-Z_]+)?, *'([A-Z_]+)'")
                .matcher(singles);
        Set<String> literalRoles = new TreeSet<>();
        Set<String> fullDomains = new TreeSet<>();
        while (m.find()) {
            String domain = m.group(2).toLowerCase();
            String role = m.group(3);
            assertThat(DomainRoleCatalog.isAssignable(domain, role) || RETIRED_AFTER_V0052.contains(role))
                    .as("%s:%s is a catalog row (or one retired after V0052)", domain, role).isTrue();
            literalRoles.add(role);
            if (m.group(1).endsWith("-FULL")) {
                fullDomains.add(domain);
            }
        }
        // every catalog role appears as a literal (wms through its single-purpose presets, others through <d>-full)
        assertThat(literalRoles).containsExactlyInAnyOrderElementsOf(catalogAtV0052());
        assertThat(fullDomains).containsExactlyInAnyOrder("scm", "erp", "finance", "ecommerce", "fan", "mes");
        String flat = flat();
        // wms-full = the union of the five wms single-purpose presets
        assertThat(flat).contains("SRC.SYSTEM_KEY IN ('WMS-INVENTORY-OPERATE', 'WMS-INBOUND-OPERATE', "
                + "'WMS-OUTBOUND-OPERATE', 'WMS-MASTER-READ', 'WMS-OPS-ACTIONS')");
        assertThat(flat).contains("WHERE T.SYSTEM_KEY = 'WMS-FULL'");
        // all-domains-full and legacy-compat = the union of every <domain>-full
        assertThat(flat).contains("SRC.SYSTEM_KEY IN ('WMS-FULL', 'SCM-FULL', 'ERP-FULL', 'FINANCE-FULL', "
                + "'ECOMMERCE-FULL', 'FAN-FULL', 'MES-FULL')");
        assertThat(flat).contains("WHERE T.SYSTEM_KEY IN ('ALL-DOMAINS-FULL', 'LEGACY-COMPAT')");
    }

    @Test
    @DisplayName("domain_access.manage → SUPER_ADMIN and TENANT_ADMIN only (INSERT IGNORE)")
    void permissionSeed() {
        List<String> seeds = statementsStartingWith("INSERT IGNORE INTO admin_role_permissions");
        assertThat(seeds).hasSize(2);
        assertThat(seeds).allMatch(s -> s.contains("'DOMAIN_ACCESS.MANAGE'"));
        assertThat(String.join(" ", seeds)).contains("NAME = 'SUPER_ADMIN'").contains("NAME = 'TENANT_ADMIN'");
    }

    @Test
    @DisplayName("backfill (a): every membership of a non-'*' operator, '*' memberships excluded, NOT-EXISTS guarded")
    void backfillMemberships() {
        List<String> backfill = statementsStartingWith("INSERT INTO operator_domain_set_grant");
        assertThat(backfill).as("two backfill statements: (a) memberships, (b) '*' operators").hasSize(2);
        String a = backfill.get(0);
        assertThat(a).contains("SELECT A.OPERATOR_ID, A.TENANT_ID, S.ID, 'MIGRATION', NULL, NULL, NOW(6)");
        assertThat(a).contains("FROM OPERATOR_TENANT_ASSIGNMENT A JOIN ADMIN_OPERATORS O ON O.ID = A.OPERATOR_ID");
        assertThat(a).contains("JOIN DOMAIN_PERMISSION_SET S ON S.SYSTEM_KEY = 'LEGACY-COMPAT'");
        assertThat(a).contains("WHERE O.TENANT_ID <> '*' AND A.TENANT_ID <> '*'");
        assertThat(a).contains("NOT EXISTS ( SELECT 1 FROM (SELECT OPERATOR_ID, TENANT_ID, SET_ID, SOURCE_KEY "
                + "FROM OPERATOR_DOMAIN_SET_GRANT) G WHERE G.OPERATOR_ID = A.OPERATOR_ID AND G.TENANT_ID = A.TENANT_ID "
                + "AND G.SET_ID = S.ID AND G.SOURCE_KEY = 'MIGRATION' )");
    }

    @Test
    @DisplayName("backfill (b): every '*' operator × '*' × legacy-compat × MIGRATION (OD-3), NOT-EXISTS guarded")
    void backfillPlatformOperators() {
        String b = statementsStartingWith("INSERT INTO operator_domain_set_grant").get(1);
        assertThat(b).contains("SELECT O.ID, '*', S.ID, 'MIGRATION', NULL, NULL, NOW(6)");
        assertThat(b).contains("WHERE O.TENANT_ID = '*'");
        assertThat(b).contains("G.OPERATOR_ID = O.ID AND G.TENANT_ID = '*' AND G.SET_ID = S.ID "
                + "AND G.SOURCE_KEY = 'MIGRATION'");
        // no membership row is ever created for a '*' operator (ADR-082 D2)
        assertThat(flat()).doesNotContain("INSERT INTO OPERATOR_TENANT_ASSIGNMENT");
    }

    @Test
    @DisplayName("forward-only · additive · no swallowed errors on the backfill · no cross-statement user variable")
    void forwardOnly() {
        String flat = flat();
        assertThat(flat)
                .doesNotContain("ROLLBACK")
                .doesNotContain("DROP ")
                .doesNotContain("DELETE FROM")
                .doesNotContain("UPDATE ")
                .doesNotContain("REPLACE INTO")
                .doesNotContain("ON DUPLICATE KEY")
                .doesNotContain("INSERT IGNORE INTO OPERATOR_DOMAIN_SET_GRANT");
        assertThat(Pattern.compile("@[a-z_]+").matcher(body(sql)).find())
                .as("V0016 cycle-2 trap: no MySQL user variable").isFalse();
    }
}
