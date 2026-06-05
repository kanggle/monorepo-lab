package com.example.security.domain.pii;

import java.time.Instant;
import java.util.List;

/**
 * Value object capturing the result of a GDPR PII masking operation
 * triggered by {@code account.deleted(anonymized=true)}.
 *
 * <p>Immutable. Created by {@code PiiMaskingService} and passed to the
 * outbox publisher to emit {@code security.pii.masked}.
 */
public record PiiMaskingRecord(
        String accountId,
        String tenantId,
        Instant maskedAt,
        List<String> tableNames
) {
    public PiiMaskingRecord {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (maskedAt == null) {
            throw new IllegalArgumentException("maskedAt must not be null");
        }
        tableNames = tableNames == null ? List.of() : List.copyOf(tableNames);
    }
}
