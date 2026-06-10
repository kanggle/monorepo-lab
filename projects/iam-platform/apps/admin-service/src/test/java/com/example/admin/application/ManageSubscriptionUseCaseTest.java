package com.example.admin.application;

import com.example.admin.application.exception.SubscriptionTransitionInvalidException;
import com.example.admin.application.port.TenantDomainSubscriptionPort;
import com.example.admin.application.tenant.SubscriptionMutationSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static com.example.admin.application.OperatorUseCaseTestSupport.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-343 (ADR-MONO-023 step 2b): unit coverage for the operator-facing
 * subscription management use-case — delegation to the entitlement authority
 * (account-service via the port) + audit-on-success.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManageSubscriptionUseCaseTest {

    @Mock TenantDomainSubscriptionPort port;
    @Mock AdminActionAuditor auditor;

    ManageSubscriptionUseCase useCase;

    @BeforeEach
    void init() {
        useCase = new ManageSubscriptionUseCase(port, auditor);
        when(auditor.newAuditId()).thenReturn("audit-sub");
    }

    @Test
    @DisplayName("subscribe delegates to the port (actorId from context) and records audit on success")
    void subscribe_delegatesAndAudits() {
        when(port.subscribe("acme", "scm", "new contract", "actor-uuid"))
                .thenReturn(new SubscriptionMutationSummary(
                        "acme", "scm", null, "ACTIVE", Instant.parse("2026-06-10T00:00:00Z")));

        SubscriptionMutationSummary result =
                useCase.subscribe("acme", "scm", actor(), "new contract");

        assertThat(result.currentStatus()).isEqualTo("ACTIVE");
        assertThat(result.previousStatus()).isNull();
        verify(port).subscribe("acme", "scm", "new contract", "actor-uuid");
        verify(auditor).record(any(AdminActionAuditor.AuditRecord.class));
    }

    @Test
    @DisplayName("changeStatus delegates to the port and records audit on success")
    void changeStatus_delegatesAndAudits() {
        when(port.changeStatus("acme", "wms", "SUSPENDED", "past due", "actor-uuid"))
                .thenReturn(new SubscriptionMutationSummary(
                        "acme", "wms", "ACTIVE", "SUSPENDED", Instant.parse("2026-06-10T00:00:00Z")));

        SubscriptionMutationSummary result =
                useCase.changeStatus("acme", "wms", "SUSPENDED", actor(), "past due");

        assertThat(result.previousStatus()).isEqualTo("ACTIVE");
        assertThat(result.currentStatus()).isEqualTo("SUSPENDED");
        verify(port).changeStatus("acme", "wms", "SUSPENDED", "past due", "actor-uuid");
        verify(auditor).record(any(AdminActionAuditor.AuditRecord.class));
    }

    @Test
    @DisplayName("a failed delegation propagates and writes NO audit row (record-on-success)")
    void changeStatus_failureNotAudited() {
        when(port.changeStatus("acme", "wms", "ACTIVE", "resume", "actor-uuid"))
                .thenThrow(new SubscriptionTransitionInvalidException("illegal"));

        assertThatThrownBy(() -> useCase.changeStatus("acme", "wms", "ACTIVE", actor(), "resume"))
                .isInstanceOf(SubscriptionTransitionInvalidException.class);

        verify(auditor, never()).record(any(AdminActionAuditor.AuditRecord.class));
    }
}
