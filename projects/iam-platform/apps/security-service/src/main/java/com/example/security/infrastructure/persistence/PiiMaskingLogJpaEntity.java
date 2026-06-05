package com.example.security.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Idempotency log for GDPR PII masking operations triggered by
 * {@code account.deleted(anonymized=true)} (TASK-BE-258).
 *
 * <p>The unique constraint on {@code event_id} ensures that a replayed
 * Kafka delivery for the same event does not re-mask already-masked rows.
 * The consumer catches {@link org.springframework.dao.DataIntegrityViolationException}
 * on duplicate {@code event_id} and treats it as a success (already processed).
 *
 * <p>Rows are never updated or deleted — audit-heavy A3 immutability.
 */
@Entity
@Table(name = "pii_masking_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PiiMaskingLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "masked_at", nullable = false)
    private Instant maskedAt;

    /** JSON array string, e.g. {@code ["login_history","suspicious_events"]}. */
    @Column(name = "table_names", nullable = false, length = 512)
    private String tableNames;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public static PiiMaskingLogJpaEntity create(String eventId, String tenantId,
                                                 String accountId, Instant maskedAt,
                                                 String tableNamesJson) {
        PiiMaskingLogJpaEntity e = new PiiMaskingLogJpaEntity();
        e.eventId = eventId;
        e.tenantId = tenantId;
        e.accountId = accountId;
        e.maskedAt = maskedAt;
        e.tableNames = tableNamesJson;
        return e;
    }
}
