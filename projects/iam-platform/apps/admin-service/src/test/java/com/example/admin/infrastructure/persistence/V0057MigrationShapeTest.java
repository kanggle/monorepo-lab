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
 * TASK-MONO-793 (ADR-MONO-083 P6 · owner decisions, 2026-10-15 UTC) — non-Docker shape-pin for
 * {@code V0057__erp_fine_grained_domain_roles.sql}. Same shape as {@link V0056MigrationShapeTest} (SCM, P5); the
 * behavioural half on real MySQL is {@code V0057ErpRoleMigrationIntegrationTest}.
 *
 * <ol>
 *   <li>the three new ERP presets, fixed ids {@code …021} – {@code …023}, listed right after {@code erp-full};</li>
 *   <li>🔴 the widen (the four ERP roles into every set holding {@code ERP_OPERATOR}) runs BEFORE the delete;</li>
 *   <li>the four roles in the file = the {@code erp} rows of {@link DomainRoleCatalog};</li>
 *   <li>the delete touches only {@code ('erp', 'ERP_OPERATOR')};</li>
 *   <li>both partnership JSON scopes are rewritten, each {@code JSON_CONTAINS}-guarded;</li>
 *   <li>no DROP · no user variable · role writes are {@code INSERT IGNORE}.</li>
 * </ol>
 */
class V0057MigrationShapeTest {

    static final String MIGRATION_RESOURCE = "/db/migration/V0057__erp_fine_grained_domain_roles.sql";

    static final List<String> V0057_PRESETS = List.of("erp-read", "erp-masterdata-operate", "erp-approval-operate");

    static final List<String> ERP_ROLES = List.of(
            "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE");

    private static String sql() throws IOException {
        try (InputStream in = V0057MigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in).as("V0057 migration must be on the classpath at " + MIGRATION_RESOURCE).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

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
    @DisplayName("🔴 the catalog's erp rows are exactly the four ERP business roles (ERP_OPERATOR left the catalog)")
    void catalogErpRows() {
        List<String> erp = DomainRoleCatalog.entries().stream()
                .filter(e -> e.domainKey().equals("erp")).map(DomainRoleCatalog.Entry::role).toList();
        assertThat(erp).containsExactlyElementsOf(ERP_ROLES);
    }

    @Test
    @DisplayName("🔴 presets: the three ERP presets with fixed ids …021–…023, right after erp-full in DomainPermissionSetPresets")
    void presets() throws IOException {
        List<String> st = statements();
        int at = indexOfFirst(st, "INSERT IGNORE INTO domain_permission_set (");
        assertThat(at).as("the preset insert exists").isNotNegative();
        Matcher m = Pattern.compile("\\('([0-9A-F-]{36})', NULL, '([A-Z0-9-]+)'").matcher(st.get(at));
        List<String> keys = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        while (m.find()) {
            ids.add(m.group(1));
            keys.add(m.group(2).toLowerCase());
        }
        assertThat(keys).containsExactlyElementsOf(V0057_PRESETS);
        assertThat(ids).containsExactly(
                "00000083-0000-7000-8000-000000000021", "00000083-0000-7000-8000-000000000022",
                "00000083-0000-7000-8000-000000000023");
        int erpFull = DomainPermissionSetPresets.SYSTEM_KEYS.indexOf("erp-full");
        assertThat(DomainPermissionSetPresets.SYSTEM_KEYS.subList(erpFull + 1, erpFull + 4))
                .containsExactlyElementsOf(V0057_PRESETS);
        // 23 after V0057; TASK-MONO-794's V0058 adds the three Finance presets.
        assertThat(DomainPermissionSetPresets.SYSTEM_KEYS).hasSize(26);
    }

    @Test
    @DisplayName("🔴 the widen (INSERT IGNORE of the four roles into every set holding ERP_OPERATOR) runs BEFORE the delete")
    void widenBeforeDelete() throws IOException {
        List<String> st = statements();
        int widen = -1;
        for (int i = 0; i < st.size(); i++) {
            String s = st.get(i);
            if (s.startsWith("INSERT IGNORE INTO DOMAIN_PERMISSION_SET_ROLE")
                    && s.contains("ROLE_NAME = 'ERP_OPERATOR'")) {
                widen = i;
            }
        }
        int delete = indexOfFirst(st, "DELETE FROM domain_permission_set_role");
        assertThat(widen).as("the widen statement exists").isNotNegative();
        assertThat(delete).as("the delete statement exists").isNotNegative();
        assertThat(widen).as("widen precedes delete").isLessThan(delete);
        String w = st.get(widen);
        for (String role : ERP_ROLES) {
            assertThat(w).as("widen adds %s", role).contains("'" + role + "'");
        }
        assertThat(w).contains("DOMAIN_KEY = 'ERP'");
    }

    @Test
    @DisplayName("🔴 the delete removes ('erp','ERP_OPERATOR') rows only — nothing else")
    void deleteIsNarrow() throws IOException {
        List<String> deletes = statements().stream().filter(s -> s.startsWith("DELETE")).toList();
        assertThat(deletes).hasSize(1);
        assertThat(deletes.get(0)).isEqualTo(
                "DELETE FROM DOMAIN_PERMISSION_SET_ROLE WHERE DOMAIN_KEY = 'ERP' AND ROLE_NAME = 'ERP_OPERATOR'");
    }

    @Test
    @DisplayName("🔴 every preset role literal is a catalog row; the new presets cover the four roles between them")
    void presetRolesAreCatalogRows() throws IOException {
        List<String> st = statements();
        int at = indexOfFirst(st, "INSERT IGNORE INTO domain_permission_set_role");
        assertThat(at).isNotNegative();
        Matcher m = Pattern.compile("'([A-Z-]+)'(?: AS [A-Z_]+)?, *'([A-Z]+)'(?: AS [A-Z_]+)?, *'([A-Z_]+)'")
                .matcher(st.get(at));
        Set<String> literal = new TreeSet<>();
        while (m.find()) {
            assertThat(V0057_PRESETS).contains(m.group(1).toLowerCase());
            assertThat(DomainRoleCatalog.isAssignable(m.group(2).toLowerCase(), m.group(3)))
                    .as("%s is a catalog row", m.group(3)).isTrue();
            literal.add(m.group(3));
        }
        assertThat(literal).containsExactlyInAnyOrderElementsOf(ERP_ROLES);
    }

    @Test
    @DisplayName("🔴 both partnership JSON scopes rewritten, each guarded by JSON_CONTAINS(…, '\"ERP_OPERATOR\"')")
    void partnershipScopesRewritten() throws IOException {
        List<String> updates = statements().stream().filter(s -> s.startsWith("UPDATE TENANT_PARTNERSHIP")).toList();
        assertThat(updates).hasSize(2);
        assertThat(updates.get(0)).startsWith("UPDATE TENANT_PARTNERSHIP SET DELEGATED_SCOPE");
        assertThat(updates.get(1)).startsWith("UPDATE TENANT_PARTNERSHIP_PARTICIPANT SET PARTICIPANT_SCOPE");
        for (String u : updates) {
            assertThat(u).contains("JSON_CONTAINS(").contains("'\"ERP_OPERATOR\"'");
            for (String role : ERP_ROLES) {
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
