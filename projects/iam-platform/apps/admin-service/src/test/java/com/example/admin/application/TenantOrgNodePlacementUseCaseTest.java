package com.example.admin.application;

import com.example.admin.application.exception.OrgNodeNotFoundException;
import com.example.admin.application.exception.TenantNotFoundException;
import com.example.admin.application.orgnode.CeilingView;
import com.example.admin.application.orgnode.OrgNodeView;
import com.example.admin.application.orgnode.TenantPlacementView;
import com.example.admin.application.port.OrgNodePort;
import com.example.admin.application.port.TenantPlacementPort;
import com.example.admin.application.tenant.CreateTenantUseCase;
import com.example.admin.application.tenant.TenantSummary;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import com.example.admin.infrastructure.persistence.rbac.OrgNodeSubtreeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07, «양쪽을 다 관리하는 사람만») — the two-sided check
 * on a tenant placement, driven through the use-case with the REAL {@link OrgNodeScopeGuard}
 * (only its inputs — the grant evaluator, the auditor and the authority ports — are mocked).
 *
 * <p>Tree used throughout (one company, three divisions):
 * <pre>
 *   holding ── wms-div   (S)   ← tenant "acme-wms" lives here
 *          ├── erp-div   (D)   ← the destination in most tests
 *          └── other-div (G)   ← tenant "other-svc" lives here, outside D's admin
 * </pre>
 *
 * <p>Strict stubs: every stub is used, so a test that stops consulting a predicate fails loudly.
 */
@ExtendWith(MockitoExtension.class)
class TenantOrgNodePlacementUseCaseTest {

    @Mock TenantPlacementPort placementPort;
    @Mock OrgNodePort orgNodePort;
    @Mock AdminGrantScopeEvaluator grantScopeEvaluator;
    @Mock AdminActionAuditor auditor;
    @Mock OrgNodeSubtreeResolver subtreeResolver;
    @Mock CreateTenantUseCase createTenantUseCase;

    private TenantOrgNodePlacementUseCase useCase;

    private static final String S = "wms-div";
    private static final String D = "erp-div";
    private static final String G = "other-div";

    /** ORG_ADMIN @ erp-div only — the "destination admin". */
    private static final OperatorContext DEST_ADMIN = new OperatorContext("op-dest", "j1");
    /** ORG_ADMIN @ holding — administers S, D and G. */
    private static final OperatorContext HOLDING_ADMIN = new OperatorContext("op-holding", "j2");
    /** ORG_ADMIN @ wms-div — the source side of acme-wms. */
    private static final OperatorContext SOURCE_ADMIN = new OperatorContext("op-source", "j3");
    /** TENANT_ADMIN @ the tenant AND ORG_ADMIN @ erp-div (rider P1's intended actor). */
    private static final OperatorContext OWNER_AND_DEST = new OperatorContext("op-owner-dest", "j4");
    /** TENANT_ADMIN @ the tenant AND ORG_ADMIN @ other-div (does NOT administer D). */
    private static final OperatorContext OWNER_ELSEWHERE = new OperatorContext("op-owner-else", "j5");
    private static final OperatorContext SUPER = new OperatorContext("op-super", "j6");

    @BeforeEach
    void setUp() {
        OrgNodeScopeGuard guard = new OrgNodeScopeGuard(grantScopeEvaluator, auditor);
        useCase = new TenantOrgNodePlacementUseCase(placementPort, orgNodePort, guard,
                subtreeResolver, auditor, createTenantUseCase);
    }

    private static OrgNodeView node(String id, String parentId) {
        return new OrgNodeView(id, parentId, "n-" + id, parentId == null ? 1 : 2,
                CeilingView.unbounded(), Instant.EPOCH, Instant.EPOCH);
    }

    private void tree() {
        when(orgNodePort.list()).thenReturn(List.of(
                node("holding", null), node(S, "holding"), node(D, "holding"), node(G, "holding")));
    }

    private void orgAdminAt(OperatorContext actor, String... nodeIds) {
        when(grantScopeEvaluator.isPlatformScope(actor.operatorId(), Permission.ORG_MANAGE)).thenReturn(false);
        when(grantScopeEvaluator.grantedOrgNodeIds(actor.operatorId(), Permission.ORG_MANAGE))
                .thenReturn(Set.of(nodeIds));
    }

