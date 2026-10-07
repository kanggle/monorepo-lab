package com.example.account.application.service;

import com.example.account.application.exception.OrgNodeNotFoundException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.exception.TenantOrgNodeConflictException;
import com.example.account.application.result.TenantPlacementResult;
import com.example.account.domain.orgnode.EntitlementCeiling;
import com.example.account.domain.orgnode.OrgNode;
import com.example.account.domain.orgnode.OrgNodeId;
import com.example.account.domain.repository.OrgNodeRepository;
import com.example.account.domain.repository.TenantDomainSubscriptionRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.SubscriptionStatus;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantDomainSubscription;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-625 — the authority side of a tenant placement: existence, the compare-then-write
 * on the authorized source, idempotence, and the P2 effect (ACTIVE ∩ ceiling, before/after).
 * Authorization is NOT here — it is admin-service's (see its TenantOrgNodePlacementUseCaseTest).
 */
@ExtendWith(MockitoExtension.class)
class TenantOrgNodePlacementUseCaseTest {

    @Mock TenantRepository tenantRepository;
    @Mock OrgNodeRepository orgNodeRepository;
    @Mock TenantDomainSubscriptionRepository subscriptionRepository;
    @Mock OrgNodeQueryUseCase orgNodeQueryUseCase;

    @InjectMocks TenantOrgNodePlacementUseCase useCase;

    private static final String T = "acme-wms";
    private static final OrgNodeId S = new OrgNodeId("node-s");
    private static final OrgNodeId D = new OrgNodeId("node-d");

    private static Tenant tenantAt(OrgNodeId node) {
        return Tenant.reconstitute(new TenantId(T), "Acme WMS", TenantType.B2B_ENTERPRISE,
                TenantStatus.ACTIVE, node, Instant.EPOCH, Instant.EPOCH);
    }

    private static OrgNode node(OrgNodeId id) {
        return OrgNode.create(id, null, "n", EntitlementCeiling.unbounded(), 1, Instant.EPOCH);
    }

    private void activeSubscriptions(String... domains) {
        when(subscriptionRepository.findActiveByTenantId(T)).thenReturn(
                java.util.Arrays.stream(domains)
                        .map(d -> TenantDomainSubscription.reconstitute(new TenantId(T), d,
                                SubscriptionStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH))
                        .toList());
    }

    @Test
    @DisplayName("move S → D: assignOrgNode + save, changed=true, effect computed against the ceilings")
    void move() {
        when(tenantRepository.findByIdForUpdate(new TenantId(T))).thenReturn(Optional.of(tenantAt(S)));
        when(orgNodeRepository.findById(D)).thenReturn(Optional.of(node(D)));
        activeSubscriptions("finance", "wms");
        when(orgNodeQueryUseCase.effectiveCeiling(S)).thenReturn(EntitlementCeiling.unbounded());
        when(orgNodeQueryUseCase.effectiveCeiling(D)).thenReturn(EntitlementCeiling.bounded(List.of("wms")));

        TenantPlacementResult r = useCase.place(T, "node-d", "node-s");

        assertThat(r.changed()).isTrue();
        assertThat(r.fromOrgNodeId()).isEqualTo("node-s");
        assertThat(r.toOrgNodeId()).isEqualTo("node-d");
        assertThat(r.domainsBefore()).containsExactly("finance", "wms");
        assertThat(r.domainsAfter()).containsExactly("wms");
        assertThat(r.lostDomains()).containsExactly("finance");
        assertThat(r.gainedDomains()).isEmpty();

        ArgumentCaptor<Tenant> saved = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(saved.capture());
        assertThat(saved.getValue().getOrgNodeId()).isEqualTo(D);
    }

