package com.example.admin.domain.rbac;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-477 / ADR-MONO-045 D3 — unit tests for the {@link ScopeSet} value object:
 * normalization, triple-intersection math, admin-role cap, subset check.
 */
class ScopeSetTest {

    @Test
    @DisplayName("normalize: trims, drops blanks, dedupes, preserves order")
    void normalize() {
        ScopeSet s = ScopeSet.of(List.of(" wms ", "wms", "scm", "  "), List.of("R1", "R1", "R2"));
        assertThat(s.domains()).containsExactly("wms", "scm");
        assertThat(s.roles()).containsExactly("R1", "R2");
    }

    @Test
    @DisplayName("null collections → empty scope")
    void nullCollections() {
        ScopeSet s = ScopeSet.of(null, null);
        assertThat(s.isEmpty()).isTrue();
        assertThat(s.domains()).isEmpty();
        assertThat(s.roles()).isEmpty();
    }

    @Test
    @DisplayName("intersect: element-wise on domains and roles independently, preserving this's order")
    void intersect() {
        ScopeSet a = ScopeSet.of(List.of("wms", "scm", "finance"), List.of("R1", "R2", "R3"));
        ScopeSet b = ScopeSet.of(List.of("scm", "wms"), List.of("R2", "R9"));
        ScopeSet r = a.intersect(b);
        assertThat(r.domains()).containsExactly("wms", "scm");
        assertThat(r.roles()).containsExactly("R2");
    }

    @Test
    @DisplayName("intersect with disjoint → empty")
    void intersectDisjoint() {
        ScopeSet a = ScopeSet.of(List.of("wms"), List.of("R1"));
        ScopeSet b = ScopeSet.of(List.of("scm"), List.of("R2"));
        assertThat(a.intersect(b).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("triple-intersection: delegated ∩ participant ∩ host-holds")
    void tripleIntersection() {
        ScopeSet delegated = ScopeSet.of(List.of("wms", "scm"), List.of("WMS_OP", "SCM_PLANNER"));
        ScopeSet participant = ScopeSet.of(List.of("wms"), List.of("WMS_OP"));
        ScopeSet hostHolds = ScopeSet.of(List.of("wms", "scm", "finance"),
                List.of("WMS_OP", "SCM_PLANNER", "FIN"));
        ScopeSet derived = delegated.intersect(participant).intersect(hostHolds);
        assertThat(derived.domains()).containsExactly("wms");
        assertThat(derived.roles()).containsExactly("WMS_OP");
    }

    @Test
    @DisplayName("containsAdminRole: any of SUPER_ADMIN/TENANT_ADMIN/TENANT_BILLING_ADMIN → true")
    void containsAdminRole() {
        assertThat(ScopeSet.of(List.of("wms"), List.of("WMS_OP")).containsAdminRole()).isFalse();
        assertThat(ScopeSet.of(List.of("wms"), List.of("TENANT_ADMIN")).containsAdminRole()).isTrue();
        assertThat(ScopeSet.of(List.of("wms"), List.of("SUPER_ADMIN")).containsAdminRole()).isTrue();
        assertThat(ScopeSet.of(List.of("wms"), List.of("TENANT_BILLING_ADMIN")).containsAdminRole()).isTrue();
    }

    @Test
    @DisplayName("isSubsetOf: both domains and roles must be subsets")
    void isSubsetOf() {
        ScopeSet delegated = ScopeSet.of(List.of("wms", "scm"), List.of("R1", "R2"));
        assertThat(ScopeSet.of(List.of("wms"), List.of("R1")).isSubsetOf(delegated)).isTrue();
        assertThat(ScopeSet.of(List.of("wms", "finance"), List.of("R1")).isSubsetOf(delegated)).isFalse();
        assertThat(ScopeSet.of(List.of("wms"), List.of("R9")).isSubsetOf(delegated)).isFalse();
    }
}
