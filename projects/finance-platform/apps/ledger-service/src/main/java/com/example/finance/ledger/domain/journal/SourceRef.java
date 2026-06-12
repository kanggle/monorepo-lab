package com.example.finance.ledger.domain.journal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Provenance of a journal entry (architecture.md § Layer Structure): the
 * account-service transaction + signed event that drove the auto-journal. An
 * embeddable value object — pure semantics plus JPA mapping.
 *
 * <p>{@code sourceType} is {@code "TRANSACTION"} in the first increment;
 * {@code sourceTransactionId} keys the reversal lookup (a {@code reversed.v1}
 * references the ORIGINAL transaction); {@code sourceEventId} is the dedupe key.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class SourceRef {

    public static final String TYPE_TRANSACTION = "TRANSACTION";
    public static final String TYPE_MANUAL = "MANUAL";
    public static final String TYPE_REVALUATION = "REVALUATION";

    @Column(name = "source_type", length = 30, nullable = false)
    private String sourceType;

    @Column(name = "source_transaction_id", length = 64, nullable = false)
    private String sourceTransactionId;

    @Column(name = "source_event_id", length = 64, nullable = false)
    private String sourceEventId;

    private SourceRef(String sourceType, String sourceTransactionId, String sourceEventId) {
        this.sourceType = sourceType;
        this.sourceTransactionId = sourceTransactionId;
        this.sourceEventId = sourceEventId;
    }

    public static SourceRef ofTransaction(String sourceTransactionId, String sourceEventId) {
        Objects.requireNonNull(sourceTransactionId, "sourceTransactionId");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        return new SourceRef(TYPE_TRANSACTION, sourceTransactionId, sourceEventId);
    }

    /**
     * Provenance for an operator-initiated manual posting (5th increment,
     * TASK-FIN-BE-011). {@code sourceEventId} is the namespaced dedupe key
     * ({@code manual:{Idempotency-Key}}); {@code sourceTransactionId} carries the
     * operator {@code reference} when present, falling back to the
     * {@code sourceEventId} so the NOT-NULL column always has a value.
     */
    public static SourceRef ofManual(String reference, String sourceEventId) {
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        // sourceTransactionId is NOT NULL → fall back to sourceEventId when reference is blank
        String txnId = (reference != null && !reference.isBlank()) ? reference : sourceEventId;
        return new SourceRef(TYPE_MANUAL, txnId, sourceEventId);
    }

    /**
     * Provenance for an operator-initiated FX revaluation (9th increment,
     * TASK-FIN-BE-015 — mirrors {@link #ofManual}). {@code sourceEventId} is the
     * namespaced dedupe key ({@code reval:{Idempotency-Key}}); {@code sourceTransactionId}
     * carries the operator {@code reference} when present, falling back to the
     * {@code sourceEventId} so the NOT-NULL column always has a value. The GL/AP feed
     * sees the unrealized FX adjustment tagged {@code sourceType = "REVALUATION"}.
     */
    public static SourceRef ofRevaluation(String reference, String sourceEventId) {
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        String txnId = (reference != null && !reference.isBlank()) ? reference : sourceEventId;
        return new SourceRef(TYPE_REVALUATION, txnId, sourceEventId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceRef that)) return false;
        return Objects.equals(sourceType, that.sourceType)
                && Objects.equals(sourceTransactionId, that.sourceTransactionId)
                && Objects.equals(sourceEventId, that.sourceEventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceType, sourceTransactionId, sourceEventId);
    }
}
