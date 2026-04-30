package com.example.admin.application;

import com.example.admin.application.event.AdminEventPublisher;
import com.example.admin.application.exception.AuditFailureException;
import com.example.admin.infrastructure.persistence.AdminActionJpaEntity;
import com.example.admin.infrastructure.persistence.AdminActionJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AdminActionAuditorTest {

    @Mock
    AdminActionJpaRepository repo;

    @Mock
    AdminOperatorJpaRepository operatorRepo;

    @Mock
    AdminEventPublisher publisher;

    @InjectMocks
    AdminActionAuditor auditor;

    @Mock
    AdminOperatorJpaEntity operatorEntity;

    private OperatorContext op() {
        return new OperatorContext("op-1", "jti-1");
    }

    private void stubOperatorResolution() {
        when(operatorRepo.findByOperatorId("op-1")).thenReturn(Optional.of(operatorEntity));
        when(operatorEntity.getId()).thenReturn(42L);
    }

    @Test
    void recordStart_propagates_db_failure_as_audit_failure_and_skips_event() {
        stubOperatorResolution();
        doThrow(new RuntimeException("db down")).when(repo).save(any());

        AdminActionAuditor.StartRecord start = new AdminActionAuditor.StartRecord(
                "audit-1", ActionCode.ACCOUNT_LOCK, op(),
                "ACCOUNT", "acc-1", "fraud", null, "idemp",
                Instant.now());

        assertThatThrownBy(() -> auditor.recordStart(start))
                .isInstanceOf(AuditFailureException.class);

        verify(publisher, never()).publishAdminActionPerformed(any());
    }

    @Test
    void recordStart_persists_in_progress_row_with_resolved_operator_fk() {
        stubOperatorResolution();

        AdminActionAuditor.StartRecord start = new AdminActionAuditor.StartRecord(
                "audit-1", ActionCode.ACCOUNT_LOCK, op(),
                "ACCOUNT", "acc-1", "fraud", null, "idemp",
                Instant.now());

        auditor.recordStart(start);

        ArgumentCaptor<AdminActionJpaEntity> captor = ArgumentCaptor.forClass(AdminActionJpaEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo("IN_PROGRESS");
        assertThat(captor.getValue().getCompletedAt()).isNull();
        assertThat(captor.getValue().getOperatorId()).isEqualTo(42L);
        verify(publisher, never()).publishAdminActionPerformed(any());
    }

    @Test
    void recordStart_throws_audit_failure_when_operator_row_missing() {
        when(operatorRepo.findByOperatorId("op-1")).thenReturn(Optional.empty());

        AdminActionAuditor.StartRecord start = new AdminActionAuditor.StartRecord(
                "audit-1", ActionCode.ACCOUNT_LOCK, op(),
                "ACCOUNT", "acc-1", "fraud", null, "idemp",
                Instant.now());

        assertThatThrownBy(() -> auditor.recordStart(start))
                .isInstanceOf(AuditFailureException.class);

        verify(repo, never()).save(any());
        verify(publisher, never()).publishAdminActionPerformed(any());
    }

    @Test
    void recordCompletion_finalizes_and_publishes_event() {
        AdminActionJpaEntity entity = AdminActionJpaEntity.create(
                "audit-1", "ACCOUNT_LOCK", "op-1", "UNKNOWN",
                "ACCOUNT", "acc-1", "fraud", null, "idemp",
                "IN_PROGRESS", null, Instant.now(), null);
        when(repo.findByLegacyAuditId("audit-1")).thenReturn(Optional.of(entity));

        AdminActionAuditor.CompletionRecord done = new AdminActionAuditor.CompletionRecord(
                "audit-1", ActionCode.ACCOUNT_LOCK, op(),
                "ACCOUNT", "acc-1", "fraud", null, "idemp",
                Outcome.SUCCESS, null, Instant.now(), Instant.now());

        auditor.recordCompletion(done);

        assertThat(entity.getOutcome()).isEqualTo("SUCCESS");
        assertThat(entity.getCompletedAt()).isNotNull();
        verify(repo).save(entity);
        verify(publisher).publishAdminActionPerformed(any());
    }

    @Test
    void recordDenied_inserts_row_and_emits_event() {
        // In unit scope SecurityContext is absent → operator UUID resolves to "unknown".
        when(operatorRepo.findByOperatorId("unknown")).thenReturn(Optional.of(operatorEntity));
        when(operatorEntity.getId()).thenReturn(7L);

        auditor.recordDenied(ActionCode.ACCOUNT_LOCK, "account.lock",
                "/api/admin/accounts/acc-1/lock", "POST", "acc-1");

        ArgumentCaptor<AdminActionJpaEntity> captor = ArgumentCaptor.forClass(AdminActionJpaEntity.class);
        verify(repo).save(captor.capture());
        AdminActionJpaEntity saved = captor.getValue();
        assertThat(saved.getOutcome()).isEqualTo("DENIED");
        assertThat(saved.getPermissionUsed()).isEqualTo("account.lock");
        assertThat(saved.getTargetType()).isEqualTo("ACCOUNT");
        assertThat(saved.getTargetId()).isEqualTo("acc-1");
        assertThat(saved.getOperatorId()).isEqualTo(7L);
        assertThat(saved.getCompletedAt()).isNotNull();
        verify(publisher).publishAdminActionPerformed(any());
    }

    @Test
    void recordDenied_fail_closed_when_operator_row_missing() {
        when(operatorRepo.findByOperatorId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditor.recordDenied(ActionCode.ACCOUNT_LOCK, "account.lock",
                "/api/admin/accounts/acc-1/lock", "POST", "acc-1"))
                .isInstanceOf(AuditFailureException.class);

        verify(repo, never()).save(any());
        verify(publisher, never()).publishAdminActionPerformed(any());
    }

    @Test
    void recordCompletion_missing_in_progress_row_throws_audit_failure() {
        when(repo.findByLegacyAuditId("audit-missing")).thenReturn(Optional.empty());

        AdminActionAuditor.CompletionRecord done = new AdminActionAuditor.CompletionRecord(
                "audit-missing", ActionCode.ACCOUNT_LOCK, op(),
                "ACCOUNT", "acc-1", "fraud", null, "idemp",
                Outcome.SUCCESS, null, Instant.now(), Instant.now());

        assertThatThrownBy(() -> auditor.recordCompletion(done))
                .isInstanceOf(AuditFailureException.class);

        verify(publisher, never()).publishAdminActionPerformed(any());
    }
}
