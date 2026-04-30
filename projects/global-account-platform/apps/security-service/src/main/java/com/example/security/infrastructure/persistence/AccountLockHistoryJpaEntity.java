package com.example.security.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Append-only record of {@code account.locked} events consumed from Kafka.
 * Rows are never updated or deleted — audit-heavy A3 immutability.
 * Deduplication is enforced via a unique constraint on {@code event_id}.
 */
@Entity
@Table(name = "account_lock_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountLockHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "locked_by", nullable = false, length = 36)
    private String lockedBy;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;

    public static AccountLockHistoryJpaEntity create(String eventId, String accountId,
                                                      String reason, String lockedBy,
                                                      String source, Instant occurredAt) {
        AccountLockHistoryJpaEntity e = new AccountLockHistoryJpaEntity();
        e.eventId = eventId;
        e.accountId = accountId;
        e.reason = reason;
        e.lockedBy = lockedBy;
        e.source = source;
        e.occurredAt = occurredAt;
        return e;
    }
}
