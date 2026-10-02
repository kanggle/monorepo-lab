package com.example.product.domain.model;

import com.example.product.domain.exception.SellerInvitationAlreadyUsedException;
import com.example.product.domain.exception.SellerInvitationExpiredException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * An operator's invitation of a person, by email, to become a member of a seller (ADR-MONO-079 D5,
 * TASK-MONO-752). Row of {@code seller_member_invitations}; the tenant is stamped at the persistence boundary.
 *
 * <p>🔴 The invitation is <b>not</b> an identity. Holding its token proves only that someone received the link;
 * the link is accepted by a person logged in to the store, and IAM checks that the logged-in account's email is
 * {@link #getEmail()} (ADR-MONO-034 § 1.3 — an email match alone links nothing). Only the SHA-256 of the token
 * is kept ({@link #getTokenHash()}).
 *
 * <p>Single use ({@link SellerInvitationStatus}) and time-bound ({@link #getExpiresAt()}).
 */
public final class SellerInvitation {

    private final String id;
    private final String sellerId;
    private final String email;
    private final String tokenHash;
    private final SellerInvitationStatus status;
    private final Instant expiresAt;
    private final String invitedBy;
    private final Instant createdAt;
    private final Instant acceptedAt;
    private final String acceptedAccountId;

    private SellerInvitation(String id, String sellerId, String email, String tokenHash,
                             SellerInvitationStatus status, Instant expiresAt, String invitedBy,
                             Instant createdAt, Instant acceptedAt, String acceptedAccountId) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId is required");
        this.email = Objects.requireNonNull(email, "email is required");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
        this.invitedBy = invitedBy;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.acceptedAt = acceptedAt;
        this.acceptedAccountId = acceptedAccountId;
    }

    /** A fresh PENDING invitation; the email is stored trimmed and lower-cased. */
    public static SellerInvitation issue(String sellerId, String email, String tokenHash, String invitedBy,
                                         Instant now, Duration ttl) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty() || !normalized.contains("@")) {
            throw new IllegalArgumentException("A valid email is required");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Invitation lifetime must be positive");
        }
        return new SellerInvitation(UUID.randomUUID().toString(), sellerId, normalized, tokenHash,
                SellerInvitationStatus.PENDING, now.plus(ttl), invitedBy, now, null, null);
    }

    public static SellerInvitation reconstitute(String id, String sellerId, String email, String tokenHash,
                                                SellerInvitationStatus status, Instant expiresAt, String invitedBy,
                                                Instant createdAt, Instant acceptedAt, String acceptedAccountId) {
        return new SellerInvitation(id, sellerId, email, tokenHash, status, expiresAt, invitedBy, createdAt,
                acceptedAt, acceptedAccountId);
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether {@code accountId} may go on to accept this invitation at {@code now}.
     *
     * @return {@code true} when the same account already accepted it (a double submit — the caller answers with
     *         the existing membership), {@code false} when it is PENDING and in date (the caller proceeds)
     * @throws SellerInvitationAlreadyUsedException accepted by another account (single use)
     * @throws SellerInvitationExpiredException     PENDING but past {@link #getExpiresAt()}
     */
    public boolean alreadyAcceptedBy(String accountId, Instant now) {
        if (status == SellerInvitationStatus.ACCEPTED) {
            if (accountId != null && accountId.equals(acceptedAccountId)) {
                return true;
            }
            throw new SellerInvitationAlreadyUsedException();
        }
        if (isExpired(now)) {
            throw new SellerInvitationExpiredException();
        }
        return false;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public String getId() {
        return id;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getEmail() {
        return email;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public SellerInvitationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getInvitedBy() {
        return invitedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public String getAcceptedAccountId() {
        return acceptedAccountId;
    }
}
