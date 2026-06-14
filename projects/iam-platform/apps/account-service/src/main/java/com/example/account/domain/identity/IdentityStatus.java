package com.example.account.domain.identity;

/**
 * Lifecycle status of a central {@link Identity} (the per-person registry record,
 * ADR-MONO-034 U1-A). Independent of {@code AccountStatus} — an identity is the
 * person; the consumer account (and, once linked, the operator extension) carry
 * their own per-principal status.
 */
public enum IdentityStatus {
    ACTIVE,
    INACTIVE
}
