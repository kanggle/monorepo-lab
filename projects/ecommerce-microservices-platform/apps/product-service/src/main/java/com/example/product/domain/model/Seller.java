package com.example.product.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Marketplace seller aggregate (ADR-MONO-030 Step 3 §3.1 — inner axis). A seller
 * is a <b>participant inside a single tenant</b>, not an isolation boundary
 * (ADR-030 D3-B rejected): the aggregate key is the composite
 * {@code (tenant_id, seller_id)}. The owning tenant is stamped at the persistence
 * boundary (mirroring {@code Product} / {@code Category}), so it is not a field of
 * this clean domain model.
 *
 * <p>v1 lifecycle is intentionally minimal — register + active status only. Seller
 * onboarding flow, settlement, and commission are out of scope (Step 4).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Seller {

    /** The per-tenant default seller every pre-marketplace product is attributed to. */
    public static final String DEFAULT_SELLER_ID = "default";

    private String sellerId;
    private String displayName;
    private SellerStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static Seller register(String sellerId, String displayName) {
        validateSellerId(sellerId);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Seller display name must not be blank");
        }
        Seller seller = new Seller();
        seller.sellerId = sellerId.trim();
        seller.displayName = displayName.trim();
        seller.status = SellerStatus.ACTIVE;
        Instant now = Instant.now();
        seller.createdAt = now;
        seller.updatedAt = now;
        return seller;
    }

    /** Convenience factory for the per-tenant default seller (D8 degradation). */
    public static Seller defaultSeller() {
        return register(DEFAULT_SELLER_ID, "Default Seller");
    }

    public static Seller reconstitute(String sellerId, String displayName, SellerStatus status,
                                      Instant createdAt, Instant updatedAt) {
        validateSellerId(sellerId);
        if (status == null) throw new IllegalArgumentException("Seller status must not be null");
        Seller seller = new Seller();
        seller.sellerId = sellerId;
        seller.displayName = displayName;
        seller.status = status;
        seller.createdAt = createdAt;
        seller.updatedAt = updatedAt;
        return seller;
    }

    public boolean isActive() {
        return status == SellerStatus.ACTIVE;
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
