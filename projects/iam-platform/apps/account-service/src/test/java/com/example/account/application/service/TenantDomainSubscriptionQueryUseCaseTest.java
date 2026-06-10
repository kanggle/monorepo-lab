package com.example.account.application.service;

import com.example.account.application.result.TenantDomainSubscriptionResult;
import com.example.account.domain.repository.TenantDomainSubscriptionRepository;
import com.example.account.domain.tenant.TenantDomainSubscription;
import com.example.account.domain.tenant.SubscriptionStatus;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-322 (ADR-MONO-019 D2): unit coverage for
 * {@link TenantDomainSubscriptionQueryUseCase} — the ACTIVE subscription read
 * surface the admin-service catalog projection consumes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class TenantDomainSubscriptionQueryUseCaseTest {

    @Mock
    private TenantDomainSubscriptionRepository repository;

    private TenantDomainSubscriptionQueryUseCase useCase() {
        return new TenantDomainSubscriptionQueryUseCase(repository);
    }

    private static TenantDomainSubscription sub(String tenantId, String domainKey) {
        return TenantDomainSubscription.reconstitute(
                new TenantId(tenantId), domainKey, SubscriptionStatus.ACTIVE,
                Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("no filter → all ACTIVE subscriptions mapped (backward-compat seed shape)")
    void listActive_noFilter_returnsAll() {
        when(repository.findAllActive()).thenReturn(List.of(
                sub("wms", "wms"), sub("scm", "scm"),
                sub("erp", "erp"), sub("finance", "finance")));

        List<TenantDomainSubscriptionResult> results = useCase().listActive(null);

        assertThat(results)
                .extracting(TenantDomainSubscriptionResult::tenantId,
                        TenantDomainSubscriptionResult::domainKey)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("wms", "wms"),
                        org.assertj.core.groups.Tuple.tuple("scm", "scm"),
                        org.assertj.core.groups.Tuple.tuple("erp", "erp"),
                        org.assertj.core.groups.Tuple.tuple("finance", "finance"));
    }

    @Test
    @DisplayName("domainKey filter → only matching subscriptions")
    void listActive_domainFilter_returnsMatching() {
        when(repository.findAllActive()).thenReturn(List.of(
                sub("acme", "wms"), sub("wms", "wms"), sub("scm", "scm")));

        List<TenantDomainSubscriptionResult> results = useCase().listActive("wms");

        assertThat(results)
                .extracting(TenantDomainSubscriptionResult::tenantId)
                .containsExactlyInAnyOrder("acme", "wms");
    }

    @Test
    @DisplayName("blank filter treated as no filter")
    void listActive_blankFilter_returnsAll() {
        when(repository.findAllActive()).thenReturn(List.of(sub("wms", "wms")));

        assertThat(useCase().listActive("   ")).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // TASK-BE-324 — tenantId reverse lookup (ADR-MONO-019 § 3.3 keystone)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("tenantId reverse lookup → only that tenant's ACTIVE subscriptions")
    void listActive_tenantIdReverseLookup_returnsTenantSubs() {
        when(repository.findActiveByTenantId("acme")).thenReturn(List.of(
                sub("acme", "finance"), sub("acme", "wms")));

        List<TenantDomainSubscriptionResult> results = useCase().listActive(null, "acme");

        assertThat(results)
                .extracting(TenantDomainSubscriptionResult::tenantId,
                        TenantDomainSubscriptionResult::domainKey)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("acme", "finance"),
                        org.assertj.core.groups.Tuple.tuple("acme", "wms"));
    }

    @Test
    @DisplayName("tenantId reverse lookup with domainKey → AND-composed")
    void listActive_tenantIdAndDomainKey_andComposed() {
        when(repository.findActiveByTenantId("acme")).thenReturn(List.of(
                sub("acme", "finance"), sub("acme", "wms")));

        List<TenantDomainSubscriptionResult> results = useCase().listActive("finance", "acme");

        assertThat(results)
                .extracting(TenantDomainSubscriptionResult::domainKey)
                .containsExactly("finance");
    }

    @Test
    @DisplayName("unknown tenantId → empty")
    void listActive_unknownTenantId_returnsEmpty() {
        when(repository.findActiveByTenantId("nope")).thenReturn(List.of());

        assertThat(useCase().listActive(null, "nope")).isEmpty();
    }

    @Test
    @DisplayName("null tenantId → falls back to findAllActive (existing behaviour)")
    void listActive_nullTenantId_usesFindAllActive() {
        when(repository.findAllActive()).thenReturn(List.of(sub("wms", "wms"), sub("scm", "scm")));

        List<TenantDomainSubscriptionResult> results = useCase().listActive(null, null);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("blank tenantId → falls back to findAllActive (existing behaviour)")
    void listActive_blankTenantId_usesFindAllActive() {
        when(repository.findAllActive()).thenReturn(List.of(sub("wms", "wms")));

        assertThat(useCase().listActive(null, "  ")).hasSize(1);
    }
}
