package com.example.product.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A person linked to a marketplace seller (ADR-MONO-079 D5, TASK-MONO-752). Row of
 * {@code seller_members(tenant_id, seller_id, account_id, role, status, joined_at)}; the tenant is stamped at the
 * persistence boundary, as for {@link Seller}.
 *
 * <p>{@code accountId} is the person's IAM <b>pool</b> account (the store token's {@code sub}) — not the seller's
 * machine account ({@link Seller#getAccountId()}, ADR-042 D2). One role, {@link SellerMemberRole#MEMBER}
 * (rider R4).
 *
 * <p>Status:
 * <ul>
 *   <li>{@link SellerMemberStatus#ACTIVE} — linked; IAM holds {@code consumer_site_roles(account, tenant, SELLER)}
 *       for this person, or the revocation of it has not been confirmed yet (a SUSPENDED / CLOSED seller with
 *       ACTIVE members = revocations still owed, retried by the next SUSPEND / CLOSE).</li>
 *   <li>{@link SellerMemberStatus#REVOKED} — unlinked; the role was confirmed removed, or another ACTIVE seller
 *       membership still needs it.</li>
 * </ul>
 */
public final class SellerMember {

    private final String sellerId;
    private final String accountId;
    private final SellerMemberRole role;
    private final SellerMemberStatus status;
    private final Instant joinedAt;

    private SellerMember(String sellerId, String accountId, SellerMemberRole role, SellerMemberStatus status,
                         Instant joinedAt) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("sellerId is required");
        }
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId is required");
        }
        this.sellerId = sellerId;
        this.accountId = accountId;
        this.role = Objects.requireNonNull(role, "role is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt is required");
    }

    /** A person who just accepted an invitation. */
    public static SellerMember join(String sellerId, String accountId, Instant joinedAt) {
        return new SellerMember(sellerId, accountId, SellerMemberRole.MEMBER, SellerMemberStatus.ACTIVE, joinedAt);
    }

    public static SellerMember reconstitute(String sellerId, String accountId, SellerMemberRole role,
                                            SellerMemberStatus status, Instant joinedAt) {
        return new SellerMember(sellerId, accountId, role, status, joinedAt);
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getAccountId() {
        return accountId;
    }

    public SellerMemberRole getRole() {
        return role;
    }

    public SellerMemberStatus getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public boolean isActive() {
        return status == SellerMemberStatus.ACTIVE;
    }
}
