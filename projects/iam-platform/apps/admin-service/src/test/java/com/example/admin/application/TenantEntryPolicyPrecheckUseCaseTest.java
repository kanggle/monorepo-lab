package com.example.admin.application;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.SecondFactorEnrolmentPort;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-771 S5 — {@link TenantEntryPolicyPrecheckUseCase} («켜기 전 사전 점검»): the three-way count over the
 * tenant roster, scope gating on the read path, and that an auth-service failure is NOT turned into a number.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TenantEntryPolicyPrecheckUseCase (unit)")
class TenantEntryPolicyPrecheckUseCaseTest {

    @Mock SecondFactorEnrolmentPort enrolmentPort;
    @Mock AdminGrantScopeEvaluator grantScopeEvaluator;
    @Mock AdminActionAuditor auditor;

    TenantEntryPolicyPrecheckUseCase useCase;

    static final String ADMIN = "00000000-0000-7000-8000-0000000c5b01";
    static final OperatorContext ACTOR = new OperatorContext(ADMIN, "jti");

    @BeforeEach
    void setUp() {
        useCase = new TenantEntryPolicyPrecheckUseCase(enrolmentPort, new TenantScopeGuard(grantScopeEvaluator, auditor));
    }

    private void inScope(String tenantId, boolean in) {
        if (in) {
            lenient().when(grantScopeEvaluator.isTenantInAdminScope(ADMIN, Permission.TENANT_SECURITY_MANAGE, tenantId))
                    .thenReturn(true);
        } else {
            when(grantScopeEvaluator.isTenantInAdminScope(ADMIN, Permission.TENANT_SECURITY_MANAGE, tenantId))
                    .thenReturn(false);
        }
    }

    @Test
    @DisplayName("roster [a, b, c, null] · enrolled {b} → operators 4 · enrolled 1 · notEnrolled 2 · unlinked 1")
    void counts() {
        inScope("acme", true);
        List<String> roster = new ArrayList<>(Arrays.asList("acc-a", "acc-b", "acc-c", null));
        when(enrolmentPort.activeOperatorAccountIdsOf("acme")).thenReturn(roster);
        when(enrolmentPort.enrolledAmong(List.of("acc-a", "acc-b", "acc-c"))).thenReturn(Set.of("acc-b"));

        assertThat(useCase.summarize(ACTOR, "acme"))
                .isEqualTo(new TenantEntryPolicyPrecheckUseCase.EnrolmentSummary("acme", 4, 1, 2, 1));
    }

    @Test
    @DisplayName("nobody linked → auth-service is not asked at all")
    void noLinked_noRemoteCall() {
        inScope("acme", true);
        when(enrolmentPort.activeOperatorAccountIdsOf("acme")).thenReturn(Arrays.asList(null, " "));

        assertThat(useCase.summarize(ACTOR, "acme"))
                .isEqualTo(new TenantEntryPolicyPrecheckUseCase.EnrolmentSummary("acme", 2, 0, 0, 2));
    }

    @Test
    @DisplayName("🔴 another tenant → TENANT_SCOPE_DENIED, roster never read (no DENIED row on the read path)")
    void otherTenant_denied() {
        inScope("other", false);

        assertThatExceptionOfType(TenantScopeDeniedException.class).isThrownBy(() -> useCase.summarize(ACTOR, "other"));
        verifyNoInteractions(enrolmentPort, auditor);
    }

    @Test
    @DisplayName("'*' → 400 before anything")
    void platformSentinel() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> useCase.summarize(ACTOR, "*"));
        verifyNoInteractions(grantScopeEvaluator, enrolmentPort);
    }

    @Test
    @DisplayName("auth-service failure propagates (→ 503) — never reported as «0 not enrolled»")
    void authDown_propagates() {
        inScope("acme", true);
        when(enrolmentPort.activeOperatorAccountIdsOf("acme")).thenReturn(List.of("acc-a"));
        when(enrolmentPort.enrolledAmong(List.of("acc-a"))).thenThrow(new DownstreamFailureException("down", null));

        assertThatExceptionOfType(DownstreamFailureException.class).isThrownBy(() -> useCase.summarize(ACTOR, "acme"));
    }
}
