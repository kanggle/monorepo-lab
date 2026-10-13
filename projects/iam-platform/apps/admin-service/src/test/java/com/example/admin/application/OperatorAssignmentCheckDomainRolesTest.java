package com.example.admin.application;

import com.example.admin.application.exception.DomainAccessUnavailableException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.EffectiveDomainRolePort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-631 AC-6 (auth-to-admin.md § check rule 7 · ADR-MONO-083 D6) — {@code domainRoles} on the assume check:
 * <ul>
 *   <li>membership path → the roles of the (operator, T) set rows; none → {@code []};</li>
 *   <li>{@code '*'} path → (operator, T) ∪ (operator, {@code '*'}) rows — and {@code []}, not «everything», when none;</li>
 *   <li>partnership host reach → the field is omitted and the host's set rows are NOT read;</li>
 *   <li>{@code assigned=false} → omitted, nothing read;</li>
 *   <li>a read failure → {@link DomainAccessUnavailableException} (5xx), never {@code []}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OperatorAssignmentCheckUseCase — domainRoles (TASK-BE-631 AC-6)")
class OperatorAssignmentCheckDomainRolesTest {

    private static final String SUB = "00000000-0000-7000-8000-0000000631a1";

    @Mock AdminOperatorPort operatorPort;
    @Mock TenantScopeResolver tenantScopeResolver;
    @Mock OperatorTenantAssignmentPort assignmentPort;
    @Mock TenantPartnershipPort partnershipPort;
    @Mock EffectiveDomainRolePort domainRolePort;

    OperatorAssignmentCheckUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new OperatorAssignmentCheckUseCase(tenantScopeResolver, assignmentPort,
                new OperatorOidcSubjectResolver(operatorPort), partnershipPort,
                new UnboundedHostEntitledScopeResolver(),
                new OperatorSecondFactorRequirement(operatorPort, assignmentPort, ids -> Set.of()),
                domainRolePort);
    }

    private AdminOperatorPort.OperatorView operator(String home, long id) {
        return new AdminOperatorPort.OperatorView(id, SUB, home, "op@example.com", "h", "Op",
                "ACTIVE", null, null, Instant.now(), Instant.now(), null, null);
    }

    @Test
    @DisplayName("membership path: domainRoles = the roles of the (operator, T) rows (the union, in the port's order)")
    void membershipPath_union() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("acme", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme")).thenReturn(Set.of("acme"));
        when(domainRolePort.effectiveRoleNames(7L, List.of("acme")))
                .thenReturn(List.of("INVENTORY_READ", "INVENTORY_WRITE", "OUTBOUND_READ"));

        OperatorAssignmentCheckUseCase.Result r = useCase.check(SUB, "acme");

        assertThat(r.assigned()).isTrue();
        assertThat(r.domainRoles()).containsExactly("INVENTORY_READ", "INVENTORY_WRITE", "OUTBOUND_READ");
    }

    @Test
    @DisplayName("membership path with no set (admin role only) → domainRoles = [] (present, not omitted)")
    void membershipPath_noSet_emptyList() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("acme", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme")).thenReturn(Set.of("acme"));
        when(domainRolePort.effectiveRoleNames(7L, List.of("acme"))).thenReturn(List.of());

        OperatorAssignmentCheckUseCase.Result r = useCase.check(SUB, "acme");

        assertThat(r.assigned()).isTrue();
        assertThat(r.domainRoles()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("'*' path: reads (operator, T) ∪ (operator, '*') — and [] (not «everything») when there is no row")
    void platformPath_readsTenantAndStar() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("*", 1L)));
        when(domainRolePort.effectiveRoleNames(1L, List.of("acme", "*")))
                .thenReturn(List.of("SCM_OPERATOR", "FAN_OPERATOR"));
        when(domainRolePort.effectiveRoleNames(1L, List.of("globex", "*"))).thenReturn(List.of());

        assertThat(useCase.check(SUB, "acme").domainRoles()).containsExactly("SCM_OPERATOR", "FAN_OPERATOR");
        assertThat(useCase.check(SUB, "globex").domainRoles())
                .as("a '*' operator without a set row gets nothing, not the whole catalog")
                .isNotNull().isEmpty();
    }

    @Test
    @DisplayName("partnership host reach → domainRoles omitted (null) and the host's set rows are never read")
    void partnershipPath_omitted_notRead() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("partner-co", 9L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(9L, "partner-co")).thenReturn(Set.of("partner-co"));
        TenantPartnershipPort.PartnershipView p = new TenantPartnershipPort.PartnershipView(
                55L, "p-1", "host-co", "partner-co", PartnershipStatus.ACTIVE,
                ScopeSet.of(List.of("wms"), List.of("OUTBOUND_READ")),
                null, null, Instant.now(), Instant.now(), null);
        when(partnershipPort.findActivePartnership("host-co", "partner-co")).thenReturn(Optional.of(p));
        when(partnershipPort.findParticipant(55L, 9L)).thenReturn(Optional.of(
                new TenantPartnershipPort.ParticipantView(55L, 9L, null, Instant.now(), null)));

        OperatorAssignmentCheckUseCase.Result r = useCase.check(SUB, "host-co");

        assertThat(r.assigned()).isTrue();
        assertThat(r.delegatedScope()).isNotNull();
        assertThat(r.domainRoles()).isNull();
        verify(domainRolePort, never()).effectiveRoleNames(anyLong(), anyCollection());
    }

    @Test
    @DisplayName("assigned=false → domainRoles omitted, nothing read")
    void notAssigned_omitted() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("acme", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme")).thenReturn(Set.of("acme"));

        OperatorAssignmentCheckUseCase.Result r = useCase.check(SUB, "globex");

        assertThat(r.assigned()).isFalse();
        assertThat(r.domainRoles()).isNull();
        verify(domainRolePort, never()).effectiveRoleNames(anyLong(), anyCollection());
    }

    @Test
    @DisplayName("🔴 read failure → DomainAccessUnavailableException (5xx), never [] (Failure Scenario 3)")
    void readFailure_throws() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("acme", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme")).thenReturn(Set.of("acme"));
        when(domainRolePort.effectiveRoleNames(7L, List.of("acme")))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));

        assertThatExceptionOfType(DomainAccessUnavailableException.class)
                .isThrownBy(() -> useCase.check(SUB, "acme"));
    }
}
