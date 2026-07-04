package com.example.admin.domain.rbac;

/**
 * TASK-BE-477 / ADR-MONO-045 D1 — the cross-org partnership lifecycle state machine.
 *
 * <p>Transitions per the canonical matrix in
 * {@code specs/contracts/http/admin-api.md § Partnership Management → Status 전이 매트릭스}:
 *
 * <pre>
 *  current      accept        suspend           reactivate    terminate
 *  (none)  → PENDING (invite)  —                 —             —
 *  PENDING → ACTIVE            INVALID           INVALID       → TERMINATED
 *  ACTIVE    INVALID         → SUSPENDED         INVALID       → TERMINATED
 *  SUSPENDED INVALID         NO_OP (200,no evt)  → ACTIVE       → TERMINATED
 *  TERMINATED INVALID         INVALID            INVALID       NO_OP (idempotent 200)
 * </pre>
 *
 * <p>Each transition method returns a {@link Transition} verdict so the use-case can
 * distinguish: {@code APPLIED} (mutate + emit event), {@code NO_OP} (200, no event —
 * idempotent same-state), {@code INVALID} (409 {@code PARTNERSHIP_TRANSITION_INVALID}).
 * Framework-free.
 */
public enum PartnershipStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    TERMINATED;

    /** Outcome of a requested lifecycle transition. */
    public enum Transition {
        /** State changes and a lifecycle event is emitted. */
        APPLIED,
        /** Idempotent same-state request: 200, no event, no mutation. */
        NO_OP,
        /** Illegal transition → 409 PARTNERSHIP_TRANSITION_INVALID. */
        INVALID
    }

    /** {@code :accept} — only valid from PENDING → ACTIVE. */
    public Transition accept() {
        return this == PENDING ? Transition.APPLIED : Transition.INVALID;
    }

    /** {@code :suspend} — ACTIVE → SUSPENDED (APPLIED); SUSPENDED → NO_OP; else INVALID. */
    public Transition suspend() {
        if (this == ACTIVE) {
            return Transition.APPLIED;
        }
        if (this == SUSPENDED) {
            return Transition.NO_OP;
        }
        return Transition.INVALID;
    }

    /** {@code :reactivate} — only valid from SUSPENDED → ACTIVE. */
    public Transition reactivate() {
        return this == SUSPENDED ? Transition.APPLIED : Transition.INVALID;
    }

    /**
     * {@code :terminate} — PENDING/ACTIVE/SUSPENDED → TERMINATED (APPLIED, one-shot
     * cascade event); TERMINATED → NO_OP (idempotent terminal 200).
     */
    public Transition terminate() {
        return this == TERMINATED ? Transition.NO_OP : Transition.APPLIED;
    }

    /**
     * @return {@code true} iff a partnership in this status derives cross-org reach
     *         (only {@code ACTIVE}). PENDING / SUSPENDED / TERMINATED derive 0
     *         (ADR-045 D6 cascade-revoke = derivation loss).
     */
    public boolean derivesReach() {
        return this == ACTIVE;
    }
}
