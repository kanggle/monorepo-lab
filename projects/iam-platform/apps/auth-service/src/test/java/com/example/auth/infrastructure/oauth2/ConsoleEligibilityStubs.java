package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.OperatorConsoleEligibilityPort;
import com.example.auth.domain.repository.CredentialRepository;
import org.mockito.Mockito;

/**
 * TASK-MONO-772 S4 — the console-eligibility port for customizer tests whose cells never put a consumer-pool
 * principal on the console client. Throws an {@link AssertionError} (an {@code Error}, so the issuer's
 * fail-closed {@code RuntimeException} catch cannot turn it into a quiet refusal) if any cell does ask.
 */
final class ConsoleEligibilityStubs {

    static final OperatorConsoleEligibilityPort NOT_ASKED = accountId -> {
        throw new AssertionError("console eligibility must not be asked on this path (accountId=" + accountId + ")");
    };

    /**
     * TASK-MONO-772 S6 — a credential store in which no account has moved into the pool: every
     * {@code findByAccountId} answers empty (Mockito's {@code Optional} default), so the customizer's
     * «console session predates a pool move» check never refuses. For cells that are not about that check.
     */
    static CredentialRepository credentialsNotMoved() {
        return Mockito.mock(CredentialRepository.class);
    }

    private ConsoleEligibilityStubs() {
    }
}
