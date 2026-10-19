package com.example.erp.masterdata.infrastructure.persistence.jpa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-ERP-BE-045 — {@link DepartmentRepositoryImpl#subtreeIds}: roots → roots ∪ every descendant, computed
 * from ONE tenant-scoped edge query (the picker resolves its scope once per request, not one ancestor walk per
 * employee). Containment must agree with the single-target check
 * ({@code RoleScopeAuthorizationAdapter.isWithinScopedSubtree}): a department is in iff it or an ancestor is a
 * root, a root that matches no department still counts as itself, and status is not consulted.
 *
 * <pre>
 *   A ─ A1 ─ A11          B ─ B1          C ⇄ D  (corrupt cycle — must terminate)
 *     └ A2
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class DepartmentSubtreeIdsTest {

    private static final String TENANT = "demo-corp";

    @Mock
    DepartmentJpaRepository jpa;

    private DepartmentRepositoryImpl repo;

    private static Object[] edge(String id, String parentId) {
        return new Object[]{id, parentId};
    }

    @BeforeEach
    void setUp() {
        repo = new DepartmentRepositoryImpl(jpa);
    }

    private void stubForest() {
        List<Object[]> edges = new ArrayList<>();
        edges.add(edge("A", null));
        edges.add(edge("A1", "A"));
        edges.add(edge("A11", "A1"));
        edges.add(edge("A2", "A"));
        edges.add(edge("B", null));
        edges.add(edge("B1", "B"));
        edges.add(edge("C", "D"));
        edges.add(edge("D", "C"));
        when(jpa.findHierarchyEdges(TENANT)).thenReturn(edges);
    }

    @Test
    @DisplayName("🔴 BE-045: root A → A and all its descendants; the sibling tree B is excluded")
    void rootExpandsToDescendants() {
        stubForest();
        assertThat(repo.subtreeIds(Set.of("A"), TENANT)).containsExactlyInAnyOrder("A", "A1", "A11", "A2");
    }

    @Test
    @DisplayName("BE-045: an inner root A1 → A1 + A11 only (no climbing up to A or across to A2)")
    void innerRoot() {
        stubForest();
        assertThat(repo.subtreeIds(Set.of("A1"), TENANT)).containsExactlyInAnyOrder("A1", "A11");
    }

    @Test
    @DisplayName("BE-045: several roots → their union, from a single edge query")
    void severalRootsOneQuery() {
        stubForest();
        assertThat(repo.subtreeIds(Set.of("A1", "B"), TENANT)).containsExactlyInAnyOrder("A1", "A11", "B", "B1");
        verify(jpa, times(1)).findHierarchyEdges(TENANT);
    }

    @Test
    @DisplayName("BE-045: a root matching no department still counts as itself (same as the single check's exact-match)")
    void unknownRootKeepsItself() {
        stubForest();
        assertThat(repo.subtreeIds(Set.of("X"), TENANT)).containsExactly("X");
    }

    @Test
    @DisplayName("BE-045: a corrupt parent cycle terminates (visited set)")
    void cycleTerminates() {
        stubForest();
        assertThat(repo.subtreeIds(Set.of("C"), TENANT)).containsExactlyInAnyOrder("C", "D");
    }

    @Test
    @DisplayName("BE-045: no roots → empty, no query")
    void noRoots() {
        assertThat(repo.subtreeIds(Set.of(), TENANT)).isEmpty();
        verifyNoInteractions(jpa);
    }
}
