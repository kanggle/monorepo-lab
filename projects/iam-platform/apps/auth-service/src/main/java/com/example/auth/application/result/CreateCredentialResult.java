package com.example.auth.application.result;

import java.time.Instant;

/**
 * Result of persisting a new credential row, or resolving an idempotent retry.
 *
 * <p>TASK-BE-247: {@code wasIdempotent} is {@code true} when the credential row already existed
 * with the same (accountId, email) — the controller returns HTTP 200 in that case instead of 201.
 * Callers (account-service) treat both 200 and 201 as success.</p>
 */
public record CreateCredentialResult(
        String accountId,
        Instant createdAt,
        boolean wasIdempotent
) {
    /**
     * Convenience constructor for the normal (new-row) case. {@code wasIdempotent} defaults to
     * {@code false}.
     */
    public CreateCredentialResult(String accountId, Instant createdAt) {
        this(accountId, createdAt, false);
    }
}
