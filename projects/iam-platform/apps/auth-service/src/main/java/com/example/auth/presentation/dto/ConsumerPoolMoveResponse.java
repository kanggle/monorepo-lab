package com.example.auth.presentation.dto;

/**
 * TASK-BE-618 — 200 body of {@code POST /internal/auth/consumer-pool/moves}: {@code moved} = this call
 * moved the credential; {@code alreadyInPool} = it was already a pool credential (idempotent re-run).
 * Both {@code false} = the account has no credential (nothing to move).
 */
public record ConsumerPoolMoveResponse(boolean moved, boolean alreadyInPool) {
}
