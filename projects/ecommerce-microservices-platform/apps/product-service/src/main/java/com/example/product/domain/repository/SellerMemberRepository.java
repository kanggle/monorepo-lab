package com.example.product.domain.repository;

import com.example.product.domain.model.SellerInvitation;
import com.example.product.domain.model.SellerMember;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for seller members and their invitations (ADR-MONO-079 D5, TASK-MONO-752). Like
 * {@link SellerRepository}, every operation is scoped to the current tenant ({@code TenantContext}).
 */
public interface SellerMemberRepository {

    /** Members of one seller, by {@code joined_at} ascending. */
    List<SellerMember> findMembers(String sellerId);

    Optional<SellerMember> findMember(String sellerId, String accountId);

    /** Inserts the member, or re-activates an existing (REVOKED) row of the same person with a new join time. */
    SellerMember upsertActive(SellerMember member);

    /** Marks one member REVOKED. */
    void markRevoked(String sellerId, String accountId);

    /**
     * Whether {@code accountId} is still an ACTIVE member of an ACTIVE seller other than {@code exceptSellerId}
     * in this tenant — the store {@code SELLER} role is one per site, so it must stay while such a membership
     * remains.
     */
    boolean hasOtherActiveMembershipInActiveSeller(String accountId, String exceptSellerId);

    SellerInvitation saveInvitation(SellerInvitation invitation);

    Optional<SellerInvitation> findInvitationByTokenHash(String tokenHash);

    /** Invitations of one seller, newest first. */
    List<SellerInvitation> findInvitations(String sellerId);

    /**
     * PENDING → ACCEPTED, only if still PENDING (single use under concurrency).
     *
     * @return {@code true} iff this call made the transition
     */
    boolean markInvitationAccepted(String invitationId, String accountId, Instant acceptedAt);
}
