package com.example.admin.application;

import com.example.admin.application.exception.OrgNodeNotFoundException;
import com.example.admin.application.exception.OrgNodeSelfCeilingDeniedException;
import com.example.admin.application.exception.PermissionDeniedException;
import com.example.admin.application.orgnode.CeilingView;
import com.example.admin.application.orgnode.OrgNodeView;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-492 / ADR-MONO-047 D5 — the org-node reach predicates.
 *
 * <p>Tree used throughout:
 * <pre>
 *   root ── biz ── wms
 *            └──── erp
 *   other (a second root, outside every grant)
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrgNodeScopeGuardTest {

    @Mock AdminGrantScopeEvaluator grantScopeEvaluator;
    @Mock AdminActionAuditor auditor;

    @InjectMocks OrgNodeScopeGuard guard;

    private static final OperatorContext ACTOR = new OperatorContext("op-1", "jti-1");

    private static final List<OrgNodeView> TREE = List.of(
            node("root", null), node("biz", "root"), node("wms", "biz"), node("erp", "biz"),
            node("other", null));

    private static OrgNodeView node(String id, String parentId) {
        return new OrgNodeView(id, parentId, "n-" + id, 1, CeilingView.unbounded(),
                Instant.EPOCH, Instant.EPOCH);
    }

    private OrgNodeScopeGuard.Reach reachFor(boolean platform, Set<String> grantNodeIds) {
        when(grantScopeEvaluator.isPlatformScope("op-1", Permission.ORG_MANAGE)).thenReturn(platform);
        when(grantScopeEvaluator.grantedOrgNodeIds("op-1", Permission.ORG_MANAGE)).thenReturn(grantNodeIds);
        return guard.resolveReach(ACTOR, TREE);
    }

    @Nested
    @DisplayName("administers(actor, N) — ancestors(N) ∪ {N}")
    class Administers {

        @Test
        @DisplayName("ORG_ADMIN @ biz administers biz and its descendants, but not root nor a sibling root")
        void nodeAdmin_reachesOwnSubtreeOnly() {
            OrgNodeScopeGuard.Reach reach = reachFor(false, Set.of("biz"));

            assertThat(reach.administers("biz")).isTrue();
            assertThat(reach.administers("wms")).isTrue();
            assertThat(reach.administers("erp")).isTrue();
            assertThat(reach.administers("root")).isFalse();   // upward is not reach
            assertThat(reach.administers("other")).isFalse();  // a disjoint tree
        }

        @Test
        @DisplayName("SUPER_ADMIN administers every EXISTING node — and still 404s on an unknown id")
        void platformActor_reachesEverythingThatExists() {
            OrgNodeScopeGuard.Reach reach = reachFor(true, Set.of());

            assertThat(reach.administers("root")).isTrue();
            assertThat(reach.administers("other")).isTrue();
            assertThat(reach.administers("ghost")).isFalse();
        }

        @Test
        @DisplayName("no grant at all → administers nothing")
        void noGrant_reachesNothing() {
            OrgNodeScopeGuard.Reach reach = reachFor(false, Set.of());
            assertThat(reach.administers("root")).isFalse();
            assertThat(reach.administers("wms")).isFalse();
        }
    }

    @Nested
    @DisplayName("strictlyAdministers(actor, N) — STRICT ancestors only")
    class StrictlyAdministers {

        @Test
        @DisplayName("ORG_ADMIN @ biz may NOT alter biz itself, but may alter its children")
        void nodeAdmin_cannotAlterOwnNode() {
            OrgNodeScopeGuard.Reach reach = reachFor(false, Set.of("biz"));

            assertThat(reach.strictlyAdministers("biz")).isFalse();  // ← the self-escalation bar
            assertThat(reach.strictlyAdministers("wms")).isTrue();
            assertThat(reach.strictlyAdministers("erp")).isTrue();
        }

        @Test
        @DisplayName("ORG_ADMIN @ root may alter biz (a strict descendant)")
        void ancestorAdmin_mayAlterDescendant() {
            OrgNodeScopeGuard.Reach reach = reachFor(false, Set.of("root"));
            assertThat(reach.strictlyAdministers("biz")).isTrue();
            assertThat(reach.strictlyAdministers("root")).isFalse();
        }

        @Test
        @DisplayName("SUPER_ADMIN strictly administers every node, including a root")
        void platformActor_mayAlterAnyNode() {
            OrgNodeScopeGuard.Reach reach = reachFor(true, Set.of());
            assertThat(reach.strictlyAdministers("root")).isTrue();
        }
    }

    @Nested
    @DisplayName("guard outcomes")
    class GuardOutcomes {

        @Test
        @DisplayName("out-of-reach target → 404 ORG_NODE_NOT_FOUND (403 would leak its existence)")
        void outOfReach_is404() {
            OrgNodeScopeGuard.Reach reach = reachFor(false, Set.of("biz"));
            assertThatThrownBy(() ->
                    guard.requireAdministers(ACTOR, reach, "other", ActionCode.ORG_NODE_UPDATE))
                    .isInstanceOf(OrgNodeNotFoundException.class);
        }

        @Test
        @DisplayName("own-node ceiling edit → 403 ORG_NODE_SELF_CEILING_DENIED, not 404")
        void selfCeilingEdit_is403() {
            OrgNodeScopeGuard.Reach reach = reachFor(false, Set.of("biz"));
            // The actor demonstrably administers `biz`, so nothing is leaked by admitting it exists.
            assertThatThrownBy(() ->
                    guard.requireStrictlyAdministers(ACTOR, reach, "biz", ActionCode.ORG_NODE_CEILING_SET))
                    .isInstanceOf(OrgNodeSelfCeilingDeniedException.class);
        }

        @Test
        @DisplayName("strict guard on an out-of-reach node → still 404, never the 403 variant")
        void strictGuard_outOfReach_is404() {
            OrgNodeScopeGuard.Reach reach = reachFor(false, Set.of("biz"));
            assertThatThrownBy(() ->
                    guard.requireStrictlyAdministers(ACTOR, reach, "other", ActionCode.ORG_NODE_CEILING_SET))
                    .isInstanceOf(OrgNodeNotFoundException.class);
        }

        @Test
        @DisplayName("ROOT creation by a non-platform actor → 403 PERMISSION_DENIED")
        void rootCreation_isPlatformOnly() {
            OrgNodeScopeGuard.Reach reach = reachFor(false, Set.of("root"));
            assertThatThrownBy(() -> guard.requirePlatformForRoot(ACTOR, reach))
                    .isInstanceOf(PermissionDeniedException.class);

            OrgNodeScopeGuard.Reach platformReach = reachFor(true, Set.of());
            guard.requirePlatformForRoot(ACTOR, platformReach); // does not throw
        }
    }

    @Nested
    @DisplayName("visibleNodes()")
    class VisibleNodes {

        @Test
        @DisplayName("ORG_ADMIN @ biz sees exactly subtree(biz); ungrouped/other trees stay hidden")
        void nodeAdmin_seesOwnSubtree() {
            OrgNodeScopeGuard.Reach reach = reachFor(false, Set.of("biz"));
            assertThat(reach.visibleNodes()).extracting(OrgNodeView::orgNodeId)
                    .containsExactlyInAnyOrder("biz", "wms", "erp");
        }

        @Test
        @DisplayName("SUPER_ADMIN sees every node")
        void platformActor_seesAll() {
            OrgNodeScopeGuard.Reach reach = reachFor(true, Set.of());
            assertThat(reach.visibleNodes()).hasSize(TREE.size());
        }

        @Test
        @DisplayName("a grant naming a node that no longer exists contributes nothing (fail-closed)")
        void danglingGrant_contributesNothing() {
            OrgNodeScopeGuard.Reach reach = reachFor(false, Set.of("deleted-node"));
            assertThat(reach.visibleNodes()).isEmpty();
            assertThat(reach.administers("biz")).isFalse();
        }
    }
}
