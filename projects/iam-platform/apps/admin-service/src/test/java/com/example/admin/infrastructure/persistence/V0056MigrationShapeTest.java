package com.example.admin.infrastructure.persistence;

import com.example.admin.domain.rbac.DomainPermissionSetPresets;
import com.example.admin.domain.rbac.DomainRoleCatalog;
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
 * TASK-MONO-792 (ADR-MONO-083 P5 · owner decisions Q4 · Q5, 2026-10-15 UTC) — non-Docker shape-pin for
 * {@code V0056__scm_fine_grained_domain_roles.sql}. The behavioural half (re-running the file's own statements on
 * real MySQL against fixtures) is {@code V0056ScmRoleMigrationIntegrationTest}.
 *
 * <ol>
 *   <li>the five new SCM presets, fixed ids {@code …016} – {@code …020}, = the tail of
 *       {@link DomainPermissionSetPresets#SYSTEM_KEYS} after {@code wms-full}'s neighbours;</li>
 *   <li>🔴 the widen (add the eight SCM roles to every set holding {@code SCM_OPERATOR}) runs BEFORE the delete of
 *       {@code SCM_OPERATOR} — so no set is ever «SCM 0», even mid-file;</li>
 *   <li>the eight roles in the file = the {@code scm} rows of {@link DomainRoleCatalog};</li>
 *   <li>the delete touches only {@code ('scm', 'SCM_OPERATOR')};</li>
 *   <li>both partnership JSON scopes are rewritten, each guarded by {@code JSON_CONTAINS} (no row without the role is
 *       touched);</li>
 *   <li>no DROP · no user variable · the role writes are {@code INSERT IGNORE} (a re-run inserts 0).</li>
 * </ol>
 */
class V0056MigrationShapeTest {

    static final String MIGRATION_RESOURCE = "/db/migration/V0056__scm_fine_grained_domain_roles.sql";

    static final List<String> V0056_PRESETS = List.of(
            "scm-read", "scm-procurement-operate", "scm-demand-planning-operate",
            "scm-logistics-operate", "scm-inventory-visibility-operate");

    static final List<String> SCM_ROLES = List.of(
            "SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE",
            "SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE",
            "SCM_LOGISTICS_READ", "SCM_LOGISTICS_WRITE",
            "SCM_INVENTORY_VISIBILITY_READ", "SCM_INVENTORY_VISIBILITY_WRITE");

    /** Loaded per test so a missing file is an assertion failure in each, not a class-init error. */
    private static String sql() throws IOException {
        try (InputStream in = V0056MigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in).as("V0056 migration must be on the classpath at " + MIGRATION_RESOURCE).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Statements, whole-line comments stripped, whitespace collapsed, upper-cased, in file order. */
    private static List<String> statements() throws IOException {
        String body = V0052MigrationShapeTest.body(sql());
        List<String> out = new ArrayList<>();
        for (String s : body.split(";")) {
            String f = s.replaceAll("\\s+", " ").trim().toUpperCase();
            if (!f.isEmpty()) {
                out.add(f);
            }
        }
        return out;
    }

    private static int indexOfFirst(List<String> statements, String prefix) {
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).startsWith(prefix.toUpperCase())) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("🔴 the catalog's scm rows are exactly the eight SCM business roles (SCM_OPERATOR left the catalog)")
    void catalogScmRows() {
        List<String> scm = DomainRoleCatalog.entries().stream()
                .filter(e -> e.domainKey().equals("scm")).map(DomainRoleCatalog.Entry::role).toList();
        assertThat(scm).containsExactlyElementsOf(SCM_ROLES);
    }

    @Test
    @DisplayName("🔴 presets: the five SCM presets with fixed ids …016–…020, and they are in DomainPermissionSetPresets")
    void presets() throws IOException {
        String insert = statements().get(indexOfFirst(statements(), "INSERT IGNORE INTO domain_permission_set ("));
        Matcher m = Pattern.compile("\\('([0-9A-F-]{36})', NULL, '([A-Z0-9-]+)'").matcher(insert);
        List<String> keys = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        while (m.find()) {
            ids.add(m.group(1));
            keys.add(m.group(2).toLowerCase());
        }
        assertThat(keys).containsExactlyElementsOf(V0056_PRESETS);
        assertThat(ids).containsExactly(
                "00000083-0000-7000-8000-000000000016", "00000083-0000-7000-8000-000000000017",
                "00000083-0000-7000-8000-000000000018", "00000083-0000-7000-8000-000000000019",
                "00000083-0000-7000-8000-000000000020");
        assertThat(DomainPermissionSetPresets.SYSTEM_KEYS).containsAll(V0056_PRESETS);
        // 20 after V0056; TASK-MONO-793's V0057 adds the three ERP presets (V0057MigrationShapeTest pins those).
        assertThat(DomainPermissionSetPresets.SYSTEM_KEYS).hasSize(23);
    }

    @Test
    @DisplayName("🔴 Q5: the widen (INSERT IGNORE of the eight roles into every set holding SCM_OPERATOR) runs BEFORE the delete")
    void widenBeforeDelete() throws IOException {
        List<String> st = statements();
        int widen = -1;
        for (int i = 0; i < st.size(); i++) {
            String s = st.get(i);
            if (s.startsWith("INSERT IGNORE INTO DOMAIN_PERMISSION_SET_ROLE")
                    && s.contains("ROLE_NAME = 'SCM_OPERATOR'")) {
                widen = i;
            }
        }
        int delete = indexOfFirst(st, "DELETE FROM domain_permission_set_role");
        assertThat(widen).as("the widen statement exists").isNotNegative();
        assertThat(delete).as("the delete statement exists").isNotNegative();
        assertThat(widen).as("widen precedes delete").isLessThan(delete);
        String w = st.get(widen);
        for (String role : SCM_ROLES) {
            assertThat(w).as("widen adds %s", role).contains("'" + role + "'");
        }
        assertThat(w).contains("DOMAIN_KEY = 'SCM'");
    }

    @Test
    @DisplayName("🔴 the delete removes ('scm','SCM_OPERATOR') rows only — nothing else")
    void deleteIsNarrow() throws IOException {
        List<String> deletes = statements().stream().filter(s -> s.startsWith("DELETE")).toList();
        assertThat(deletes).hasSize(1);
        assertThat(deletes.get(0)).isEqualTo(
                "DELETE FROM DOMAIN_PERMISSION_SET_ROLE WHERE DOMAIN_KEY = 'SCM' AND ROLE_NAME = 'SCM_OPERATOR'");
    }

    @Test
    @DisplayName("🔴 every preset's role literal is a catalog row; the new presets cover the eight roles between them")
    void presetRolesAreCatalogRows() throws IOException {
        String roles = statements().get(indexOfFirst(statements(), "INSERT IGNORE INTO domain_permission_set_role"));
        Matcher m = Pattern.compile("'([A-Z-]+)'(?: AS [A-Z_]+)?, *'([A-Z]+)'(?: AS [A-Z_]+)?, *'([A-Z_]+)'")
                .matcher(roles);
        Set<String> literal = new TreeSet<>();
        while (m.find()) {
            assertThat(V0056_PRESETS).contains(m.group(1).toLowerCase());
            assertThat(DomainRoleCatalog.isAssignable(m.group(2).toLowerCase(), m.group(3)))
                    .as("%s is a catalog row", m.group(3)).isTrue();
            literal.add(m.group(3));
        }
        assertThat(literal).containsExactlyInAnyOrderElementsOf(SCM_ROLES);
    }

    @Test
    @DisplayName("🔴 Q5: both partnership JSON scopes rewritten, each guarded by JSON_CONTAINS(…, '\"SCM_OPERATOR\"')")
    void partnershipScopesRewritten() throws IOException {
        List<String> updates = statements().stream().filter(s -> s.startsWith("UPDATE TENANT_PARTNERSHIP")).toList();
        assertThat(updates).hasSize(2);
        assertThat(updates.get(0)).startsWith("UPDATE TENANT_PARTNERSHIP SET DELEGATED_SCOPE");
        assertThat(updates.get(1)).startsWith("UPDATE TENANT_PARTNERSHIP_PARTICIPANT SET PARTICIPANT_SCOPE");
        for (String u : updates) {
            assertThat(u).contains("JSON_CONTAINS(").contains("'\"SCM_OPERATOR\"'");
            for (String role : SCM_ROLES) {
                assertThat(u).contains("'" + role + "'");
            }
        }
    }

    @Test
    @DisplayName("forward-only: no DROP, no user variable, no plain INSERT into the role table")
    void forwardOnly() throws IOException {
        String body = V0052MigrationShapeTest.body(sql());
        String flat = body.replaceAll("\\s+", " ").toUpperCase();
        assertThat(flat).doesNotContain("DROP ").doesNotContain("ROLLBACK")
                .doesNotContain("INSERT INTO DOMAIN_PERMISSION_SET_ROLE");
        assertThat(Pattern.compile("@[a-z_]+").matcher(body).find()).as("no MySQL user variable").isFalse();
    }
}
