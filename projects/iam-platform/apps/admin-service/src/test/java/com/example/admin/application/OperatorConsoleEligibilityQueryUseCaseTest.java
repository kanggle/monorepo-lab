package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-MONO-772 S4 — the console-eligibility predicate = the token exchange's ({@code oidc_subject = accountId}
 * through the shared {@link OperatorOidcSubjectResolver}, {@code status = ACTIVE}). Neither the status-blind
 * facet read nor the identity axis is consulted.
 *
 * <p>TASK-MONO-784 S2 (ADR-MONO-082 b · D-10): {@code ∧ ('*' ∨ membership ≥ 1)} through the shared
 * {@link OperatorConsoleAdmission} — the same object the token exchange asks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OperatorConsoleEligibilityQueryUseCase (TASK-MONO-772 S4)")
class OperatorConsoleEligibilityQueryUseCaseTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000772";

    @Mock private AdminOperatorPort adminOperatorPort;
    @Mock private OperatorTenantAssignmentPort assignmentPort;

    private OperatorConsoleEligibilityQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new OperatorConsoleEligibilityQueryUseCase(new OperatorOidcSubjectResolver(adminOperatorPort),
                new OperatorConsoleAdmission(assignmentPort));
    }

    private static AdminOperatorPort.OperatorView operator(String status) {
        return operator(status, "acme-corp");
    }

    private static AdminOperatorPort.OperatorView operator(String status, String home) {
        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        return new AdminOperatorPort.OperatorView(1L, "op-uuid", home, "op@example.com", null,
                "Op", status, null, null, now, now, null, null);
    }

    @Test
    @DisplayName("ACTIVE 운영자 · 소속 1 → true")
    void active_true() {
        given(adminOperatorPort.findByOidcSubject(ACCOUNT)).willReturn(Optional.of(operator("ACTIVE")));
        given(assignmentPort.findAssignedTenantIds(1L)).willReturn(Set.of("acme-corp"));

        assertThat(useCase.isConsoleEligible(ACCOUNT)).isTrue();
    }

    @Test
    @DisplayName("🔴 784 S2 (D-10 · F16): ACTIVE 비-'*' · 소속 0 → false / 대조군: 같은 행에 소속 1 → true")
    void active_noMembership_false_controlWithMembership() {
        given(adminOperatorPort.findByOidcSubject(ACCOUNT)).willReturn(Optional.of(operator("ACTIVE")));
        // The row's home stays acme-corp (R1) — it is not a membership.
        given(assignmentPort.findAssignedTenantIds(1L)).willReturn(Set.of(), Set.of("globex"));

        assertThat(useCase.isConsoleEligible(ACCOUNT)).as("last membership removed").isFalse();
        assertThat(useCase.isConsoleEligible(ACCOUNT)).as("control — one membership").isTrue();
    }

    @Test
    @DisplayName("784 S2: '*' 운영자 → 소속을 읽지 않고 true (D2 — '*' 는 소속이 아니다)")
    void platform_true_noMembershipRead() {
        given(adminOperatorPort.findByOidcSubject(ACCOUNT)).willReturn(Optional.of(operator("ACTIVE", "*")));

        assertThat(useCase.isConsoleEligible(ACCOUNT)).isTrue();
        verify(assignmentPort, never()).findAssignedTenantIds(anyLong());
    }

    @Test
    @DisplayName("🔴 AC-3: SUSPENDED · LOCKED · DISABLED 운영자 → false (퇴사 · 정지)")
    void nonActive_false() {
        for (String status : new String[] {"SUSPENDED", "LOCKED", "DISABLED"}) {
            given(adminOperatorPort.findByOidcSubject(ACCOUNT)).willReturn(Optional.of(operator(status)));
            assertThat(useCase.isConsoleEligible(ACCOUNT)).as(status).isFalse();
        }
    }

    @Test
    @DisplayName("🔴 AC-2: 운영자 행 없음 → false · 상태 무관 facet 읽기는 쓰지 않는다")
    void noOperator_false_facetNotUsed() {
        given(adminOperatorPort.findByOidcSubject(ACCOUNT)).willReturn(Optional.empty());

        assertThat(useCase.isConsoleEligible(ACCOUNT)).isFalse();
        verify(adminOperatorPort, never()).existsOperatorBySubject(anyString());
        verify(adminOperatorPort, never()).existsOperatorByIdentity(anyString());
    }

    @Test
    @DisplayName("빈 accountId → false · 조회 없음 (공용 resolver 의 fail-closed)")
    void blank_false_noLookup() {
        assertThat(useCase.isConsoleEligible(" ")).isFalse();
        verify(adminOperatorPort, never()).findByOidcSubject(anyString());
    }
}