    private void platform(OperatorContext actor) {
        when(grantScopeEvaluator.isPlatformScope(actor.operatorId(), Permission.ORG_MANAGE)).thenReturn(true);
        when(grantScopeEvaluator.grantedOrgNodeIds(actor.operatorId(), Permission.ORG_MANAGE)).thenReturn(Set.of());
    }

    private static TenantPlacementView effect(String tenantId, String from, String to, boolean changed) {
        return new TenantPlacementView(tenantId, from, to,
                List.of("finance", "wms"), List.of("wms"), List.of("finance"), List.of(), changed);
    }

    // ── AC-1 — isolation control group: the FAILING side first ─────────────────────

    @Nested
    @DisplayName("AC-1 — a destination admin cannot pull in a tenant it does not administer")
    class PullIn {

        @Test
        @DisplayName("ORG_ADMIN @ D pulling other-svc (under G) into D → 404 TENANT_NOT_FOUND, no write, DENIED row; "
                + "the holding admin (administers both) moving the same tenant → succeeds")
        void destinationOnlyAdminIsRefused_bothSidesAdminSucceeds() {
            tree();
            when(placementPort.currentOrgNodeId("other-svc")).thenReturn(G);

            // ── failing side: destination-only admin ──
            orgAdminAt(DEST_ADMIN, D);
            assertThatThrownBy(() -> useCase.place(DEST_ADMIN, "other-svc", D, "pull it in"))
                    .isInstanceOf(TenantNotFoundException.class);
            verify(placementPort, never()).place(anyString(), any(), any());
            verify(auditor).recordTenantPlacementDenied(DEST_ADMIN, ActionCode.TENANT_ORG_NODE_ASSIGN,
                    "other-svc", OrgNodeScopeGuard.SIDE_SOURCE, G, D);
            verify(auditor, never()).record(any());

            // ── control: the actor that administers BOTH sides ──
            orgAdminAt(HOLDING_ADMIN, "holding");
            when(placementPort.place("other-svc", D, G)).thenReturn(effect("other-svc", G, D, true));
            when(auditor.newAuditId()).thenReturn("audit-1");

            TenantPlacementView result = useCase.place(HOLDING_ADMIN, "other-svc", D, "reorg");

            assertThat(result.changed()).isTrue();
            // The write is conditional on the source that was authorized.
            verify(placementPort).place("other-svc", D, G);
            verify(subtreeResolver).invalidateAll();
        }

        @Test
        @DisplayName("an UNGROUPED tenant outside the actor's operator.manage scope → 404, no write")
        void ungroupedTenantNotOwned_isRefused() {
            tree();
            when(placementPort.currentOrgNodeId("loose-svc")).thenReturn(null);
            orgAdminAt(DEST_ADMIN, D);
            when(grantScopeEvaluator.isTenantInAdminScope("op-dest", Permission.OPERATOR_MANAGE, "loose-svc"))
                    .thenReturn(false);

            assertThatThrownBy(() -> useCase.place(DEST_ADMIN, "loose-svc", D, "grab"))
                    .isInstanceOf(TenantNotFoundException.class);

            verify(placementPort, never()).place(anyString(), any(), any());
            verify(auditor).recordTenantPlacementDenied(DEST_ADMIN, ActionCode.TENANT_ORG_NODE_ASSIGN,
                    "loose-svc", OrgNodeScopeGuard.SIDE_SOURCE, null, D);
        }

        @Test
        @DisplayName("for an ungrouped tenant the subtree cache is dropped BEFORE the P1 source predicate runs")
        void ungroupedSource_dropsSubtreeCacheFirst() {
            tree();
            when(placementPort.currentOrgNodeId("loose-svc")).thenReturn(null);
            orgAdminAt(DEST_ADMIN, D);
            when(grantScopeEvaluator.isTenantInAdminScope("op-dest", Permission.OPERATOR_MANAGE, "loose-svc"))
                    .thenReturn(false);

            assertThatThrownBy(() -> useCase.place(DEST_ADMIN, "loose-svc", D, "grab"))
                    .isInstanceOf(TenantNotFoundException.class);

            // A tenant detached seconds ago may still sit in a cached ORG_ADMIN subtree; the P1
            // check must not see it there.
            InOrder order = inOrder(subtreeResolver, grantScopeEvaluator);
            order.verify(subtreeResolver).invalidateAll();
            order.verify(grantScopeEvaluator)
                    .isTenantInAdminScope("op-dest", Permission.OPERATOR_MANAGE, "loose-svc");
        }
    }

