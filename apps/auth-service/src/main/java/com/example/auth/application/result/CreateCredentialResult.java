package com.example.auth.application.result;

import java.time.Instant;

/**
 * Result of persisting a new credential row.
 */
public record CreateCredentialResult(
        String accountId,
        Instant createdAt
) {
}
