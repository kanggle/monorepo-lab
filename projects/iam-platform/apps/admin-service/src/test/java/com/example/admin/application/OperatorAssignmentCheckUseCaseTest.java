package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.TenantPartnershipPort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-327 / ADR-MONO-020 § 3.3 step 2 (D2) — unit tests for
 * {@link OperatorAssignmentCheckUseCase}, the read-only authorization helper
 * behind {@code GET /internal/operator-assignments/check}.
 *
 * <p>Covers the fail-closed resolution order (AC-4): unknown subject /
 * non-ACTIVE operator → {@code false}; {@code '*'} platform → {@code true};
 * otherwise the {@link TenantScopeResolver} effective scope decides.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OperatorAssignmentCheckUseCase 단위 테스트 (TASK-BE-327 assignment gate)")
class OperatorAssignmentCheckUseCaseTest {

    private static final String OIDC_SUBJECT = "00000000-0000-7000-8000-0000000000a1";

    @Mock
    private AdminOperatorPort operatorPort;

    @Mock
    private TenantScopeResolver tenantScopeResolver;

    @Mock
    private OperatorTenantAssignmentPort assignmentPort;

    // TASK-BE-477 (ADR-MONO-045): the cross-org partnership branch is exercised only
    // when the normal effective scope misses; findActivePartnership returns null
    // (mock default) so these BE-327/338 tests keep their byte-identical verdicts.
    @Mock
    private TenantPartnershipPort partnershipPort;

    private OperatorAssignmentCheckUseCase useCase;

    @BeforeEach
    void setUp() {
        // TASK-MONO-295: exercise the REAL shared dual-key resolver (wrapping the
        // mock operatorPort) so the dual-key fallback is tested end-to-end through
        // the SAME component the login-time exchange uses — not a stubbed-away mock.
        OperatorOidcSubjectResolver resolver = new OperatorOidcSubjectResolver(operatorPort);
        useCase = new OperatorAssignmentCheckUseCase(tenantScopeResolver, assignmentPort, resolver,
                partnershipPort, new UnboundedHostEntitledScopeResolver(),
                // TASK-MONO-771 S4: no tenant has an entry policy in this suite (row absent = off).
                new OperatorSecondFactorRequirement(operatorPort, assignmentPort, ids -> java.util.Set.of()));
    }

    private AdminOperatorPort.OperatorView operator(String tenantId, String status, long internalId) {
        return new AdminOperatorPort.OperatorView(
                internalId, OIDC_SUBJECT, tenantId, "op@example.com", "hash",
                "Op", status, null, null, Instant.now(), Instant.now(), null, null);
    }

    @Test
    @DisplayName("assigned: effective scope 가 선택 tenant 포함 → true")
    void assigned_whenScopeContainsTenant() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp", "globex"));