    // ── AC-2 — the tenant owner cannot take its tenant out of a node alone ──────────

    @Nested
    @DisplayName("AC-2 — detaching a tenant that sits under S")
    class Detach {

        @Test
        @DisplayName("TENANT_ADMIN @ T (even with org.manage elsewhere) → 404, no write; TENANT_ADMIN is NOT consulted for a grouped T")
        void tenantOwnerCannotEscapeCeiling() {
            tree();
            when(placementPort.currentOrgNodeId("acme-wms")).thenReturn(S);
            orgAdminAt(OWNER_AND_DEST, D);

            assertThatThrownBy(() -> useCase.place(OWNER_AND_DEST, "acme-wms", null, "leave the node"))
                    .isInstanceOf(TenantNotFoundException.class);

            verify(placementPort, never()).place(anyString(), any(), any());
            // For a grouped tenant the source side is S's administrators, full stop — the
            // tenant-scope predicate (where TENANT_ADMIN would count) is never asked.
            verify(grantScopeEvaluator, never()).isTenantInAdminScope(anyString(), anyString(), anyString());
            verify(auditor).recordTenantPlacementDenied(OWNER_AND_DEST, ActionCode.TENANT_ORG_NODE_ASSIGN,
                    "acme-wms", OrgNodeScopeGuard.SIDE_SOURCE, S, null);
        }

        @Test
        @DisplayName("ORG_ADMIN @ S detaches → succeeds (no destination check for 'ungrouped')")
        void sourceAdminDetaches() {
            tree();
            when(placementPort.currentOrgNodeId("acme-wms")).thenReturn(S);
            orgAdminAt(SOURCE_ADMIN, S);
            when(placementPort.place("acme-wms", null, S)).thenReturn(effect("acme-wms", S, null, true));
            when(auditor.newAuditId()).thenReturn("audit-2");

            TenantPlacementView result = useCase.place(SOURCE_ADMIN, "acme-wms", null, "spin off");

            assertThat(result.toOrgNodeId()).isNull();
            verify(placementPort).place("acme-wms", null, S);
        }
    }

    // ── AC-3 — rider P1, both directions ───────────────────────────────────────────

    @Nested
    @DisplayName("AC-3 — rider P1: an ungrouped tenant's TENANT_ADMIN counts as the source side")
    class RiderP1 {

        @Test
        @DisplayName("TENANT_ADMIN @ T + ORG_ADMIN @ D → attaches T under D")
        void ownerWhoAlsoAdministersDestination_succeeds() {
            tree();
            when(placementPort.currentOrgNodeId("new-svc")).thenReturn(null);
            orgAdminAt(OWNER_AND_DEST, D);
            when(grantScopeEvaluator.isTenantInAdminScope("op-owner-dest", Permission.OPERATOR_MANAGE, "new-svc"))
                    .thenReturn(true);
            when(placementPort.place("new-svc", D, null)).thenReturn(effect("new-svc", null, D, true));
            when(auditor.newAuditId()).thenReturn("audit-3");

            useCase.place(OWNER_AND_DEST, "new-svc", D, "join the company");

            verify(placementPort).place("new-svc", D, null);
        }

        @Test
        @DisplayName("TENANT_ADMIN @ T but NOT an administrator of D → 404 ORG_NODE_NOT_FOUND, no write, DENIED(DESTINATION)")
        void ownerAloneCannotChooseAForeignNode() {
            tree();
            when(placementPort.currentOrgNodeId("new-svc")).thenReturn(null);
            orgAdminAt(OWNER_ELSEWHERE, G);
            when(grantScopeEvaluator.isTenantInAdminScope("op-owner-else", Permission.OPERATOR_MANAGE, "new-svc"))
                    .thenReturn(true);

            assertThatThrownBy(() -> useCase.place(OWNER_ELSEWHERE, "new-svc", D, "join"))
                    .isInstanceOf(OrgNodeNotFoundException.class);

            verify(placementPort, never()).place(anyString(), any(), any());
            verify(auditor).recordTenantPlacementDenied(OWNER_ELSEWHERE, ActionCode.TENANT_ORG_NODE_ASSIGN,
                    "new-svc", OrgNodeScopeGuard.SIDE_DESTINATION, null, D);
        }
    }

