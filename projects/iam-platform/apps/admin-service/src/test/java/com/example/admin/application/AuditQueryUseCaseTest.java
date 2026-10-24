package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-249 → TASK-BE-357: the tenant resolve+gate is now the shared
 * {@link QueryTenantScopeGate} (covered by {@code QueryTenantScopeGateTest}); these
 * cases mock the gate and focus on this use-case's own responsibilities — the
 * source/finder branching, size/page clamping, and meta-audit.
 */
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
    private QueryTenantScopeGate queryTenantScopeGate;

    private AuditQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AuditQueryUseCase(adminActionRepo, securityServiceClient, auditor, queryTenantScopeGate);

        // Default: normal operator resolves to its own non-platform tenant "fan-platform".
        when(queryTenantScopeGate.resolve(any(), any(), eq(ActionCode.AUDIT_QUERY), eq("audit.read")))
                .thenReturn(new QueryTenantScopeGate.Resolved("fan-platform", false));
        when(auditor.reserveAuditId()).thenReturn("meta-audit-id");
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
        when(securityServiceClient.queryLoginHistory(eq("fan-platform"), eq("acc-1"), any(), any()))
                .thenReturn(List.of(loginEntry));

        SecurityServiceClient.SuspiciousEventEntry suspEntry = new SecurityServiceClient.SuspiciousEventEntry(
                "evt-susp", "acc-1", "VELOCITY", 80, "ALERT", Instant.now());
        when(securityServiceClient.querySuspiciousEvents(eq("fan-platform"), eq("acc-1"), any(), any()))
                .thenReturn(List.of(suspEntry));

        AuditQueryResult result = useCase.query(cmd("op-1", "acc-1", null, 0, 20, null));

        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.content()).extracting(AuditQueryResult.Entry::source)
                .containsExactlyInAnyOrder("admin", "login_history", "suspicious");
        // TASK-BE-654: a security-source read is recorded under the composite permission key.
        verify(auditor).recordWithPermission(any(), eq("audit.read+security.event.read"));
        verify(auditor, never()).record(any());
    }

    @Test
    @DisplayName("source=admin → SecurityServiceClient 호출 없음")
    void query_sourceAdmin_securityClientNotCalled() {
        useCase.query(cmd("op-1", "acc-1", "admin", 0, 20, null));

        verify(securityServiceClient, never()).queryLoginHistory(any(), any(), any(), any());
        verify(securityServiceClient, never()).querySuspiciousEvents(any(), any(), any(), any());
    }

    @Test
    @DisplayName("accountId=null → login·suspicious 조회 스킵")
    void query_nullAccountId_skipsLoginAndSuspicious() {
        useCase.query(cmd("op-1", null, null, 0, 20, null));

        verify(securityServiceClient, never()).queryLoginHistory(any(), any(), any(), any());
        verify(securityServiceClient, never()).querySuspiciousEvents(any(), any(), any(), any());
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

    // ── TASK-BE-654: 메타-감사 행이 무엇을 읽었는지 남긴다 · 실패해도 남는다 ─────────────

    @Test
    @DisplayName("BE-654: admin 소스만 → record(기본 권한) · detail 에 sources=admin · 기간 · actionCode")
    void metaAudit_adminOnly_recordsWhatWasRead() {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-24T00:00:00Z");
        useCase.query(new QueryAuditCommand("acc-1", "ACCOUNT_LOCK", from, to, "admin", 0, 20, null, "reason",
                new OperatorContext("op-1", "jti-1"), null));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> row = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(row.capture());
        verify(auditor, never()).recordWithPermission(any(), any());
        assertThat(row.getValue().outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(row.getValue().targetId()).isEqualTo("acc-1");
        assertThat(row.getValue().downstreamDetail())
                .isEqualTo("sources=admin requestedSource=admin from=2026-10-01T00:00:00Z"
                        + " to=2026-10-24T00:00:00Z actionCode=ACCOUNT_LOCK");
    }

    @Test
    @DisplayName("BE-654: 보안 소스를 읽으면 detail 에 그 소스 · permission_used = audit.read+security.event.read")
    void metaAudit_securitySources_recordedWithCompositePermission() {
        when(securityServiceClient.queryLoginHistory(eq("fan-platform"), eq("acc-1"), any(), any())).thenReturn(List.of());
        when(securityServiceClient.querySuspiciousEvents(eq("fan-platform"), eq("acc-1"), any(), any())).thenReturn(List.of());

        useCase.query(cmd("op-1", "acc-1", null, 0, 20, null));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> row = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(row.capture(), eq("audit.read+security.event.read"));
        assertThat(row.getValue().downstreamDetail()).startsWith("sources=admin,login_history,suspicious requestedSource=-");
    }

    @Test
    @DisplayName("🔴 BE-654: security-service 조회가 실패해도 FAILURE 행이 남고 원래 예외가 나간다")
    void metaAudit_downstreamFailure_leavesFailureRow() {
        when(securityServiceClient.queryLoginHistory(eq("fan-platform"), eq("acc-1"), any(), any()))
                .thenThrow(new IllegalStateException("security-service timeout"));

        assertThatThrownBy(() -> useCase.query(cmd("op-1", "acc-1", "login_history", 0, 20, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security-service timeout");

        ArgumentCaptor<AdminActionAuditor.AuditRecord> row = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        // the failed source may have been read before failing → still the composite permission key
        verify(auditor).recordWithPermission(row.capture(), eq("audit.read+security.event.read"));
        assertThat(row.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(row.getValue().targetId()).isEqualTo("acc-1");
        assertThat(row.getValue().downstreamDetail())
                .contains("requestedSource=login_history")
                .endsWith("failed=login_history error=IllegalStateException");
    }

    @Test
    @DisplayName("BE-654: 실패 행 쓰기마저 실패하면 원래 예외가 나가고 감사 실패는 suppressed 로 붙는다")
    void metaAudit_failureRowWriteFails_originalExceptionWins() {
        when(securityServiceClient.queryLoginHistory(eq("fan-platform"), eq("acc-1"), any(), any()))
                .thenThrow(new IllegalStateException("security-service timeout"));
        org.mockito.Mockito.doThrow(new IllegalStateException("audit store down"))
                .when(auditor).recordWithPermission(any(), any());

        assertThatThrownBy(() -> useCase.query(cmd("op-1", "acc-1", "login_history", 0, 20, null)))
                .hasMessage("security-service timeout")
                .satisfies(t -> assertThat(t.getSuppressed()).extracting(Throwable::getMessage)
                        .containsExactly("audit store down"));
    }

    // ── finder 분기 (gate 결과에 따른 admin_actions 조회 경로) ────────────────────

    @Test
    @DisplayName("일반 운영자(non-platform) → findByTenantId 사용, searchCrossTenant 미사용")
    void query_normalOperator_usesFindByTenantId() {
        // default gate → Resolved("fan-platform", false)
        useCase.query(cmd("op-1", "acc-1", "admin", 0, 20, "fan-platform"));

        verify(adminActionRepo).findByTenantId(eq("fan-platform"), any(), any(), any(), any(), any(Pageable.class));
        verify(adminActionRepo, never()).searchCrossTenant(anyString(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("SUPER_ADMIN이 tenantId=* (platform scope) → findByTenantId('*') 사용")
    void query_superAdmin_platformScope_usesFindByTenantId_star() {
        when(queryTenantScopeGate.resolve(any(), any(), eq(ActionCode.AUDIT_QUERY), eq("audit.read")))
                .thenReturn(new QueryTenantScopeGate.Resolved("*", true));
        when(adminActionRepo.findByTenantId(eq("*"), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        useCase.query(cmd("super-op", null, "admin", 0, 20, "*"));

        verify(adminActionRepo).findByTenantId(eq("*"), any(), any(), any(), any(), any(Pageable.class));
        verify(adminActionRepo, never()).searchCrossTenant(anyString(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("SUPER_ADMIN이 특정 tenant (platform scope) → searchCrossTenant 사용")
    void query_superAdmin_specificTenant_usesSearchCrossTenant() {
        when(queryTenantScopeGate.resolve(any(), any(), eq(ActionCode.AUDIT_QUERY), eq("audit.read")))
                .thenReturn(new QueryTenantScopeGate.Resolved("fan-platform", true));
        when(adminActionRepo.searchCrossTenant(eq("fan-platform"), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        useCase.query(cmd("super-op", "acc-1", "admin", 0, 20, "fan-platform"));

        verify(adminActionRepo).searchCrossTenant(eq("fan-platform"), any(), any(), any(), any(), any(Pageable.class));
        verify(adminActionRepo, never()).findByTenantId(anyString(), any(), any(), any(), any(), any(Pageable.class));
    }

    // ── TASK-BE-656: 보안 소스도 게이트된 테넌트로 읽는다 ───────────────────────────────

    @Test
    @DisplayName("🔴 BE-656 AC-2: 게이트된 테넌트(fan-platform 아님)가 두 보안 조회에 그대로 넘어간다")
    void securitySources_receiveGatedTenant() {
        when(queryTenantScopeGate.resolve(any(), any(), eq(ActionCode.AUDIT_QUERY), eq("audit.read")))
                .thenReturn(new QueryTenantScopeGate.Resolved("ecommerce", false));
        when(securityServiceClient.queryLoginHistory(any(), any(), any(), any())).thenReturn(List.of());
        when(securityServiceClient.querySuspiciousEvents(any(), any(), any(), any())).thenReturn(List.of());

        useCase.query(cmd("op-1", "acc-1", null, 0, 20, "ecommerce"));

        verify(securityServiceClient).queryLoginHistory(eq("ecommerce"), eq("acc-1"), any(), any());
        verify(securityServiceClient).querySuspiciousEvents(eq("ecommerce"), eq("acc-1"), any(), any());
    }

    @Test
    @DisplayName("BE-656: platform scope — '*' 는 '*' 로, 특정 테넌트는 그 테넌트로(admin 소스의 finder 와 같은 값)")
    void securitySources_platformScope() {
        when(securityServiceClient.queryLoginHistory(any(), any(), any(), any())).thenReturn(List.of());
        when(securityServiceClient.querySuspiciousEvents(any(), any(), any(), any())).thenReturn(List.of());
        when(adminActionRepo.searchCrossTenant(anyString(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        when(queryTenantScopeGate.resolve(any(), any(), eq(ActionCode.AUDIT_QUERY), eq("audit.read")))
                .thenReturn(new QueryTenantScopeGate.Resolved("*", true));
        useCase.query(cmd("super-op", "acc-1", null, 0, 20, null));
        verify(securityServiceClient).queryLoginHistory(eq("*"), eq("acc-1"), any(), any());
        verify(securityServiceClient).querySuspiciousEvents(eq("*"), eq("acc-1"), any(), any());

        when(queryTenantScopeGate.resolve(any(), any(), eq(ActionCode.AUDIT_QUERY), eq("audit.read")))
                .thenReturn(new QueryTenantScopeGate.Resolved("consumer-pool", true));
        useCase.query(cmd("super-op", "acc-1", null, 0, 20, "consumer-pool"));
        verify(securityServiceClient).queryLoginHistory(eq("consumer-pool"), eq("acc-1"), any(), any());
        verify(securityServiceClient).querySuspiciousEvents(eq("consumer-pool"), eq("acc-1"), any(), any());
    }

    @Test
    @DisplayName("🔴 BE-656: 의심 이벤트 행은 계약의 id · ruleCode · detectedAt 로 채워진다(IP 없음)")
    void suspiciousEntry_mappedFromContractFields() {
        Instant detectedAt = Instant.parse("2026-10-20T01:02:03Z");
        when(securityServiceClient.querySuspiciousEvents(eq("fan-platform"), eq("acc-1"), any(), any()))
                .thenReturn(List.of(new SecurityServiceClient.SuspiciousEventEntry(
                        "susp-1", "acc-1", "GEO_ANOMALY", 85, "AUTO_LOCK", detectedAt)));

        AuditQueryResult result = useCase.query(cmd("op-1", "acc-1", "suspicious", 0, 20, null));

        assertThat(result.content()).singleElement().satisfies(e -> {
            assertThat(e.source()).isEqualTo("suspicious");
            assertThat(e.eventId()).isEqualTo("susp-1");
            assertThat(e.actionCode()).isEqualTo("GEO_ANOMALY");
            assertThat(e.accountId()).isEqualTo("acc-1");
            assertThat(e.ipMasked()).isNull();
            assertThat(e.occurredAt()).isEqualTo(detectedAt);
        });
    }

    // ── gate 거부 전파 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("gate가 TenantScopeDenied 던지면 전파 + 메타-감사 미기록")
    void query_gateDenies_propagates_noMetaAudit() {
        when(queryTenantScopeGate.resolve(any(), any(), eq(ActionCode.AUDIT_QUERY), eq("audit.read")))
                .thenThrow(new TenantScopeDeniedException("out of scope"));

        assertThatThrownBy(() -> useCase.query(cmd("op-1", "acc-1", "admin", 0, 20, "ecommerce")))
                .isInstanceOf(TenantScopeDeniedException.class);

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
