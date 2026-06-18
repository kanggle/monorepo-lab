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
 * TASK-MONO-299 (ADR-MONO-040 Phase 3 part B) — unit tests for the shared
 * account_id-only {@link OperatorOidcSubjectResolver} that BOTH operator-token
 * exchanges (assume-tenant gate + login-time exchange) delegate to. The Phase-2
 * legacy email fallback is removed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OperatorOidcSubjectResolver (account_id-only) 단위 테스트")
class OperatorOidcSubjectResolverTest {

    private static final String ACCOUNT_ID = "00000000-0000-7000-8000-0000000000a1";

    @Mock AdminOperatorPort operatorPort;
    @InjectMocks OperatorOidcSubjectResolver resolver;

    private AdminOperatorPort.OperatorView op() {
        return new AdminOperatorPort.OperatorView(
                7L, "op-uuid", "acme-corp", "operator@example.com", "hash", "Op",
                "ACTIVE", null, null, Instant.now(), Instant.now(), null, null);
    }

    @Test
    @DisplayName("account_id 키 hit → 그 row 반환")
    void accountIdHit_returnsRow() {
        when(operatorPort.findByOidcSubject(ACCOUNT_ID)).thenReturn(Optional.of(op()));

        assertThat(resolver.resolve(ACCOUNT_ID)).isPresent();
    }

    @Test
    @DisplayName("account_id miss → empty (fail-closed; no row matched)")
    void accountIdMiss_empty() {
        when(operatorPort.findByOidcSubject(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("blank oidcSubject → empty, repository 미조회")
    void blankSubject_emptyWithoutLookup() {
        assertThat(resolver.resolve("  ")).isEmpty();
        verify(operatorPort, never()).findByOidcSubject("  ");
    }

    @Test
    @DisplayName("null oidcSubject → empty, repository 미조회")
    void nullSubject_emptyWithoutLookup() {
        assertThat(resolver.resolve(null)).isEmpty();
        verify(operatorPort, never()).findByOidcSubject(null);
    }
}
