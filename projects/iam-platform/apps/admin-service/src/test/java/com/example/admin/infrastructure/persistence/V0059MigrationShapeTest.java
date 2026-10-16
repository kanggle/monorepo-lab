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
 * TASK-MONO-795 (ADR-MONO-083 P8 · owner decisions, 2026-10-16 UTC) — non-Docker shape-pin for
 * {@code V0059__ecommerce_fine_grained_domain_roles.sql}. Same shape as {@link V0058MigrationShapeTest} (P7); the
 * behavioural half on real MySQL is {@code V0059EcommerceRoleMigrationIntegrationTest}.
 *
 * <ol>
 *   <li>the seven new E-commerce presets, fixed ids {@code …027} – {@code …033}, listed right after
 *       {@code ecommerce-full} (no member-operate preset — member is read only, owner decision Q1);</li>
 *   <li>🔴 the widen (the thirteen E-commerce roles into every set holding {@code ECOMMERCE_OPERATOR}) runs BEFORE the
 *       delete;</li>
 *   <li>the thirteen roles in the file = the {@code ecommerce} rows of {@link DomainRoleCatalog};</li>
 *   <li>the delete touches only {@code ('ecommerce', 'ECOMMERCE_OPERATOR')};</li>
 *   <li>both partnership JSON scopes are rewritten, each {@code JSON_CONTAINS}-guarded;</li>
 *   <li>no DROP · no user variable · role writes are {@code INSERT IGNORE}.</li>
 * </ol>
 */
class V0059MigrationShapeTest {

    static final String MIGRATION_RESOURCE = "/db/migration/V0059__ecommerce_fine_grained_domain_roles.sql";

    static final List<String> V0059_PRESETS = List.of(
            "ecommerce-read", "ecommerce-order-operate", "ecommerce-product-operate", "ecommerce-settlement-operate",
            "ecommerce-promotion-operate", "ecommerce-shipping-operate", "ecommerce-notification-operate");

    static final List<String> ECOMMERCE_ROLES = List.of(
            "ECOMMERCE_ORDER_READ", "ECOMMERCE_ORDER_WRITE", "ECOMMERCE_PRODUCT_READ", "ECOMMERCE_PRODUCT_WRITE",
            "ECOMMERCE_MEMBER_READ", "ECOMMERCE_SETTLEMENT_READ", "ECOMMERCE_SETTLEMENT_WRITE",
            "ECOMMERCE_PROMOTION_READ", "ECOMMERCE_PROMOTION_WRITE", "ECOMMERCE_SHIPPING_READ",
            "ECOMMERCE_SHIPPING_WRITE", "ECOMMERCE_NOTIFICATION_READ", "ECOMMERCE_NOTIFICATION_WRITE");

    private static String sql() throws IOException {
        try (InputStream in = V0059MigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in).as("V0059 migration must be on the classpath at " + MIGRATION_RESOURCE).isNotNull();
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
    @DisplayName("🔴 the catalog's ecommerce rows are exactly the thirteen E-commerce business roles (ECOMMERCE_OPERATOR left the catalog)")
    void catalogEcommerceRows() {
        List<String> ecommerce = DomainRoleCatalog.entries().stream()
                .filter(e -> e.domainKey().equals("ecommerce")).map(DomainRoleCatalog.Entry::role).toList();
        assertThat(ecommerce).containsExactlyElementsOf(ECOMMERCE_ROLES);
    }

    @Test
    @DisplayName("🔴 presets: the seven E-commerce presets with fixed ids …027–…033, right after ecommerce-full in DomainPermissionSetPresets")
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
        assertThat(keys).containsExactlyElementsOf(V0059_PRESETS);
        assertThat(ids).containsExactly(
                "00000083-0000-7000-8000-000000000027", "00000083-0000-7000-8000-000000000028",
                "00000083-0000-7000-8000-000000000029", "00000083-0000-7000-8000-000000000030",
                "00000083-0000-7000-8000-000000000031", "00000083-0000-7000-8000-000000000032",
                "00000083-0000-7000-8000-000000000033");
        int ecommerceFull = DomainPermissionSetPresets.SYSTEM_KEYS.indexOf("ecommerce-full");
        assertThat(ecommerceFull).isNotNegative();
        assertThat(DomainPermissionSetPresets.SYSTEM_KEYS.subList(ecommerceFull + 1, ecommerceFull + 8))
                .containsExactlyElementsOf(V0059_PRESETS);
        assertThat(DomainPermissionSetPresets.SYSTEM_KEYS).hasSize(33);
    }

