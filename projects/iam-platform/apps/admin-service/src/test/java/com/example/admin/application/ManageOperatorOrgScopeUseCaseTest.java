package com.example.admin.application;

import com.example.admin.application.exception.AssignmentNotFoundException;
import com.example.admin.application.exception.InvalidRequestException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.TenantScopeMismatchException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort.AssignmentView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-339 — unit tests for {@link ManageOperatorOrgScopeUseCase}: set
 * non-empty / clear null / explicit [] / tenant-mismatch 403 / not-assigned 404
 * / normalization (dedupe + blank-reject). The persistence + serialization
 * round-trip is covered by the IT.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("ManageOperatorOrgScopeUseCase 단위 테스트 (TASK-BE-339 org_scope 관리)")
class ManageOperatorOrgScopeUseCaseTest {

    private static final String OP_PUBLIC_ID = "00000000-0000-7000-8000-0000000000a1";
    private static final long OP_INTERNAL_ID = 42L;
    private static final String TENANT = "acme-corp";

    @Mock
    private AdminOperatorPort operatorPort;

    @Mock
    private OperatorTenantAssignmentPort assignmentPort;

    @Mock
    private AdminActionAuditor auditor;

    @Mock
    private TenantScopeGuard tenantScopeGuard;

    @InjectMocks
    private ManageOperatorOrgScopeUseCase useCase;

    private final OperatorContext caller = new OperatorContext("caller-uuid", "jti-1");

    private AdminOperatorPort.OperatorView operatorView() {
        return new AdminOperatorPort.OperatorView(
                OP_INTERNAL_ID, OP_PUBLIC_ID, TENANT, "op@example.com", "hash",
                "Op", "ACTIVE", null, null, Instant.now(), Instant.now(), null, null);
    }

    // ───────────────────────────── listAssignments ─────────────────────────────

    @Test
    @DisplayName("listAssignments: 활성 테넌트 assignment 1행 반환")
    void list_returnsActiveTenantAssignment() {
        when(operatorPort.findByOperatorId(OP_PUBLIC_ID)).thenReturn(Optional.of(operatorView()));
        when(assignmentPort.findAssignment(OP_INTERNAL_ID, TENANT))
                .thenReturn(Optional.of(new AssignmentView(TENANT, List.of("dept-sales"), null)));

        List<AssignmentView> result = useCase.listAssignments(OP_PUBLIC_ID, TENANT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tenantId()).isEqualTo(TENANT);
        assertThat(result.get(0).orgScope()).containsExactly("dept-sales");
    }

    @Test
    @DisplayName("listAssignments: 활성 테넌트에 미배정 → 빈 배열")
    void list_emptyWhenNotAssigned() {
        when(operatorPort.findByOperatorId(OP_PUBLIC_ID)).thenReturn(Optional.of(operatorView()));
        when(assignmentPort.findAssignment(OP_INTERNAL_ID, TENANT)).thenReturn(Optional.empty());

        assertThat(useCase.listAssignments(OP_PUBLIC_ID, TENANT)).isEmpty();
    }

    @Test
    @DisplayName("listAssignments: 활성 테넌트 헤더 없으면 빈 배열")
    void list_emptyWhenNoActiveTenant() {
        when(operatorPort.findByOperatorId(OP_PUBLIC_ID)).thenReturn(Optional.of(operatorView()));

        assertThat(useCase.listAssignments(OP_PUBLIC_ID, null)).isEmpty();
    }

