package com.example.product.domain.model;

/**
 * Seller lifecycle status (ADR-MONO-030 Step 3 §3.1 + Step 4 facet f, ADR-MONO-042).
 *
 * <p>A seller is now a real provisioned principal (ADR-042): onboarding mints an IAM
 * seller-operator account fail-soft, so the seller is born {@link #PENDING_PROVISIONING}
 * and transitions to {@link #ACTIVE} only once the backing account/identity are
 * provisioned. Operators can {@link #SUSPENDED} or {@link #CLOSED} a seller, which
 * deactivates the backing account.
 *
 * <ul>
 *   <li>{@code PENDING_PROVISIONING} — born here on onboarding; not yet operable (no
 *       seller-scope authority until ACTIVE). Stays here if IAM provisioning is
 *       unavailable (D3 fail-soft) and is retryable.</li>
 *   <li>{@code ACTIVE} — provisioned + operable. The per-tenant {@code default} seller
 *       is always ACTIVE (D8 standalone degrade anchor, never provisioned). Legacy
 *       pre-ADR-042 sellers backfill here (null account/identity, behavior-unchanged).</li>
 *   <li>{@code SUSPENDED} — operator-suspended; the backing account is locked (D4).</li>
 *   <li>{@code CLOSED} — operator-closed; the backing account is deactivated (D4).</li>
 * </ul>
 */
public enum SellerStatus {
    PENDING_PROVISIONING,
    ACTIVE,
    SUSPENDED,
    CLOSED
}