    @Test
    @DisplayName("🔴 the widen (INSERT IGNORE of the thirteen roles into every set holding ECOMMERCE_OPERATOR) runs BEFORE the delete")
    void widenBeforeDelete() throws IOException {
        List<String> st = statements();
        int widen = -1;
        for (int i = 0; i < st.size(); i++) {
            String s = st.get(i);
            if (s.startsWith("INSERT IGNORE INTO DOMAIN_PERMISSION_SET_ROLE")
                    && s.contains("ROLE_NAME = 'ECOMMERCE_OPERATOR'")) {
                widen = i;
            }
        }
        int delete = indexOfFirst(st, "DELETE FROM domain_permission_set_role");
        assertThat(widen).as("the widen statement exists").isNotNegative();
        assertThat(delete).as("the delete statement exists").isNotNegative();
        assertThat(widen).as("widen precedes delete").isLessThan(delete);
        String w = st.get(widen);
        for (String role : ECOMMERCE_ROLES) {
            assertThat(w).as("widen adds %s", role).contains("'" + role + "'");
        }
        assertThat(w).contains("DOMAIN_KEY = 'ECOMMERCE'");
    }

    @Test
    @DisplayName("🔴 the delete removes ('ecommerce','ECOMMERCE_OPERATOR') rows only — nothing else")
    void deleteIsNarrow() throws IOException {
        List<String> deletes = statements().stream().filter(s -> s.startsWith("DELETE")).toList();
        assertThat(deletes).hasSize(1);
        assertThat(deletes.get(0)).isEqualTo(
                "DELETE FROM DOMAIN_PERMISSION_SET_ROLE WHERE DOMAIN_KEY = 'ECOMMERCE' AND ROLE_NAME = 'ECOMMERCE_OPERATOR'");
    }

    @Test
    @DisplayName("🔴 every preset role literal is a catalog row; the new presets together cover all thirteen roles")
    void presetRolesAreCatalogRows() throws IOException {
        List<String> st = statements();
        int at = indexOfFirst(st, "INSERT IGNORE INTO domain_permission_set_role");
        assertThat(at).isNotNegative();
        Matcher m = Pattern.compile("'([A-Z-]+)'(?: AS [A-Z_]+)?, *'([A-Z]+)'(?: AS [A-Z_]+)?, *'([A-Z_]+)'")
                .matcher(st.get(at));
        Set<String> literal = new TreeSet<>();
        while (m.find()) {
            assertThat(V0059_PRESETS).contains(m.group(1).toLowerCase());
            assertThat(DomainRoleCatalog.isAssignable(m.group(2).toLowerCase(), m.group(3)))
                    .as("%s is a catalog row", m.group(3)).isTrue();
            literal.add(m.group(3));
        }
        assertThat(literal).containsExactlyInAnyOrderElementsOf(ECOMMERCE_ROLES);
    }

    @Test
    @DisplayName("🔴 both partnership JSON scopes rewritten, each guarded by JSON_CONTAINS(…, '\"ECOMMERCE_OPERATOR\"')")
    void partnershipScopesRewritten() throws IOException {
        List<String> updates = statements().stream().filter(s -> s.startsWith("UPDATE TENANT_PARTNERSHIP")).toList();
        assertThat(updates).hasSize(2);
        assertThat(updates.get(0)).startsWith("UPDATE TENANT_PARTNERSHIP SET DELEGATED_SCOPE");
        assertThat(updates.get(1)).startsWith("UPDATE TENANT_PARTNERSHIP_PARTICIPANT SET PARTICIPANT_SCOPE");
        for (String u : updates) {
            assertThat(u).contains("JSON_CONTAINS(").contains("'\"ECOMMERCE_OPERATOR\"'");
            for (String role : ECOMMERCE_ROLES) {
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