    @Test
    @DisplayName("listAssignments: 미존재 operator → OperatorNotFoundException")
    void list_operatorNotFound() {
        when(operatorPort.findByOperatorId(OP_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.listAssignments(OP_PUBLIC_ID, TENANT))
                .isInstanceOf(OperatorNotFoundException.class);
    }

    // ───────────────────────────── setOrgScope ─────────────────────────────

    @Test
    @DisplayName("setOrgScope: 비-빈 배열 정규화(dedupe+trim) 후 영속 + 감사")
    void set_nonEmpty_normalizedAndPersisted() {
        when(operatorPort.findByOperatorId(OP_PUBLIC_ID)).thenReturn(Optional.of(operatorView()));
        when(assignmentPort.findAssignment(OP_INTERNAL_ID, TENANT))
                .thenReturn(Optional.of(new AssignmentView(TENANT, null, 7L)));

        AssignmentView result = useCase.setOrgScope(OP_PUBLIC_ID, TENANT, TENANT,
                List.of(" dept-a ", "dept-b", "dept-a"), caller, "reorg");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(assignmentPort).updateOrgScope(eq(OP_INTERNAL_ID), eq(TENANT), captor.capture());
        // trim + dedupe preserving order.
        assertThat(captor.getValue()).containsExactly("dept-a", "dept-b");
        assertThat(result.orgScope()).containsExactly("dept-a", "dept-b");
        verify(auditor).recordWithPermission(any(), eq("operator.manage"));
    }

    @Test
    @DisplayName("setOrgScope: null → 컬럼 NULL clear (net-zero)")
    void set_null_clears() {
        when(operatorPort.findByOperatorId(OP_PUBLIC_ID)).thenReturn(Optional.of(operatorView()));
        when(assignmentPort.findAssignment(OP_INTERNAL_ID, TENANT))
                .thenReturn(Optional.of(new AssignmentView(TENANT, List.of("dept-x"), null)));

        AssignmentView result = useCase.setOrgScope(OP_PUBLIC_ID, TENANT, TENANT, null, caller, "clear");

        verify(assignmentPort).updateOrgScope(OP_INTERNAL_ID, TENANT, null);
        assertThat(result.orgScope()).isNull();
    }

    @Test
    @DisplayName("setOrgScope: [] → 명시적 zero-scope 영속(NULL 과 구분)")
    void set_emptyList_persistsZeroScope() {
        when(operatorPort.findByOperatorId(OP_PUBLIC_ID)).thenReturn(Optional.of(operatorView()));
        when(assignmentPort.findAssignment(OP_INTERNAL_ID, TENANT))
                .thenReturn(Optional.of(new AssignmentView(TENANT, null, null)));

        AssignmentView result = useCase.setOrgScope(OP_PUBLIC_ID, TENANT, TENANT, List.of(), caller, "lockdown");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(assignmentPort).updateOrgScope(eq(OP_INTERNAL_ID), eq(TENANT), captor.capture());
        assertThat(captor.getValue()).isNotNull().isEmpty();
        assertThat(result.orgScope()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("setOrgScope: path tenantId != active tenant → TenantScopeMismatchException (403)")
    void set_tenantMismatch() {
        assertThatThrownBy(() -> useCase.setOrgScope(OP_PUBLIC_ID, "other-tenant", TENANT,
                List.of("dept-a"), caller, "reason"))
                .isInstanceOf(TenantScopeMismatchException.class);

        verify(operatorPort, never()).findByOperatorId(any());
        verify(assignmentPort, never()).updateOrgScope(any(), any(), any());
    }

    @Test
    @DisplayName("setOrgScope: assignment 행 부재 → AssignmentNotFoundException (404)")
    void set_assignmentNotFound() {
        when(operatorPort.findByOperatorId(OP_PUBLIC_ID)).thenReturn(Optional.of(operatorView()));
        when(assignmentPort.findAssignment(OP_INTERNAL_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.setOrgScope(OP_PUBLIC_ID, TENANT, TENANT,
                List.of("dept-a"), caller, "reason"))
                .isInstanceOf(AssignmentNotFoundException.class);

        verify(assignmentPort, never()).updateOrgScope(any(), any(), any());
    }

    @Test
    @DisplayName("setOrgScope: blank 원소 거부 → InvalidRequestException (400)")
    void set_blankEntryRejected() {
        when(operatorPort.findByOperatorId(OP_PUBLIC_ID)).thenReturn(Optional.of(operatorView()));
        when(assignmentPort.findAssignment(OP_INTERNAL_ID, TENANT))
                .thenReturn(Optional.of(new AssignmentView(TENANT, null, null)));

        assertThatThrownBy(() -> useCase.setOrgScope(OP_PUBLIC_ID, TENANT, TENANT,
                java.util.Arrays.asList("dept-a", "  "), caller, "reason"))
                .isInstanceOf(InvalidRequestException.class);

        verify(assignmentPort, never()).updateOrgScope(any(), any(), any());
    }

    @Test
    @DisplayName("setOrgScope: 미존재 operator → OperatorNotFoundException (404)")
    void set_operatorNotFound() {
        when(operatorPort.findByOperatorId(OP_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.setOrgScope(OP_PUBLIC_ID, TENANT, TENANT,
                List.of("dept-a"), caller, "reason"))
                .isInstanceOf(OperatorNotFoundException.class);
    }
}
