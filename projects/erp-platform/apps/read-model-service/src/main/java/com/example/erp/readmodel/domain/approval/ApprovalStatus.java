package com.example.erp.readmodel.domain.approval;

/**
 * The latest projected status of an approval request (read-only, E5). Mirrors
 * the terminal-once state observed from {@code approval-service}'s transition
 * events — this is NOT a local state machine (T4 N/A): the authoritative state
 * machine + idempotency + history live in {@code approval-service}; the
 * read-model only reflects the state it observes.
 *
 * <ul>
 *   <li>{@link #SUBMITTED} — the request is in flight ({@code submitted} event).</li>
 *   <li>{@link #APPROVED} / {@link #REJECTED} / {@link #WITHDRAWN} — terminal.</li>
 * </ul>
 *
 * <p>{@code DRAFT} never appears (a draft emits no event — the first projected
 * fact is {@code SUBMITTED}).
 */
public enum ApprovalStatus {
    SUBMITTED,
    APPROVED,
    REJECTED,
    WITHDRAWN;

    /** {@code true} for the terminal states (approved / rejected / withdrawn). */
    public boolean isTerminal() {
        return this != SUBMITTED;
    }

    /**
     * Parses an approval status from the event type, returning {@code null} when
     * unknown/absent (the consumer rejects an unmappable event → invalid → DLT).
     */
    public static ApprovalStatus fromOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
