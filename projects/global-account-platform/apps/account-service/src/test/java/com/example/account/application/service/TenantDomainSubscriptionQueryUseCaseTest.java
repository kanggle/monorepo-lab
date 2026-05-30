package com.example.account.application.service;

import com.example.account.application.result.TenantDomainSubscriptionResult;
import com.example.account.domain.repository.TenantDomainSubscriptionRepository;
import com.example.account.domain.tenant.TenantDomainSubscription;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
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
                new TenantId(tenantId), domainKey, TenantStatus.ACTIVE,
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
}