    @Test
    @DisplayName("AC-4 (authority): attach under BOUNDED{wms} loses finance; detach restores it — subscription rows only read")
    void attachThenDetach_ceilingNarrowsThenRestores() {
        // attach: ungrouped → D
        when(tenantRepository.findByIdForUpdate(new TenantId(T)))
                .thenReturn(Optional.of(tenantAt(null)), Optional.of(tenantAt(D)));
        when(orgNodeRepository.findById(D)).thenReturn(Optional.of(node(D)));
        activeSubscriptions("finance", "wms");
        when(orgNodeQueryUseCase.effectiveCeiling(D)).thenReturn(EntitlementCeiling.bounded(List.of("wms")));

        TenantPlacementResult attach = useCase.place(T, "node-d", null);
        assertThat(attach.domainsBefore()).containsExactly("finance", "wms");
        assertThat(attach.domainsAfter()).containsExactly("wms");

        // detach: D → ungrouped
        TenantPlacementResult detach = useCase.place(T, null, "node-d");
        assertThat(detach.domainsBefore()).containsExactly("wms");
        assertThat(detach.domainsAfter()).containsExactly("finance", "wms");
        assertThat(detach.gainedDomains()).containsExactly("finance");

        // Deny-only ceiling: the subscription rows were READ, never written.
        verify(subscriptionRepository, org.mockito.Mockito.times(2)).findActiveByTenantId(T);
        verifyNoMoreInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("tenant no longer at the authorized source → 409, nothing written")
    void sourceMismatch_conflict() {
        when(tenantRepository.findByIdForUpdate(new TenantId(T))).thenReturn(Optional.of(tenantAt(D)));

        assertThatThrownBy(() -> useCase.place(T, "node-d", "node-s"))
                .isInstanceOf(TenantOrgNodeConflictException.class);
        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("an ungrouped tenant with expected=S (stale check) → 409 too: null is a real position, not a wildcard")
    void ungroupedButExpectedGrouped_conflict() {
        when(tenantRepository.findByIdForUpdate(new TenantId(T))).thenReturn(Optional.of(tenantAt(null)));

        assertThatThrownBy(() -> useCase.place(T, "node-d", "node-s"))
                .isInstanceOf(TenantOrgNodeConflictException.class);
        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("target node gone (deleted concurrently) → 404 ORG_NODE_NOT_FOUND, nothing written")
    void targetMissing_404() {
        when(tenantRepository.findByIdForUpdate(new TenantId(T))).thenReturn(Optional.of(tenantAt(S)));
        when(orgNodeRepository.findById(D)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.place(T, "node-d", "node-s"))
                .isInstanceOf(OrgNodeNotFoundException.class);
        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("tenant missing → 404 TENANT_NOT_FOUND")
    void tenantMissing_404() {
        when(tenantRepository.findByIdForUpdate(new TenantId(T))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.place(T, "node-d", null))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    @DisplayName("AC-6: target = current → no write, changed=false (idempotent)")
    void sameTarget_noOp() {
        when(tenantRepository.findByIdForUpdate(new TenantId(T))).thenReturn(Optional.of(tenantAt(D)));
        when(orgNodeRepository.findById(D)).thenReturn(Optional.of(node(D)));
        activeSubscriptions("wms");
        when(orgNodeQueryUseCase.effectiveCeiling(D)).thenReturn(EntitlementCeiling.bounded(List.of("wms")));

        TenantPlacementResult r = useCase.place(T, "node-d", "node-d");

        assertThat(r.changed()).isFalse();
        assertThat(r.lostDomains()).isEmpty();
        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("preview never writes and never locks")
    void preview_readOnly() {
        when(tenantRepository.findById(new TenantId(T))).thenReturn(Optional.of(tenantAt(S)));
        activeSubscriptions("finance", "wms");
        when(orgNodeQueryUseCase.effectiveCeiling(S)).thenReturn(EntitlementCeiling.bounded(List.of("finance")));

        TenantPlacementResult r = useCase.preview(T, null);

        assertThat(r.domainsBefore()).containsExactly("finance");
        assertThat(r.domainsAfter()).containsExactly("finance", "wms");
        assertThat(r.changed()).isFalse();
        verify(tenantRepository, never()).findByIdForUpdate(any());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("currentOrgNodeId: null for an ungrouped tenant, 404 for a missing one")
    void current() {
        when(tenantRepository.findById(new TenantId(T))).thenReturn(Optional.of(tenantAt(null)));
        assertThat(useCase.currentOrgNodeId(T)).isNull();

        when(tenantRepository.findById(new TenantId("ghost-tenant"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.currentOrgNodeId("ghost-tenant"))
                .isInstanceOf(TenantNotFoundException.class);
    }
}
