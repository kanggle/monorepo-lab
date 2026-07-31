package com.example.erp.approval.infrastructure.persistence.jpa;

import com.example.common.page.PageResult;
import com.example.erp.approval.domain.request.ApprovalRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * ADR-MONO-058 § D3 — AC-2: proves {@code totalPages} ceiling-division correctness at the real
 * {@code com.example.common.page.PageResult} construction site inside {@link
 * ApprovalRequestRepositoryImpl#findAll}, mirroring masterdata-service's equivalent IT-level
 * evidence (there {@code MasterdataListFilterIntegrationTest} is Docker-gated; this is a pure
 * Mockito unit test — no Testcontainers — so it runs in {@code ./gradlew :approval-service:test}
 * without Docker).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApprovalRequestRepositoryImplTest {

    @Mock ApprovalRequestJpaRepository requestJpa;
    @Mock ApprovalActionJpaRepository actionJpa;
    @Mock ApprovalRouteStageJpaRepository routeStageJpa;

    private ApprovalRequestRepositoryImpl repository;

    @Test
    @DisplayName("AC-2: totalPages is a ceiling division over a non-exact multiple (25/10 -> 3)")
    void totalPagesIsCeilingDivisionForNonExactMultiple() {
        repository = new ApprovalRequestRepositoryImpl(requestJpa, actionJpa, routeStageJpa);
        when(requestJpa.findAllByTenantId(eq("erp"), any(PageRequest.class)))
                .thenReturn(List.<ApprovalRequest>of());
        when(requestJpa.countByTenantId("erp")).thenReturn(25L);

        PageResult<ApprovalRequest> page = repository.findAll("erp", null, 0, 10);

        assertThat(page.totalElements()).isEqualTo(25L);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("AC-2 edge case: zero elements -> totalPages 0, not 1")
    void totalPagesIsZeroForEmptyResult() {
        repository = new ApprovalRequestRepositoryImpl(requestJpa, actionJpa, routeStageJpa);
        when(requestJpa.findAllByTenantId(eq("erp"), any(PageRequest.class)))
                .thenReturn(List.<ApprovalRequest>of());
        when(requestJpa.countByTenantId("erp")).thenReturn(0L);

        PageResult<ApprovalRequest> page = repository.findAll("erp", null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(0L);
        assertThat(page.totalPages()).isEqualTo(0);
    }

    @Test
    @DisplayName("findInbox: totalPages ceiling division at the same construction pattern")
    void findInboxTotalPagesIsCeilingDivision() {
        repository = new ApprovalRequestRepositoryImpl(requestJpa, actionJpa, routeStageJpa);
        when(requestJpa.findInboxPending(eq("erp"), eq("emp-1"), any(PageRequest.class)))
                .thenReturn(List.<ApprovalRequest>of());
        when(requestJpa.countInboxPending("erp", "emp-1")).thenReturn(7L);

        PageResult<ApprovalRequest> page = repository.findInbox("erp", "emp-1", 0, 5);

        // 7 elements / 5 per page -> 2 pages.
        assertThat(page.totalPages()).isEqualTo(2);
    }
}
