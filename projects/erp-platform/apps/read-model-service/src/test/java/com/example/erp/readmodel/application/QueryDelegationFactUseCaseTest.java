package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.query.DelegationFactPage;
import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.delegation.DelegationFactProjection;
import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;
import com.example.erp.readmodel.domain.delegation.repository.DelegationFactFilter;
import com.example.erp.readmodel.domain.delegation.repository.DelegationFactProjectionRepository;
import com.example.erp.readmodel.domain.error.ReadModelNotFoundException;
import com.example.erp.readmodel.domain.projection.EmployeeProjection;
import com.example.erp.readmodel.domain.projection.repository.DepartmentProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.EmployeeProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QueryDelegationFactUseCase}: the org_scope read filter
 * (delegator-subtree), the activeAt filter pass-through, net-zero on absent
 * scope, and the out-of-scope detail → 404 rule. STRICT_STUBS.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class QueryDelegationFactUseCaseTest {

    @Mock DelegationFactProjectionRepository delegationRepository;
    @Mock DepartmentProjectionRepository departmentRepository;
    @Mock EmployeeProjectionRepository employeeRepository;

    @InjectMocks QueryDelegationFactUseCase useCase;

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-30T00:00:00Z");
    private static final Instant ACTIVE_AT = Instant.parse("2026-06-15T00:00:00Z");

    @BeforeEach
    void setDepthBound() {
        ReflectionTestUtils.setField(useCase, "departmentPathMaxDepth", 32);
    }

    private DelegationFactProjection grant(String delegatorId) {
        return DelegationFactProjection.ofGranted("dgr-1", delegatorId, "emp-d",
                FROM, TO, "vacation", FROM, "evt-1");
    }

    private EmployeeProjection emp(String id, String deptId) {
        return EmployeeProjection.of(id, "E-" + id, id, deptId, null, null,
                MasterStatus.ACTIVE, null, null, FROM, "e-evt");
    }

    @Test
    void listUnboundedScopeBuildsNetZeroFilterWithActiveAt() {
        when(delegationRepository.findPage(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(delegationRepository.count(any())).thenReturn(0L);

        useCase.list("emp-a", null, DelegationFactStatus.ACTIVE, ACTIVE_AT, null, 0, 20);

        ArgumentCaptor<DelegationFactFilter> captor =
                ArgumentCaptor.forClass(DelegationFactFilter.class);
        verify(delegationRepository).findPage(captor.capture(), eq(0), eq(20));
        DelegationFactFilter filter = captor.getValue();
        assertThat(filter.scopeUnbounded()).isTrue();
        assertThat(filter.delegatorId()).isEqualTo("emp-a");
        assertThat(filter.status()).isEqualTo(DelegationFactStatus.ACTIVE);
        assertThat(filter.activeAt()).isEqualTo(ACTIVE_AT);
    }

    @Test
    void listBoundedScopeResolvesDelegatorSubtreeIds() {
        when(departmentRepository.findSubtreeIds("dept-root", 32))
                .thenReturn(List.of("dept-root", "dept-in"));
        when(employeeRepository.findIdsByDepartmentIdIn(List.of("dept-root", "dept-in")))
                .thenReturn(List.of("emp-a", "emp-b"));
        when(delegationRepository.findPage(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(delegationRepository.count(any())).thenReturn(0L);

        DelegationFactPage page = useCase.list(null, null, null, null,
                List.of("dept-root"), 0, 20);

        ArgumentCaptor<DelegationFactFilter> captor =
                ArgumentCaptor.forClass(DelegationFactFilter.class);
        verify(delegationRepository).findPage(captor.capture(), eq(0), eq(20));
        DelegationFactFilter filter = captor.getValue();
        assertThat(filter.scopeUnbounded()).isFalse();
        assertThat(filter.scopedDelegatorIds()).containsExactlyInAnyOrder("emp-a", "emp-b");
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void getOneInScopeDelegatorResolvesViaDepartment() {
        when(delegationRepository.findById("dgr-1")).thenReturn(Optional.of(grant("emp-a")));
        when(departmentRepository.findSubtreeIds("dept-root", 32))
                .thenReturn(List.of("dept-root", "dept-in"));
        when(employeeRepository.findById("emp-a")).thenReturn(Optional.of(emp("emp-a", "dept-in")));

        DelegationFactProjection fact = useCase.getOne("dgr-1", List.of("dept-root"));

        assertThat(fact.grantId()).isEqualTo("dgr-1");
    }

    @Test
    void getOneOutOfScopeDelegatorThrowsNotFound() {
        when(delegationRepository.findById("dgr-1")).thenReturn(Optional.of(grant("emp-a")));
        when(departmentRepository.findSubtreeIds("dept-root", 32))
                .thenReturn(List.of("dept-root", "dept-in"));
        // Delegator's department dept-out is NOT in scope → 404 (no existence leak).
        when(employeeRepository.findById("emp-a")).thenReturn(Optional.of(emp("emp-a", "dept-out")));

        assertThatThrownBy(() -> useCase.getOne("dgr-1", List.of("dept-root")))
                .isInstanceOf(ReadModelNotFoundException.class);
    }

    @Test
    void getOneUnresolvedDelegatorIsFailClosedUnderBoundedScope() {
        when(delegationRepository.findById("dgr-1")).thenReturn(Optional.of(grant("emp-ghost")));
        when(departmentRepository.findSubtreeIds("dept-root", 32))
                .thenReturn(List.of("dept-root"));
        when(employeeRepository.findById("emp-ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getOne("dgr-1", List.of("dept-root")))
                .isInstanceOf(ReadModelNotFoundException.class);
    }

    @Test
    void getOneNetZeroPlatformScopeReturnsFact() {
        when(delegationRepository.findById("dgr-1")).thenReturn(Optional.of(grant("emp-a")));

        DelegationFactProjection fact = useCase.getOne("dgr-1", null);

        assertThat(fact.grantId()).isEqualTo("dgr-1");
    }

    @Test
    void getOneProjectionMissThrowsNotFound() {
        when(delegationRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getOne("ghost", null))
                .isInstanceOf(ReadModelNotFoundException.class);
    }
}
