package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.infrastructure.client.SecurityServiceClient;
import com.example.admin.infrastructure.persistence.AdminActionJpaEntity;
import com.example.admin.infrastructure.persistence.AdminActionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuditQueryUseCase 단위 테스트")
class AuditQueryUseCaseTest {

    @Mock
    private AdminActionJpaRepository adminActionRepo;
    @Mock
    private SecurityServiceClient securityServiceClient;
    @Mock
    private AdminActionAuditor auditor;
    @Mock
    private OperatorLookupPort operatorLookupPort;

    private AuditQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AuditQueryUseCase(adminActionRepo, securityServiceClient, auditor, operatorLookupPort);

        // Default: normal operator in "fan-platform" (non-platform-scope)
        when(operatorLookupPort.findByOperatorId("op-1"))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(1L, "op-1", "fan-platform")));
        when(auditor.reserveAuditId()).thenReturn("meta-audit-id");

        // Default admin-action repo response for tenant-scoped finder
        when(adminActionRepo.findByTenantId(anyString(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }

    // ── source 분기 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("source=null + accountId 있음 → 세 소스 모두 조회, totalElements=3")
    void query_sourceNullWithAccountId_queriesAllThreeSources() {
        AdminActionJpaEntity entity = adminEntity("audit-1", Instant.now().minusSeconds(10));
        when(adminActionRepo.findByTenantId(eq("fan-platform"), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        SecurityServiceClient.LoginHistoryEntry loginEntry = new SecurityServiceClient.LoginHistoryEntry(
                "evt-login", "acc-1", "SUCCESS", "1.x.x.x", "KR", Instant.now().minusSeconds(5));
        when(securityServiceClient.queryLoginHistory(eq("acc-1"), any(), any()))
                .thenReturn(List.of(loginEntry));

        SecurityServiceClient.SuspiciousEventEntry suspEntry = new SecurityServiceClient.SuspiciousEventEntry(
                "evt-susp", "acc-1", "VELOCITY", "1.x.x.x", Instant.now());
        when(securityServiceClient.querySuspiciousEvents(eq("acc-1"), any(), any()))
                .thenReturn(List.of(suspEntry));

        AuditQueryResult result = useCase.query(cmd("op-1", "acc-1", null, 0, 20, null));

        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.content()).extracting(AuditQueryResult.Entry::source)
                .containsExactlyInAnyOrder("admin", "login_history", "suspicious");
        verify(auditor).record(any());
    }

    @Test
    @DisplayName("source=admin → SecurityServiceClient 호출 없음")
    void query_sourceAdmin_securityClientNotCalled() {
        useCase.query(cmd("op-1", "acc-1", "admin", 0, 20, null));

        verify(securityServiceClient, never()).queryLoginHistory(any(), any(), any());
        verify(securityServiceClient, never()).querySuspiciousEvents(any(), any(), any());
    }

    @Test
    @DisplayName("accountId=null → login·suspicious 조회 스킵")
    void query_nullAccountId_skipsLoginAndSuspicious() {
        useCase.query(cmd("op-1", null, null, 0, 20, null));

        verify(securityServiceClient, never()).queryLoginHistory(any(), any(), any());
        verify(securityServiceClient, never()).querySuspiciousEvents(any(), any(), any());
    }

    // ── size / page 클램핑 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("size=200 → 100으로 클램프")
    void query_sizeAboveMax_clampedToHundred() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(adminActionRepo.findByTenantId(anyString(), any(), any(), any(), any(), captor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        useCase.query(cmd("op-1", null, "admin", 0, 200, null));

        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("size=0 → 1로 클램프")
    void query_sizeZero_clampedToOne() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(adminActionRepo.findByTenantId(anyString(), any(), any(), any(), any(), captor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        useCase.query(cmd("op-1", null, "admin", 0, 0, null));

        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }

    // ── 메타-감사 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("query 호출 시 AUDIT_QUERY 메타-감사 기록")
    void query_recordsMetaAuditWithAuditQueryCode() {
        useCase.query(cmd("op-1", "acc-1", "admin", 0, 20, null));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> captor =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(captor.capture());
        assertThat(captor.getValue().actionCode()).isEqualTo(ActionCode.AUDIT_QUERY);
    }

    // ── TASK-BE-249: 테넌트 스코프 분기 ──────────────────────────────────────────

    @Test
    @DisplayName("TASK-BE-249: 일반 운영자가 다른 tenant 조회 시 TenantScopeDeniedException")
    void query_normalOperator_crossTenantRequest_throwsTenantScopeDenied() {
        // "op-1" is fan-platform; requesting tenantId="other-platform" → denied
        assertThatThrownBy(() -> useCase.query(cmd("op-1", "acc-1", "admin", 0, 20, "other-platform")))
                .isInstanceOf(TenantScopeDeniedException.class);
    }

    @Test
    @DisplayName("TASK-BE-249: 일반 운영자가 자기 tenant 조회 시 성공 — findByTenantId 사용")
    void query_normalOperator_ownTenant_usesFindByTenantId() {
        useCase.query(cmd("op-1", "acc-1", "admin", 0, 20, "fan-platform"));

        verify(adminActionRepo).findByTenantId(eq("fan-platform"), any(), any(), any(), any(), any(Pageable.class));
        verify(adminActionRepo, never()).searchCrossTenant(anyString(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("TASK-BE-249: SUPER_ADMIN이 tenantId=* 요청 시 platform 행 조회")
    void query_superAdmin_platformScopeRequest_usesFindByTenantId_star() {
        when(operatorLookupPort.findByOperatorId("super-op"))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(99L, "super-op", "*")));
        when(adminActionRepo.findByTenantId(eq("*"), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        useCase.query(cmd("super-op", null, "admin", 0, 20, "*"));

        verify(adminActionRepo).findByTenantId(eq("*"), any(), any(), any(), any(), any(Pageable.class));
        verify(adminActionRepo, never()).searchCrossTenant(anyString(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("TASK-BE-249: SUPER_ADMIN이 특정 tenant 조회 시 searchCrossTenant 사용")
    void query_superAdmin_specificTenantRequest_usesSearchCrossTenant() {
        when(operatorLookupPort.findByOperatorId("super-op"))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(99L, "super-op", "*")));
        when(adminActionRepo.searchCrossTenant(eq("fan-platform"), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        useCase.query(cmd("super-op", "acc-1", "admin", 0, 20, "fan-platform"));

        verify(adminActionRepo).searchCrossTenant(eq("fan-platform"), any(), any(), any(), any(), any(Pageable.class));
        verify(adminActionRepo, never()).findByTenantId(anyString(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("TASK-BE-249: 존재하지 않는 operator → TenantScopeDeniedException")
    void query_unknownOperator_throwsTenantScopeDenied() {
        when(operatorLookupPort.findByOperatorId("unknown-op"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.query(cmd("unknown-op", null, "admin", 0, 20, null)))
                .isInstanceOf(TenantScopeDeniedException.class)
                .hasMessageContaining("Operator not found");
    }

    // ── TASK-BE-262: cross-tenant deny audit row ──────────────────────────────

    @Test
    @DisplayName("TASK-BE-262: cross-tenant request denied → auditor.recordCrossTenantDenied() 호출")
    void query_crossTenantRequest_denied_calls_auditor_recordCrossTenantDenied() {
        // "op-1" belongs to "fan-platform" — requesting "other-platform" → denied
        assertThatThrownBy(() -> useCase.query(cmd("op-1", "acc-1", "admin", 0, 20, "other-platform")))
                .isInstanceOf(TenantScopeDeniedException.class);

        verify(auditor).recordCrossTenantDenied(
                any(), anyString(),
                any(ActionCode.class), anyString(), anyString());
        // meta-audit must NOT be recorded (deny happened before the query executed)
        verify(auditor, never()).record(any());
        verify(auditor, never()).reserveAuditId();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static QueryAuditCommand cmd(String operatorId, String accountId, String source,
                                         int page, int size, String tenantId) {
        OperatorContext op = new OperatorContext(operatorId, "jti-1");
        return new QueryAuditCommand(accountId, null, null, null, source, page, size, null, "reason", op, tenantId);
    }

    private static AdminActionJpaEntity adminEntity(String auditId, Instant startedAt) {
        AdminActionJpaEntity e = mock(AdminActionJpaEntity.class);
        when(e.getLegacyAuditId()).thenReturn(auditId);
        when(e.getActionCode()).thenReturn("ACCOUNT_LOCK");
        when(e.getActorId()).thenReturn("op-1");
        when(e.getTargetId()).thenReturn("acc-1");
        when(e.getReason()).thenReturn("reason");
        when(e.getOutcome()).thenReturn("SUCCESS");
        when(e.getStartedAt()).thenReturn(startedAt);
        return e;
    }
}
