package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.port.AccountSecondFactorResetPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * TASK-MONO-771 S6 — {@link AccountSecondFactorResetPort} over {@link AuthServiceClient#resetSecondFactor}
 * (admin-to-auth.md § second-factor reset).
 */
@Component
@RequiredArgsConstructor
public class AccountSecondFactorResetAdapter implements AccountSecondFactorResetPort {

    private final AuthServiceClient authServiceClient;

    @Override
    public ResetResult reset(String accountId, String operatorId, String reason, String idempotencyKey) {
        AuthServiceClient.SecondFactorResetResponse resp =
                authServiceClient.resetSecondFactor(accountId, operatorId, reason, idempotencyKey);
        if (resp.wasConfirmed() == null) {
            // The contract says the field is always there; a 200 without it is not an answer we can audit.
            throw new DownstreamFailureException("auth-service second-factor reset: 'wasConfirmed' missing", null);
        }
        return new ResetResult(
                resp.accountId() != null ? resp.accountId() : accountId,
                resp.resetAt() != null ? resp.resetAt() : Instant.now(),
                resp.wasConfirmed());
    }
}