    // ── AC-6 — idempotence and the audit row ───────────────────────────────────────

    @Nested
    @DisplayName("AC-6 — idempotent no-op, audit row content")
    class IdempotenceAndAudit {

        @Test
        @DisplayName("a move writes one SUCCESS row: TENANT target, from/to nodes, the operator's reason")
        void successRowCarriesFromToAndReason() {
            tree();
            when(placementPort.currentOrgNodeId("acme-wms")).thenReturn(S);
            orgAdminAt(HOLDING_ADMIN, "holding");
            when(placementPort.place("acme-wms", D, S)).thenReturn(effect("acme-wms", S, D, true));
            when(auditor.newAuditId()).thenReturn("audit-4");

            useCase.place(HOLDING_ADMIN, "acme-wms", D, "erp 사업부로 이관");

            ArgumentCaptor<AdminActionAuditor.AuditRecord> row = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
            verify(auditor).record(row.capture());
            assertThat(row.getValue().actionCode()).isEqualTo(ActionCode.TENANT_ORG_NODE_ASSIGN);
            assertThat(row.getValue().targetType()).isEqualTo("TENANT");
            assertThat(row.getValue().targetId()).isEqualTo("acme-wms");
            assertThat(row.getValue().targetTenantId()).isEqualTo("acme-wms");
            assertThat(row.getValue().outcome()).isEqualTo(Outcome.SUCCESS);
            assertThat(row.getValue().reason()).isEqualTo(AuditReasons.normalize("erp 사업부로 이관"));
            assertThat(row.getValue().downstreamDetail())
                    .isEqualTo("from_org_node_id=" + S + " to_org_node_id=" + D + " changed=true");
        }

        @Test
        @DisplayName("same target as now → the authority reports changed=false; the row says so; still conditional on the source")
        void repeatIsANoOp() {
            tree();
            when(placementPort.currentOrgNodeId("acme-wms")).thenReturn(D);
            orgAdminAt(HOLDING_ADMIN, "holding");
            when(placementPort.place("acme-wms", D, D)).thenReturn(effect("acme-wms", D, D, false));
            when(auditor.newAuditId()).thenReturn("audit-5");

            TenantPlacementView result = useCase.place(HOLDING_ADMIN, "acme-wms", D, "again");

            assertThat(result.changed()).isFalse();
            ArgumentCaptor<AdminActionAuditor.AuditRecord> row = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
            verify(auditor).record(row.capture());
            assertThat(row.getValue().downstreamDetail()).endsWith("changed=false");
        }

        @Test
        @DisplayName("a no-op does NOT skip authorization — otherwise a 200 would reveal 'T is already under D'")
        void noOpStillAuthorizes() {
            tree();
            when(placementPort.currentOrgNodeId("acme-wms")).thenReturn(D);
            orgAdminAt(SOURCE_ADMIN, S);   // administers S, not D (where T actually is)

            assertThatThrownBy(() -> useCase.place(SOURCE_ADMIN, "acme-wms", D, "probe"))
                    .isInstanceOf(TenantNotFoundException.class);
            verify(placementPort, never()).place(anyString(), any(), any());
        }
    }

    // ── Edge cases ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("edges")
    class Edges {

        @Test
        @DisplayName("SUPER_ADMIN administers everything that exists (net-zero) — and still 404s on an unknown node")
        void superAdmin() {
            tree();
            when(placementPort.currentOrgNodeId("other-svc")).thenReturn(G);
            platform(SUPER);
            when(placementPort.place("other-svc", S, G)).thenReturn(effect("other-svc", G, S, true));
            when(auditor.newAuditId()).thenReturn("audit-6");

            useCase.place(SUPER, "other-svc", S, "platform move");
            verify(placementPort).place("other-svc", S, G);

            assertThatThrownBy(() -> useCase.place(SUPER, "other-svc", "ghost-node", "typo"))
                    .isInstanceOf(OrgNodeNotFoundException.class);
        }

