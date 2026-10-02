package com.example.product.application.service;

import com.example.product.application.port.SellerSiteRoleGateway;
import com.example.product.domain.exception.SellerInvitationAlreadyUsedException;
import com.example.product.domain.exception.SellerInvitationNotFoundException;
import com.example.product.domain.exception.SellerNotActiveException;
import com.example.product.domain.exception.SellerNotFoundException;
import com.example.product.domain.model.Seller;
import com.example.product.domain.model.SellerInvitation;
import com.example.product.domain.model.SellerMember;
import com.example.product.domain.repository.SellerMemberRepository;
import com.example.product.domain.repository.SellerRepository;
import com.example.product.domain.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Seller members (ADR-MONO-079 D5 · rider R4, TASK-MONO-752; product-api.md § Seller members): an operator
 * invites a person by email, the person accepts <b>while logged in to the store</b>, and IAM then gives that
 * person's pool account the store site role {@code SELLER}. A seller's suspension / closure takes the role
 * back — it never locks the person.
 *
 * <p><b>Transaction boundary</b> — like {@link RegisterSellerService}: no IAM HTTP call runs inside a DB
 * transaction. The accept's two writes (invitation ACCEPTED + member ACTIVE) are one short transaction in
 * {@link SellerMemberPersistence}, after the IAM grant.
 *
 * <p><b>Who decides what</b>: this service owns «which invitation, which seller, which members, does another
 * seller still need the role». IAM owns «is the logged-in account the invited email's account» — the invitation
 * email is passed to IAM ({@code expectedEmail}) and compared there, because only IAM knows the account's email
 * (the access token's {@code email} claim depends on the requested scope and is not a reliable input).
 */
@Slf4j
@Service
public class SellerMemberService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final SellerRepository sellerRepository;
    private final SellerMemberRepository memberRepository;
    private final SellerMemberPersistence persistence;
    private final SellerSiteRoleGateway siteRoleGateway;
    private final Clock clock;
    private final Duration invitationTtl;

    @Autowired
    public SellerMemberService(SellerRepository sellerRepository,
                               SellerMemberRepository memberRepository,
                               SellerMemberPersistence persistence,
                               SellerSiteRoleGateway siteRoleGateway,
                               Clock clock,
                               @Value("${seller.invitation.ttl:P7D}") Duration invitationTtl) {
        this.sellerRepository = sellerRepository;
        this.memberRepository = memberRepository;
        this.persistence = persistence;
        this.siteRoleGateway = siteRoleGateway;
        this.clock = clock;
        this.invitationTtl = invitationTtl;
    }

    /** What the operator gets back once: the invitation and its raw token (only its hash is stored). */
    public record IssuedInvitation(SellerInvitation invitation, String token) {
    }

    /** A seller's members and invitations (operator read). */
    public record SellerMembersView(List<SellerMember> members, List<SellerInvitation> invitations, Instant asOf) {
    }

    /** Outcome of revoking a seller's members' role: how many were revoked and how many are still owed. */
    public record RevocationOutcome(int revoked, int pending) {
    }

    public IssuedInvitation invite(String sellerId, String email, String invitedBy) {
        Seller seller = sellerRepository.findById(sellerId).orElseThrow(() -> new SellerNotFoundException(sellerId));
        if (!seller.isActive()) {
            throw new SellerNotActiveException();
        }
        String token = newToken();
        SellerInvitation invitation = memberRepository.saveInvitation(SellerInvitation.issue(
                sellerId, email, sha256Hex(token), invitedBy, clock.instant(), invitationTtl));
        log.info("seller invitation issued tenant={} seller={} invitation={}",
                TenantContext.currentTenant(), sellerId, invitation.getId());
        return new IssuedInvitation(invitation, token);
    }

    public SellerMembersView list(String sellerId) {
        if (sellerRepository.findById(sellerId).isEmpty()) {
            throw new SellerNotFoundException(sellerId);
        }
        return new SellerMembersView(memberRepository.findMembers(sellerId), memberRepository.findInvitations(sellerId),
                clock.instant());
    }

    /**
     * The invited person accepts (product-api.md § accept — order of checks). {@code accountId} is the gateway's
     * {@code X-User-Id} (the store token's {@code sub}); nothing in the request body names an account.
     */
    public SellerMember accept(String token, String accountId) {
        String tenant = TenantContext.currentTenant();
        if (token == null || token.isBlank()) {
            throw new SellerInvitationNotFoundException();
        }
        SellerInvitation invitation = memberRepository.findInvitationByTokenHash(sha256Hex(token))
                .orElseThrow(SellerInvitationNotFoundException::new);
        Instant now = clock.instant();
        String sellerId = invitation.getSellerId();

        if (invitation.alreadyAcceptedBy(accountId, now)) {
            // The same person submitting twice: answer with what the first accept made.
            return memberRepository.findMember(sellerId, accountId)
                    .filter(SellerMember::isActive)
                    .orElseThrow(SellerInvitationAlreadyUsedException::new);
        }
        Seller seller = sellerRepository.findById(sellerId).orElseThrow(SellerInvitationNotFoundException::new);
        if (!seller.isActive()) {
            throw new SellerNotActiveException();
        }

        // 🔴 The authorization step — fail-closed. IAM refuses unless the logged-in pool account's email is the
        //    invitation's email and the account is an ACTIVE store member. Nothing is written here before it says yes.
        siteRoleGateway.grant(tenant, accountId, invitation.getEmail());

        try {
            SellerMember member = persistence.completeAcceptance(invitation.getId(), sellerId, accountId, now);
            log.info("seller member joined tenant={} seller={} account={}", tenant, sellerId, accountId);
            return member;
        } catch (RuntimeException e) {
            Optional<SellerMember> existing = memberRepository.findMember(sellerId, accountId).filter(SellerMember::isActive);
            if (existing.isPresent() && e instanceof SellerInvitationAlreadyUsedException) {
                return existing.get(); // a concurrent double submit by the same person won the race
            }
            // The role was granted but the link was not written: take it back unless it is legitimately held.
            if (existing.isEmpty() && !memberRepository.hasOtherActiveMembershipInActiveSeller(accountId, sellerId)) {
                boolean revoked = siteRoleGateway.revoke(tenant, accountId);
                log.warn("seller accept failed after IAM grant; compensating revoke={} tenant={} seller={} account={}: {}",
                        revoked, tenant, sellerId, accountId, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * ADR-MONO-079 D5 — a SUSPENDED / CLOSED seller's members lose the store {@code SELLER} site role. Runs for
     * every ACTIVE member: a member still ACTIVE in another ACTIVE seller keeps the role (one role per site) and is
     * just marked REVOKED here; otherwise IAM revokes it and only a confirmed revoke marks the row REVOKED. A member
     * whose revoke failed stays ACTIVE and is retried by the next call (the operator re-sends SUSPEND / CLOSE).
     * 🔴 Never a lock: the person keeps {@code CUSTOMER} and the fan site.
     */
    public RevocationOutcome revokeMemberRoles(String sellerId) {
        String tenant = TenantContext.currentTenant();
        int revoked = 0;
        int pending = 0;
        for (SellerMember member : memberRepository.findMembers(sellerId)) {
            if (!member.isActive()) {
                continue;
            }
            String accountId = member.getAccountId();
            boolean stillNeeded = memberRepository.hasOtherActiveMembershipInActiveSeller(accountId, sellerId);
            if (stillNeeded || siteRoleGateway.revoke(tenant, accountId)) {
                memberRepository.markRevoked(sellerId, accountId);
                revoked++;
            } else {
                pending++;
            }
        }
        if (revoked + pending > 0) {
            log.info("seller member roles revoked tenant={} seller={} revoked={} pending={}",
                    tenant, sellerId, revoked, pending);
        }
        return new RevocationOutcome(revoked, pending);
    }

    static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
