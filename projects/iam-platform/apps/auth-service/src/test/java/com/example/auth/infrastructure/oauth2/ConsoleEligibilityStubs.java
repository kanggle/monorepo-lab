package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.OperatorConsoleEligibilityPort;

/**
 * TASK-MONO-772 S4 — the console-eligibility port for customizer tests whose cells never put a consumer-pool
 * principal on the console client. Throws an {@link AssertionError} (an {@code Error}, so the issuer's
 * fail-closed {@code RuntimeException} catch cannot turn it into a quiet refusal) if any cell does ask.
 */
final class ConsoleEligibilityStubs {

    static final OperatorConsoleEligibilityPort NOT_ASKED = accountId -> {
        throw new AssertionError("console eligibility must not be asked on this path (accountId=" + accountId + ")");
    };

    private ConsoleEligibilityStubs() {
    }
}