        assertThat(useCase.isAssigned(OIDC_SUBJECT, "globex")).isTrue();
    }

    @Test
    @DisplayName("not-assigned: effective scope 에 선택 tenant 없음 → false")
    void notAssigned_whenScopeMissingTenant() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp"));

        assertThat(useCase.isAssigned(OIDC_SUBJECT, "globex")).isFalse();
    }

    @Test
    @DisplayName("unknown subject (no operator row) → false, scope 미조회")
    void unknownSubject_failClosed() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT)).thenReturn(Optional.empty());

        assertThat(useCase.isAssigned(OIDC_SUBJECT, "acme-corp")).isFalse();
        verify(tenantScopeResolver, never()).resolveEffectiveTenantScope(anyLong(), anyString());
    }

    @Test
    @DisplayName("non-ACTIVE operator (DISABLED) → false, scope 미조회")
    void nonActiveOperator_failClosed() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "DISABLED", 7L)));

        assertThat(useCase.isAssigned(OIDC_SUBJECT, "acme-corp")).isFalse();
        verify(tenantScopeResolver, never()).resolveEffectiveTenantScope(anyLong(), anyString());
    }

    @Test
    @DisplayName("'*' platform-scope operator → 임의의 비-blank tenant 에 대해 true, scope 미조회")
    void platformScope_assignedToAnyTenant() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("*", "ACTIVE", 99L)));

        assertThat(useCase.isAssigned(OIDC_SUBJECT, "acme-corp")).isTrue();
        verify(tenantScopeResolver, never()).resolveEffectiveTenantScope(anyLong(), anyString());
    }

    @Test
    @DisplayName("blank tenantId → false, operator 미조회")
    void blankTenant_failClosed() {
        assertThat(useCase.isAssigned(OIDC_SUBJECT, "  ")).isFalse();
        verify(operatorPort, never()).findByOidcSubject(any());
    }

    // ── TASK-BE-338: org_scope on the check result ──────────────────────────────

    @Test
    @DisplayName("BE-338: assigned + 설정된 org_scope → Result.orgScope=그 값")
    void assigned_withPopulatedOrgScope() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp", "globex"));
        when(assignmentPort.findOrgScope(7L, "globex")).thenReturn(List.of("dept-sales"));

        OperatorAssignmentCheckUseCase.Result result = useCase.check(OIDC_SUBJECT, "globex");

        assertThat(result.assigned()).isTrue();
        assertThat(result.orgScope()).containsExactly("dept-sales");
    }

    @Test
    @DisplayName("BE-338: assigned + org_scope 미설정(NULL) → Result.orgScope=null (net-zero → '*')")
    void assigned_withNullOrgScope_netZero() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp"));
        when(assignmentPort.findOrgScope(7L, "acme-corp")).thenReturn(null);

        OperatorAssignmentCheckUseCase.Result result = useCase.check(OIDC_SUBJECT, "acme-corp");

        assertThat(result.assigned()).isTrue();
        assertThat(result.orgScope()).isNull();
    }

    @Test
    @DisplayName("BE-338: '*' platform-scope → Result.orgScope=null (no explicit row), findOrgScope 미조회")
    void platformScope_orgScopeNull() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("*", "ACTIVE", 99L)));

        OperatorAssignmentCheckUseCase.Result result = useCase.check(OIDC_SUBJECT, "acme-corp");

        assertThat(result.assigned()).isTrue();
        assertThat(result.orgScope()).isNull();
        verify(assignmentPort, never()).findOrgScope(anyLong(), anyString());
    }

    @Test
    @DisplayName("BE-338: not-assigned → Result.orgScope=null, findOrgScope 미조회")
    void notAssigned_orgScopeNull() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp"));

        OperatorAssignmentCheckUseCase.Result result = useCase.check(OIDC_SUBJECT, "globex");

        assertThat(result.assigned()).isFalse();
        assertThat(result.orgScope()).isNull();
        verify(assignmentPort, never()).findOrgScope(anyLong(), anyString());
    }

    // ── TASK-MONO-299 (ADR-MONO-040 Phase 3 part B): account_id-only resolution ──

    @Test
    @DisplayName("account_id-only: account_id 키 hit → assigned (oidc_subject 가 account_id 로 backfill 됨)")
    void accountIdOnly_hit_assigned() {
        // The Phase-3 end-state: oidc_subject backfilled to account_id (part A).
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp"));

        OperatorAssignmentCheckUseCase.Result result =
                useCase.check(OIDC_SUBJECT, "acme-corp");

        assertThat(result.assigned()).isTrue();
    }

    @Test
    @DisplayName("account_id-only: account_id 키 miss → fail-closed false (email fallback 없음)")
    void accountIdOnly_miss_failClosed() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT)).thenReturn(Optional.empty());

        OperatorAssignmentCheckUseCase.Result result =
                useCase.check(OIDC_SUBJECT, "acme-corp");

        assertThat(result.assigned()).isFalse();
        // Only the account_id lookup is attempted; no email fallback lookup remains.
        verify(operatorPort).findByOidcSubject(OIDC_SUBJECT);
        verify(tenantScopeResolver, never()).resolveEffectiveTenantScope(anyLong(), anyString());
    }

    // ── TASK-MONO-784 S2 (ADR-MONO-082 b · R1 · D-2 · D-11): membership = assignment rows only ──

    /** The use case wired with the REAL resolver (only the assignment port is mocked). */
    private OperatorAssignmentCheckUseCase withRealResolver() {
        return new OperatorAssignmentCheckUseCase(new TenantScopeResolver(assignmentPort), assignmentPort,
                new OperatorOidcSubjectResolver(operatorPort), partnershipPort,
                new UnboundedHostEntitledScopeResolver(),
                new OperatorSecondFactorRequirement(operatorPort, assignmentPort, ids -> Set.of()));
    }

    @Test
    @DisplayName("🔴 784 S2: 홈만 있고 배정 행 없음 → 홈 assume 거절 · 대조군: 배정 행이 있는 테넌트는 assigned")
    void homeWithoutAssignment_refused_assignedTenantControl() {
        OperatorAssignmentCheckUseCase real = withRealResolver();
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(assignmentPort.findAssignedTenantIds(7L)).thenReturn(Set.of("globex"));

        // Failing side first: the home tenant alone no longer admits (784 AC-0 F4 — R1).
        assertThat(real.check(OIDC_SUBJECT, "acme-corp").assigned())
                .as("home tenant without an assignment row is not a membership")
                .isFalse();
        // Control, same operator, same test: the assignment row still admits.
        assertThat(real.check(OIDC_SUBJECT, "globex").assigned()).isTrue();
    }

    @Test
    @DisplayName("784 S2 (V0051 백필 전제): 홈 배정 행이 있는 단일 회사 운영자 → 지금과 같은 assigned")
    void homeWithBackfilledAssignment_assigned() {
        OperatorAssignmentCheckUseCase real = withRealResolver();
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(assignmentPort.findAssignedTenantIds(7L)).thenReturn(Set.of("acme-corp"));

        assertThat(real.check(OIDC_SUBJECT, "acme-corp").assigned()).isTrue();
    }

    private static TenantPartnershipPort.PartnershipView activePartnership(long id, String host, String partner) {
        return new TenantPartnershipPort.PartnershipView(
                id, "p-" + id, host, partner, com.example.admin.domain.rbac.PartnershipStatus.ACTIVE,
                com.example.admin.domain.rbac.ScopeSet.of(List.of("wms"), List.of("WMS_OUTBOUND_OPERATOR")),
                null, null, Instant.now(), Instant.now(), null);
    }

    @Test
    @DisplayName("🔴 784 S2 D-11: 파트너십 도달 = partner ∈ 소속 — 홈이 아닌 소속 회사로 host 도달")
    void partnershipReach_throughNonHomeMembership() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp", "partner-co"));
        // Walked in sorted order: acme-corp (no partnership with host-co) first, then partner-co.
        when(partnershipPort.findActivePartnership("host-co", "acme-corp")).thenReturn(Optional.empty());
        when(partnershipPort.findActivePartnership("host-co", "partner-co"))
                .thenReturn(Optional.of(activePartnership(55L, "host-co", "partner-co")));
        when(partnershipPort.findParticipant(55L, 7L)).thenReturn(Optional.of(
                new TenantPartnershipPort.ParticipantView(55L, 7L, null, Instant.now(), null)));

        OperatorAssignmentCheckUseCase.Result r = useCase.check(OIDC_SUBJECT, "host-co");

        assertThat(r.assigned()).isTrue();
        assertThat(r.delegatedScope()).isNotNull();
        assertThat(r.delegatedScope().domains()).containsExactly("wms");
    }

    @Test
    @DisplayName("🔴 784 S2 D-11: 홈이 파트너여도 홈이 소속이 아니면 도달 0 (partner = 홈 술어 폐기)")
    void partnershipReach_homeNotMember_noReach() {
        OperatorAssignmentCheckUseCase real = withRealResolver();
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("partner-co", "ACTIVE", 7L)));
        // The operator's home is partner-co, but its only membership is globex.
        when(assignmentPort.findAssignedTenantIds(7L)).thenReturn(Set.of("globex"));

        assertThat(real.check(OIDC_SUBJECT, "host-co").assigned()).isFalse();
        // The partnership is never even looked up through the home.
        verify(partnershipPort, never()).findActivePartnership("host-co", "partner-co");
        verify(partnershipPort).findActivePartnership("host-co", "globex");
    }
}
