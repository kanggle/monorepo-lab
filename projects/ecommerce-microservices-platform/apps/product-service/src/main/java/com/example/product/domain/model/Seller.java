package com.example.product.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Marketplace seller aggregate (ADR-MONO-030 Step 3 §3.1 — inner axis; Step 4 facet f,
 * ADR-MONO-042). A seller is a <b>participant inside a single tenant</b>, not an
 * isolation boundary (ADR-030 D3-B rejected): the aggregate key is the composite
 * {@code (tenant_id, seller_id)}. The owning tenant is stamped at the persistence
 * boundary (mirroring {@code Product} / {@code Category}), so it is not a field of
 * this clean domain model.
 *
 * <p><b>Lifecycle (ADR-042 D1/D3/D4).</b> A seller is now a real provisioned principal,
 * not a trusted-claim shim. Onboarding mints an IAM seller-operator account fail-soft:
 *
 * <pre>
 *   register()  ─────────────►  PENDING_PROVISIONING
 *                                     │  markProvisioned(accountId, identityId)  (D3 success)
 *                                     ▼
 *                                  ACTIVE  ──── suspend() ───►  SUSPENDED  (D4: lock account)
 *                                     │  └──── close()   ───►  CLOSED     (D4: deactivate account)
 * </pre>
 *
 * The per-tenant {@code default} seller (D8 degrade anchor) is born {@code ACTIVE} and is
 * never provisioned. Legacy pre-ADR-042 sellers backfill to {@code ACTIVE} with null
 * account/identity (behavior-unchanged). {@code accountId}/{@code identityId} are nullable
 * until provisioned (D3 fail-soft) and, once set, are never silently overwritten.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Seller {

    /** The per-tenant default seller every pre-marketplace product is attributed to. */
    public static final String DEFAULT_SELLER_ID = "default";

    private String sellerId;
    private String displayName;
    private SellerStatus status;
    /** The backing IAM seller-operator account id (ADR-042 D2). Null until provisioned. */
    private String accountId;
    /** The born-unified central identity id (ADR-042 D5). Null until provisioned. */
    private String identityId;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Onboards a new marketplace seller (ADR-042 D3). The seller is born
     * {@link SellerStatus#PENDING_PROVISIONING} — it becomes operable only after
     * {@link #markProvisioned(String, String)} succeeds. Onboarding never blocks on
     * IAM infra (fail-soft): a seller that cannot be provisioned stays PENDING.
     */
    public static Seller register(String sellerId, String displayName) {
        validateSellerId(sellerId);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Seller display name must not be blank");
        }
        Seller seller = new Seller();
        seller.sellerId = sellerId.trim();
        seller.displayName = displayName.trim();
        seller.status = SellerStatus.PENDING_PROVISIONING;
        Instant now = Instant.now();
        seller.createdAt = now;
        seller.updatedAt = now;
        return seller;
    }

    /**
     * Convenience factory for the per-tenant default seller (D8 degradation). The
     * default seller is the standalone single-store anchor — born {@code ACTIVE} and
     * never IAM-provisioned (it is not a real marketplace seller principal).
     */
    public static Seller defaultSeller() {
        Seller seller = new Seller();
        seller.sellerId = DEFAULT_SELLER_ID;
        seller.displayName = "Default Seller";
        seller.status = SellerStatus.ACTIVE;
        Instant now = Instant.now();
        seller.createdAt = now;
        seller.updatedAt = now;
        return seller;
    }

    public static Seller reconstitute(String sellerId, String displayName, SellerStatus status,
                                      String accountId, String identityId,
                                      Instant createdAt, Instant updatedAt) {
        validateSellerId(sellerId);
        if (status == null) throw new IllegalArgumentException("Seller status must not be null");
        Seller seller = new Seller();
        seller.sellerId = sellerId;
        seller.displayName = displayName;
        seller.status = status;
        seller.accountId = accountId;
        seller.identityId = identityId;
        seller.createdAt = createdAt;
        seller.updatedAt = updatedAt;
        return seller;
    }

    /**
     * Marks the seller provisioned (ADR-042 D3 success): stores the minted
     * {@code accountId}/{@code identityId} and transitions PENDING_PROVISIONING → ACTIVE.
     * Idempotent + no-overwrite (AC-4 / F2): if already ACTIVE this is a no-op and a
     * stored non-null account/identity is never replaced. A {@code null} minted id does
     * not overwrite an existing non-null one (re-provision only fills nulls).
     */
    public void markProvisioned(String accountId, String identityId) {
        if (status == SellerStatus.CLOSED) {
            throw new IllegalStateException("Cannot provision a CLOSED seller: " + sellerId);
        }
        if (this.accountId == null && accountId != null) {
            this.accountId = accountId;
        }
        if (this.identityId == null && identityId != null) {
            this.identityId = identityId;
        }
        // Become ACTIVE only once the account is backed (identity is born-unified best-effort).
        if (this.accountId != null && status == SellerStatus.PENDING_PROVISIONING) {
            this.status = SellerStatus.ACTIVE;
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Operator suspension (ADR-042 D4): ACTIVE → SUSPENDED. Idempotent (re-suspending a
     * SUSPENDED seller is a no-op returning {@code false} = no account-lock call needed).
     *
     * @return {@code true} if a transition occurred (caller should lock the backing account).
     */
    public boolean suspend() {
        if (status == SellerStatus.SUSPENDED) {
            return false;
        }
        if (status == SellerStatus.CLOSED) {
            throw new IllegalStateException("Cannot suspend a CLOSED seller: " + sellerId);
        }
        this.status = SellerStatus.SUSPENDED;
        this.updatedAt = Instant.now();
        return true;
    }

    /**
     * Operator closure (ADR-042 D4): ACTIVE/SUSPENDED/PENDING → CLOSED (terminal).
     * Idempotent (re-closing a CLOSED seller is a no-op returning {@code false}).
     *
     * @return {@code true} if a transition occurred (caller should deactivate the account).
     */
    public boolean close() {
        if (status == SellerStatus.CLOSED) {
            return false;
        }
        this.status = SellerStatus.CLOSED;
        this.updatedAt = Instant.now();
        return true;
    }

    public boolean isActive() {
        return status == SellerStatus.ACTIVE;
    }

    public boolean isPendingProvisioning() {
        return status == SellerStatus.PENDING_PROVISIONING;
    }

    /** Whether a backing IAM account exists (deactivation is null-safe / net-zero, D4). */
    public boolean hasBackingAccount() {
        return accountId != null && !accountId.isBlank();
    }

    private static void validateSellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("Seller id must not be blank");
        }
        if (sellerId.trim().length() > 64) {
            throw new IllegalArgumentException("Seller id must not exceed 64 characters");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Seller s)) return false;
        return sellerId != null && sellerId.equals(s.sellerId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
