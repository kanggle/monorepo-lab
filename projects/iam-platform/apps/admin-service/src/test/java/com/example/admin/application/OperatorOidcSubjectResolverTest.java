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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-295 (ADR-MONO-040 Phase 2) — unit tests for the shared DUAL-KEY
 * {@link OperatorOidcSubjectResolver} that BOTH operator-token exchanges
 * (assume-tenant gate + login-time exchange) delegate to.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OperatorOidcSubjectResolver (dual-key) 단위 테스트")
class OperatorOidcSubjectResolverTest {

    private static final String ACCOUNT_ID = "00000000-0000-7000-8000-0000000000a1";
    private static final String EMAIL = "operator@example.com";

    @Mock AdminOperatorPort operatorPort;
    @InjectMocks OperatorOidcSubjectResolver resolver;

    private AdminOperatorPort.OperatorView op() {
        return new AdminOperatorPort.OperatorView(
                7L, "op-uuid", "acme-corp", EMAIL, "hash", "Op",
                "ACTIVE", null, null, Instant.now(), Instant.now(), null, null);
    }

    @Test
    @DisplayName("account_id 키 hit → 그 row 반환, email fallback 미조회")
    void accountIdHit_returnsRow_emailNotConsulted() {
        when(operatorPort.findByOidcSubject(ACCOUNT_ID)).thenReturn(Optional.of(op()));

        assertThat(resolver.resolve(ACCOUNT_ID, EMAIL)).isPresent();
        verify(operatorPort, never()).findByOidcSubject(EMAIL);
    }

    @Test
    @DisplayName("account_id miss + email hit → 레거시 email fallback 으로 resolve")
    void accountIdMiss_emailHit_resolvesViaFallback() {
        when(operatorPort.findByOidcSubject(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(operatorPort.findByOidcSubject(EMAIL)).thenReturn(Optional.of(op()));

        assertThat(resolver.resolve(ACCOUNT_ID, EMAIL)).isPresent();
    }

    @Test
    @DisplayName("두 키 모두 miss → empty (fail-closed; fallback 이 게이트를 완화하지 않음)")
    void bothKeysMiss_empty() {
        when(operatorPort.findByOidcSubject(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(operatorPort.findByOidcSubject(EMAIL)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(ACCOUNT_ID, EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("email null + account_id miss → fallback 시도 없이 empty")
    void nullEmail_accountIdMiss_noFallbackAttempt() {
        when(operatorPort.findByOidcSubject(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(ACCOUNT_ID, (String) null)).isEmpty();
        verify(operatorPort).findByOidcSubject(ACCOUNT_ID);
    }

    @Test
    @DisplayName("blank account_id + email hit → email fallback 단독으로 resolve")
    void blankAccountId_emailHit_resolvesViaEmail() {
        when(operatorPort.findByOidcSubject(EMAIL)).thenReturn(Optional.of(op()));

        assertThat(resolver.resolve("  ", EMAIL)).isPresent();
        // blank account_id key is skipped — only the email lookup runs.
        verify(operatorPort, never()).findByOidcSubject("  ");
    }
}
