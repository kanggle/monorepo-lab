package com.example.admin.application;

import com.example.admin.application.event.AdminEventPublisher;
import com.example.admin.application.exception.AuditFailureException;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.infrastructure.persistence.AdminActionJpaEntity;
import com.example.admin.infrastructure.persistence.AdminActionJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit cohort for {@link AdminActionDenyWriter}. Covers the two DENIED row
 * variants that were on the legacy {@code AdminActionAuditor}:
 * {@code recordDenied} (fail-closed aspect deny) and
 * {@code recordCrossTenantDenied} (best-effort cross-tenant scope deny).
 *
 * <p>Every assertion in this file is the verbatim assertion the legacy
 * {@code AdminActionAuditorTest} carried for the deny paths (mock target is
 * the only plumbing difference per TASK-BE-314).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AdminActionDenyWriterTest {

    @Mock
    AdminActionJpaRepository repo;

    @Mock
    OperatorLookupPort operatorLookupPort;

    @Mock
    AdminEventPublisher publisher;

    @Mock
    MeterRegistry meterRegistry;

    @Spy
    AdminActionPermissionRegistry permissions = new AdminActionPermissionRegistry();

    @InjectMocks
    AdminActionDenyWriter denyWriter;

    private OperatorContext op() {
        return new OperatorContext("op-1", "jti-1");
    }

    private void stubOperatorResolution() {
        when(operatorLookupPort.findByOperatorId("op-1"))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(42L, "op-1", "fan-platform")));
    }

    @Test
    void recordDenied_inserts_row_and_emits_event() {
        // In unit scope SecurityContext is absent → operator UUID resolves to "unknown".
        when(operatorLookupPort.findByOperatorId("unknown"))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(7L, "unknown", "fan-platform")));

        denyWriter.recordDenied(ActionCode.ACCOUNT_LOCK, "account.lock",
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
        when(operatorLookupPort.findByOperatorId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> denyWriter.recordDenied(ActionCode.ACCOUNT_LOCK, "account.lock",
                "/api/admin/accounts/acc-1/lock", "POST", "acc-1"))
                .isInstanceOf(AuditFailureException.class);

        verify(repo, never()).save(any());
        verify(publisher, never()).publishAdminActionPerformed(any());
    }

    // ── TASK-BE-262: recordCrossTenantDenied ─────────────────────────────────

    @Test
    void recordCrossTenantDenied_inserts_denied_row_with_own_tenant_and_emits_event() {
        stubOperatorResolution();

        OperatorContext actor = op();
        denyWriter.recordCrossTenantDenied(
                actor, "fan-platform", ActionCode.AUDIT_QUERY, "audit.read", "other-tenant");

        ArgumentCaptor<AdminActionJpaEntity> captor = ArgumentCaptor.forClass(AdminActionJpaEntity.class);
        verify(repo).save(captor.capture());
        AdminActionJpaEntity saved = captor.getValue();
        assertThat(saved.getOutcome()).isEqualTo("DENIED");
        assertThat(saved.getPermissionUsed()).isEqualTo("audit.read");
        assertThat(saved.getTenantId()).isEqualTo("fan-platform");
        assertThat(saved.getTargetTenantId()).isEqualTo("fan-platform"); // both = operator's own tenant per spec
        assertThat(saved.getDownstreamDetail()).contains("other-tenant");
        verify(publisher).publishAdminActionPerformed(any());
    }

    @Test
    void recordCrossTenantDenied_bestEffort_swallows_db_failure_and_does_not_throw() {
        stubOperatorResolution();
        doThrow(new RuntimeException("db down")).when(repo).save(any());

        // Must NOT throw — best-effort swallows the exception
        OperatorContext actor = op();
        assertThatCode(() ->
                denyWriter.recordCrossTenantDenied(
                        actor, "fan-platform", ActionCode.OPERATOR_CREATE, "operator.create", "*")
        ).doesNotThrowAnyException();

        verify(publisher, never()).publishAdminActionPerformed(any());
    }
}
