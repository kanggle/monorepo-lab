package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-MONO-772 S4 — the console-eligibility predicate = the token exchange's ({@code oidc_subject = accountId}
 * through the shared {@link OperatorOidcSubjectResolver}, {@code status = ACTIVE}). Neither the status-blind
 * facet read nor the identity axis is consulted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OperatorConsoleEligibilityQueryUseCase (TASK-MONO-772 S4)")
class OperatorConsoleEligibilityQueryUseCaseTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000772";

    @Mock private AdminOperatorPort adminOperatorPort;

    private OperatorConsoleEligibilityQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new OperatorConsoleEligibilityQueryUseCase(new OperatorOidcSubjectResolver(adminOperatorPort));
    }

    private static AdminOperatorPort.OperatorView operator(String status) {
        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        return new AdminOperatorPort.OperatorView(1L, "op-uuid", "acme-corp", "op@example.com", null,
                "Op", status, null, null, now, now, null, null);
    }

    @Test
    @DisplayName("ACTIVE 운영자 → true")
    void active_true() {
        given(adminOperatorPort.findByOidcSubject(ACCOUNT)).willReturn(Optional.of(operator("ACTIVE")));

        assertThat(useCase.isConsoleEligible(ACCOUNT)).isTrue();
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
        verify(adminOperatorPort, never()).existsOperatorFacet(anyString(), anyString());
    }

    @Test
    @DisplayName("빈 accountId → false · 조회 없음 (공용 resolver 의 fail-closed)")
    void blank_false_noLookup() {
        assertThat(useCase.isConsoleEligible(" ")).isFalse();
        verify(adminOperatorPort, never()).findByOidcSubject(anyString());
    }
}
