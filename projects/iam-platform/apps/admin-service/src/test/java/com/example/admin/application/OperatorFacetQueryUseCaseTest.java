package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-MONO-772 S6 (auth-to-admin.md § facet — {@code axes}) — the caller picks the axes; each axis keeps its
 * TASK-BE-618 predicate. The self-onboarded operator (subject axis) is invisible to an {@code IDENTITY}-only
 * question, which is exactly what lets the consumer-pool mover move it (ADR-MONO-080 D6 · D-6); an operator found
 * by its identity link stays faceted on that same question (F11 — still protected).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OperatorFacetQueryUseCase — axes (TASK-MONO-772 S6)")
class OperatorFacetQueryUseCaseTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000772";
    private static final String IDENTITY = "0199de71-0000-7000-8000-000000000772";

    @Mock
    private AdminOperatorPort adminOperatorPort;

    @InjectMocks
    private OperatorFacetQueryUseCase useCase;

    @Test
    @DisplayName("🔴 셀프 온보딩 운영자(oidc_subject 축만): 두 축 → true(772 이전 그대로) · IDENTITY 만 → false(옮겨진다) · 주체 축은 묻지도 않는다")
    void selfOnboarded_identityOnly_false() {
        given(adminOperatorPort.existsOperatorBySubject(ACCOUNT)).willReturn(true);

        assertThat(useCase.isOperatorFaceted(ACCOUNT, IDENTITY)).as("axes omitted = both").isTrue();

        given(adminOperatorPort.existsOperatorByIdentity(IDENTITY)).willReturn(false);
        assertThat(useCase.isOperatorFaceted(ACCOUNT, IDENTITY, Set.of(OperatorFacetAxis.IDENTITY))).isFalse();
        // The subject lookup ran once — for the both-axes call, never for the IDENTITY-only one.
        verify(adminOperatorPort).existsOperatorBySubject(ACCOUNT);
    }

    @Test
    @DisplayName("🔴 대조군(F11): 신원 연결만 있는 운영자 → IDENTITY 만 물어도 true (계속 건너뛴다)")
    void identityLinked_identityOnly_true() {
        given(adminOperatorPort.existsOperatorByIdentity(IDENTITY)).willReturn(true);

        assertThat(useCase.isOperatorFaceted(ACCOUNT, IDENTITY, Set.of(OperatorFacetAxis.IDENTITY))).isTrue();
        verify(adminOperatorPort, never()).existsOperatorBySubject(any());
    }

    @Test
    @DisplayName("OIDC_SUBJECT 만 → 신원 축은 묻지 않는다")
    void subjectOnly_identityNotAsked() {
        given(adminOperatorPort.existsOperatorBySubject(ACCOUNT)).willReturn(false);

        assertThat(useCase.isOperatorFaceted(ACCOUNT, IDENTITY, Set.of(OperatorFacetAxis.OIDC_SUBJECT))).isFalse();
        verify(adminOperatorPort, never()).existsOperatorByIdentity(any());
    }

    @Test
    @DisplayName("parse: null → 두 축 · 목록 · 모르는 값/빈 값/빈 원소 → IllegalArgumentException(값을 메시지에 싣지 않음)")
    void parse() {
        assertThat(OperatorFacetAxis.parse(null)).containsExactlyInAnyOrder(OperatorFacetAxis.values());
        assertThat(OperatorFacetAxis.parse("IDENTITY")).containsExactly(OperatorFacetAxis.IDENTITY);
        assertThat(OperatorFacetAxis.parse(" IDENTITY , OIDC_SUBJECT ")).containsExactlyInAnyOrder(OperatorFacetAxis.values());
        for (String bad : new String[] {"", " ", "IDENTITY,", ",IDENTITY", "EMAIL", "identity", "IDENTITY,,OIDC_SUBJECT"}) {
            assertThatThrownBy(() -> OperatorFacetAxis.parse(bad)).as("[%s]", bad)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("axes must be a comma list of OIDC_SUBJECT, IDENTITY");
        }
    }
}
