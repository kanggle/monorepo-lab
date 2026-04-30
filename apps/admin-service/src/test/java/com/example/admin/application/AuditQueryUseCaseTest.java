package com.example.admin.application;

import com.example.admin.infrastructure.client.SecurityServiceClient;
import com.example.admin.infrastructure.persistence.AdminActionJpaEntity;
import com.example.admin.infrastructure.persistence.AdminActionJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private AuditQueryUseCase useCase;

    // ── source 분기 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("source=null + accountId 있음 → 세 소스 모두 조회, totalElements=3")
    void query_sourceNullWithAccountId_queriesAllThreeSources() {
        AdminActionJpaEntity entity = adminEntity("audit-1", Instant.now().minusSeconds(10));
        when(adminActionRepo.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(entity)));

        SecurityServiceClient.LoginHistoryEntry loginEntry = new SecurityServiceClient.LoginHistoryEntry(
                "evt-login", "acc-1", "SUCCESS", "1.x.x.x", "KR", Instant.now().minusSeconds(5));
        when(securityServiceClient.queryLoginHistory(eq("acc-1"), any(), any()))
                .thenReturn(List.of(loginEntry));

        SecurityServiceClient.SuspiciousEventEntry suspEntry = new SecurityServiceClient.SuspiciousEventEntry(
                "evt-susp", "acc-1", "VELOCITY", "1.x.x.x", Instant.now());
        when(securityServiceClient.querySuspiciousEvents(eq("acc-1"), any(), any()))
                .thenReturn(List.of(suspEntry));

        when(auditor.reserveAuditId()).thenReturn("meta-audit-id");

        AuditQueryResult result = useCase.query(cmd("acc-1", null, 0, 20));

        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.content()).extracting(AuditQueryResult.Entry::source)
                .containsExactlyInAnyOrder("admin", "login_history", "suspicious");
        verify(auditor).record(any());
    }

    @Test
    @DisplayName("source=admin → SecurityServiceClient 호출 없음")
    void query_sourceAdmin_securityClientNotCalled() {
        when(adminActionRepo.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(auditor.reserveAuditId()).thenReturn("meta-id");

        useCase.query(cmd("acc-1", "admin", 0, 20));

        verify(securityServiceClient, never()).queryLoginHistory(any(), any(), any());
        verify(securityServiceClient, never()).querySuspiciousEvents(any(), any(), any());
    }

    @Test
    @DisplayName("accountId=null → login·suspicious 조회 스킵")
    void query_nullAccountId_skipsLoginAndSuspicious() {
        when(adminActionRepo.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(auditor.reserveAuditId()).thenReturn("meta-id");

        useCase.query(cmd(null, null, 0, 20));

        verify(securityServiceClient, never()).queryLoginHistory(any(), any(), any());
        verify(securityServiceClient, never()).querySuspiciousEvents(any(), any(), any());
    }

    // ── size / page 클램핑 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("size=200 → 100으로 클램프")
    void query_sizeAboveMax_clampedToHundred() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(adminActionRepo.search(any(), any(), any(), any(), captor.capture()))
                .thenReturn(new PageImpl<>(List.of()));
        when(auditor.reserveAuditId()).thenReturn("meta-id");

        useCase.query(cmd(null, "admin", 0, 200));

        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("size=0 → 1로 클램프")
    void query_sizeZero_clampedToOne() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(adminActionRepo.search(any(), any(), any(), any(), captor.capture()))
                .thenReturn(new PageImpl<>(List.of()));
        when(auditor.reserveAuditId()).thenReturn("meta-id");

        useCase.query(cmd(null, "admin", 0, 0));

        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }

    // ── 메타-감사 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("query 호출 시 AUDIT_QUERY 메타-감사 기록")
    void query_recordsMetaAuditWithAuditQueryCode() {
        when(adminActionRepo.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(auditor.reserveAuditId()).thenReturn("meta-id");

        useCase.query(cmd("acc-1", "admin", 0, 20));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> captor =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(captor.capture());
        assertThat(captor.getValue().actionCode()).isEqualTo(ActionCode.AUDIT_QUERY);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static QueryAuditCommand cmd(String accountId, String source, int page, int size) {
        OperatorContext op = new OperatorContext("op-1", "jti-1");
        return new QueryAuditCommand(accountId, null, null, null, source, page, size, null, "reason", op);
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
