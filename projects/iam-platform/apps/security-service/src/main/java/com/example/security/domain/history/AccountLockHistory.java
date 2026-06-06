package com.example.security.domain.history;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity for an immutable {@code account_lock_history} row.
 *
 * <p>TASK-BE-248 Phase 1 promotes the previously-only-JPA representation to a
 * proper domain entity so that downstream phases (event publishing, query
 * surface, audit projections) can refer to a tenant-aware value object rather
 * than reaching into the persistence layer. The append-only contract from
 * {@link com.example.security.consumer.AccountLockedConsumer} is preserved —
 * this class has no mutators.
 */
public final class AccountLockHistory {

    private final String tenantId;
    private final String eventId;
    private final String accountId;
    private final String reason;
    private final String lockedBy;
    private final String source;
    private final Instant occurredAt;

    public AccountLockHistory(String tenantId, String eventId, String accountId,
                               String reason, String lockedBy, String source,
                               Instant occurredAt) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        this.tenantId = tenantId;
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.lockedBy = Objects.requireNonNull(lockedBy, "lockedBy must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getReason() {
        return reason;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public String getSource() {
        return source;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
