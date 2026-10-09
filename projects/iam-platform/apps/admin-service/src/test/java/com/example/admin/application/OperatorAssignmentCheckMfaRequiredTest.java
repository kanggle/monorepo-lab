package com.example.admin.application;

import com.example.admin.application.exception.SecondFactorRequirementUnavailableException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.TenantEntryPolicyPort;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.domain.rbac.PartnershipStatus;
import com.example.admin.domain.rbac.ScopeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-771 S4 (auth-to-admin.md {@code mfaRequired}, rule 6 · OD-2) — the assignment check now also
 * reports whether entering the selected tenant needs a second factor: the tenant's entry policy ∨ the
 * operator's {@code require_2fa} role flag, computed only after {@code assigned=true} and on every path
 * (platform {@code '*'}, assignment, partnership host reach).
 *
 * <p>AC-1b (F2): a platform-scope SUPER_ADMIN is {@code assigned=true} for every tenant; before S4 nothing
 * else was said, so assume issued a domain token for any tenant with no second factor. The flag now
 * travels with that answer, and auth-service refuses on it (its own provider test).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OperatorAssignmentCheckUseCase — mfaRequired (TASK-MONO-771 S4)")
class OperatorAssignmentCheckMfaRequiredTest {

    private static final String SUB = "00000000-0000-7000-8000-0000000000a1";

    @Mock AdminOperatorPort operatorPort;
    @Mock TenantScopeResolver tenantScopeResolver;
    @Mock OperatorTenantAssignmentPort assignmentPort;
    @Mock TenantPartnershipPort partnershipPort;
    @Mock TenantEntryPolicyPort entryPolicyPort;

    OperatorAssignmentCheckUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new OperatorAssignmentCheckUseCase(tenantScopeResolver, assignmentPort,
                new OperatorOidcSubjectResolver(operatorPort), partnershipPort,
                new UnboundedHostEntitledScopeResolver(),
                new OperatorSecondFactorRequirement(operatorPort, assignmentPort, entryPolicyPort));
    }

    private AdminOperatorPort.OperatorView operator(String home, long id) {
        return new AdminOperatorPort.OperatorView(id, SUB, home, "op@example.com", "h", "Op",
                "ACTIVE", null, null, Instant.now(), Instant.now(), null, null);
    }

    @Test
    @DisplayName("AC-1b (F2): platform '*' SUPER_ADMIN (require_2fa role) → assigned=true AND mfaRequired=true for any tenant")
    void platformSuperAdmin_mfaRequiredEverywhere() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("*", 1L)));
        when(entryPolicyPort.findTenantsRequiringMfa(List.of("acme-corp"))).thenReturn(Set.of());
        when(operatorPort.anyRoleRequires2fa(1L)).thenReturn(true);

        OperatorAssignmentCheckUseCase.Result r = useCase.check(SUB, "acme-corp");

        assertThat(r.assigned()).isTrue();
        assertThat(r.mfaRequired()).isTrue();
    }

    @Test
    @DisplayName("AC-2: policy-ON tenant → mfaRequired=true; control tenant without a policy → false (same operator, no role)")
    void policyOnTenant_vsControlTenant() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("acme-corp", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp", "globex"));
        when(entryPolicyPort.findTenantsRequiringMfa(List.of("acme-corp"))).thenReturn(Set.of("acme-corp"));
        when(entryPolicyPort.findTenantsRequiringMfa(List.of("globex"))).thenReturn(Set.of());
        when(operatorPort.anyRoleRequires2fa(7L)).thenReturn(false);

        OperatorAssignmentCheckUseCase.Result policyOn = useCase.check(SUB, "acme-corp");
        OperatorAssignmentCheckUseCase.Result control = useCase.check(SUB, "globex");

        assertThat(policyOn.assigned()).isTrue();
        assertThat(policyOn.mfaRequired()).isTrue();
        assertThat(control.assigned()).isTrue();
        assertThat(control.mfaRequired()).isFalse();
    }

    @Test
    @DisplayName("AC-2 path-independent: platform '*' without a require_2fa role → the policy of the tenant entered decides")
    void platformScope_policyOfEnteredTenantDecides() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("*", 2L)));
        when(entryPolicyPort.findTenantsRequiringMfa(List.of("acme-corp"))).thenReturn(Set.of("acme-corp"));

        assertThat(useCase.check(SUB, "acme-corp").mfaRequired()).isTrue();
    }

    @Test
    @DisplayName("AC-2 path-independent: partnership host reach into a policy-ON host → mfaRequired=true")
    void partnershipHostReach_policyOn() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("partner-co", 9L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(9L, "partner-co")).thenReturn(Set.of("partner-co"));
        TenantPartnershipPort.PartnershipView p = new TenantPartnershipPort.PartnershipView(
                55L, "p-1", "host-co", "partner-co", PartnershipStatus.ACTIVE,
                ScopeSet.of(List.of("wms"), List.of("WMS_OUTBOUND_OPERATOR")),
                null, null, Instant.now(), Instant.now(), null);
        when(partnershipPort.findActivePartnership("host-co", "partner-co")).thenReturn(Optional.of(p));
        when(partnershipPort.findParticipant(55L, 9L)).thenReturn(Optional.of(
                new TenantPartnershipPort.ParticipantView(55L, 9L, null, Instant.now(), null)));
        when(entryPolicyPort.findTenantsRequiringMfa(List.of("host-co"))).thenReturn(Set.of("host-co"));

        OperatorAssignmentCheckUseCase.Result r = useCase.check(SUB, "host-co");

        assertThat(r.assigned()).isTrue();
        assertThat(r.delegatedScope()).isNotNull();
        assertThat(r.mfaRequired()).isTrue();
    }

    @Test
    @DisplayName("assigned=false → mfaRequired=false and NO requirement read (does not reveal the operator)")
    void notAssigned_mfaFalse_noRead() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("acme-corp", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp")).thenReturn(Set.of("acme-corp"));

        OperatorAssignmentCheckUseCase.Result r = useCase.check(SUB, "globex");

        assertThat(r.assigned()).isFalse();
        assertThat(r.mfaRequired()).isFalse();
        verify(entryPolicyPort, never()).findTenantsRequiringMfa(any());
        verify(operatorPort, never()).anyRoleRequires2fa(anyLong());
    }

    @Test
    @DisplayName("fail-closed: policy read fails → SecondFactorRequirementUnavailable (→ 5xx → auth-service denies)")
    void policyReadFailure_propagates() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("*", 1L)));
        when(entryPolicyPort.findTenantsRequiringMfa(List.of("acme-corp")))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));

        assertThatExceptionOfType(SecondFactorRequirementUnavailableException.class)
                .isThrownBy(() -> useCase.check(SUB, "acme-corp"));
    }
}