        @Test
        @DisplayName("preview runs the same two-sided check and never writes")
        void previewIsCheckedAndReadOnly() {
            tree();
            when(placementPort.currentOrgNodeId("other-svc")).thenReturn(G);
            orgAdminAt(DEST_ADMIN, D);

            assertThatThrownBy(() -> useCase.preview(DEST_ADMIN, "other-svc", D))
                    .isInstanceOf(TenantNotFoundException.class);
            verify(placementPort, never()).preview(anyString(), any());
            verify(placementPort, never()).place(anyString(), any(), any());
        }

        @Test
        @DisplayName("preview by an actor administering both sides returns the authority's effect, no audit row")
        void previewReturnsEffect() {
            tree();
            when(placementPort.currentOrgNodeId("acme-wms")).thenReturn(S);
            orgAdminAt(HOLDING_ADMIN, "holding");
            TenantPlacementView fx = effect("acme-wms", S, D, false);
            when(placementPort.preview("acme-wms", D)).thenReturn(fx);

            assertThat(useCase.preview(HOLDING_ADMIN, "acme-wms", D).lostDomains()).containsExactly("finance");
            verify(auditor, never()).record(any());
        }
    }

    // ── AC-5 — tenant creation with an optional orgNodeId ─────────────────────────

    @Nested
    @DisplayName("AC-5 — create under a node")
    class CreateUnder {

        @Test
        @DisplayName("SUPER_ADMIN creates T under D: created first, then placed from 'ungrouped', then audited")
        void createsThenPlaces() {
            tree();
            platform(SUPER);
            when(grantScopeEvaluator.isTenantInAdminScope("op-super", Permission.OPERATOR_MANAGE, "brand-new"))
                    .thenReturn(true);
            TenantSummary created = new TenantSummary("brand-new", "Brand New", "B2B_ENTERPRISE", "ACTIVE",
                    Instant.EPOCH, Instant.EPOCH);
            when(createTenantUseCase.execute("brand-new", "Brand New", "B2B_ENTERPRISE", SUPER, "new", "idem-1"))
                    .thenReturn(created);
            when(placementPort.place("brand-new", D, null)).thenReturn(effect("brand-new", null, D, true));
            when(auditor.newAuditId()).thenReturn("audit-7");

            TenantSummary result = useCase.createTenantUnder(SUPER, "brand-new", "Brand New", "B2B_ENTERPRISE",
                    D, "new", "idem-1");

            assertThat(result).isEqualTo(created);
            InOrder order = inOrder(createTenantUseCase, placementPort, auditor);
            order.verify(createTenantUseCase).execute("brand-new", "Brand New", "B2B_ENTERPRISE", SUPER, "new", "idem-1");
            order.verify(placementPort).place("brand-new", D, null);
            order.verify(auditor).record(any());
        }

        @Test
        @DisplayName("an unknown/out-of-reach node is refused BEFORE creation — nothing is created")
        void unknownNodeCreatesNothing() {
            tree();
            platform(SUPER);
            when(grantScopeEvaluator.isTenantInAdminScope("op-super", Permission.OPERATOR_MANAGE, "brand-new"))
                    .thenReturn(true);

            assertThatThrownBy(() -> useCase.createTenantUnder(SUPER, "brand-new", "Brand New", "B2B_ENTERPRISE",
                    "ghost-node", "new", "idem-2"))
                    .isInstanceOf(OrgNodeNotFoundException.class);

            verify(createTenantUseCase, never()).execute(anyString(), anyString(), anyString(), any(), any(), any());
            verify(placementPort, never()).place(anyString(), any(), any());
            verify(auditor).recordTenantPlacementDenied(eq(SUPER), eq(ActionCode.TENANT_ORG_NODE_ASSIGN),
                    eq("brand-new"), eq(OrgNodeScopeGuard.SIDE_DESTINATION), eq(null), eq("ghost-node"));
        }
    }
}
