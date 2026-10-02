package com.example.product.application.service;

import com.example.product.application.port.SellerSiteRoleGateway;
import com.example.product.domain.exception.SellerInvitationEmailMismatchException;
import com.example.product.domain.model.Seller;
import com.example.product.domain.model.SellerInvitation;
import com.example.product.domain.model.SellerInvitationStatus;
import com.example.product.domain.model.SellerMember;
import com.example.product.domain.model.SellerMemberStatus;
import com.example.product.domain.repository.SellerMemberRepository;
import com.example.product.domain.repository.SellerRepository;
import com.example.product.domain.tenant.TenantContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * TASK-MONO-752 test support — tenant-aware in-memory sellers, members, invitations and an IAM site-role fake,
 * so the seller-member rules run through the REAL {@link SellerMemberService}, {@link SellerMemberPersistence},
 * {@link RegisterSellerService} and {@code AccountStatusChangedSellerConsumer} without Docker.
 *
 * <p>The IAM fake models what account-service decides: it holds each pool account's email and the set of
 * {@code (tenant, account)} pairs holding {@code SELLER}; {@code grant} refuses when the invited email is not the
 * account's email (consumer-site-roles.md rule 4). {@link #failRevokes} makes revokes fail (IAM down).
 */
final class InMemorySellerStores {

    final Map<String, Seller> sellers = new LinkedHashMap<>();          // key tenant|sellerId
    final Map<String, SellerMember> members = new LinkedHashMap<>();    // key tenant|sellerId|accountId
    final Map<String, SellerInvitation> invitations = new LinkedHashMap<>(); // key tenant|id
    final Map<String, String> iamEmails = new HashMap<>();               // accountId -> email
    final Set<String> sellerRoles = new HashSet<>();                     // tenant|accountId
    final List<String> revokeCalls = new ArrayList<>();
    boolean failRevokes;
    /** Runs right after a successful grant — simulates a race (e.g. the seller suspended while IAM answered). */
    Runnable afterGrant = () -> { };

    private static String t() {
        return TenantContext.currentTenant();
    }

    final SellerRepository sellerRepository = new SellerRepository() {
        @Override public Seller save(Seller s) { sellers.putIfAbsent(t() + "|" + s.getSellerId(), s); return sellers.get(t() + "|" + s.getSellerId()); }
        @Override public Seller update(Seller s) { sellers.put(t() + "|" + s.getSellerId(), s); return s; }
        @Override public Optional<Seller> findById(String id) { return Optional.ofNullable(sellers.get(t() + "|" + id)); }
        @Override public Optional<Seller> findByAccountId(String accountId) {
            return sellers.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(t() + "|"))
                    .map(Map.Entry::getValue)
                    .filter(s -> accountId.equals(s.getAccountId()))
                    .findFirst();
        }
        @Override public boolean existsById(String id) { return sellers.containsKey(t() + "|" + id); }
        @Override public Seller ensureDefaultSeller() { throw new UnsupportedOperationException(); }
        @Override public long countAllForTenant() { return 0; }
        @Override public long countCreatedBetween(Instant from, Instant to) { return 0; }
    };

    final SellerMemberRepository memberRepository = new SellerMemberRepository() {
        @Override public List<SellerMember> findMembers(String sellerId) {
            return members.entrySet().stream().filter(e -> e.getKey().startsWith(t() + "|" + sellerId + "|"))
                    .map(Map.Entry::getValue).sorted(Comparator.comparing(SellerMember::getJoinedAt)).toList();
        }
        @Override public Optional<SellerMember> findMember(String sellerId, String accountId) {
            return Optional.ofNullable(members.get(t() + "|" + sellerId + "|" + accountId));
        }
        @Override public SellerMember upsertActive(SellerMember m) {
            String key = t() + "|" + m.getSellerId() + "|" + m.getAccountId();
            SellerMember existing = members.get(key);
            if (existing == null || !existing.isActive()) {
                members.put(key, m);
            }
            return members.get(key);
        }
        @Override public void markRevoked(String sellerId, String accountId) {
            String key = t() + "|" + sellerId + "|" + accountId;
            SellerMember m = members.get(key);
            if (m != null) {
                members.put(key, SellerMember.reconstitute(sellerId, accountId, m.getRole(),
                        SellerMemberStatus.REVOKED, m.getJoinedAt()));
            }
        }
        @Override public boolean hasOtherActiveMembershipInActiveSeller(String accountId, String exceptSellerId) {
            return members.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(t() + "|"))
                    .map(Map.Entry::getValue)
                    .filter(m -> m.getAccountId().equals(accountId) && m.isActive()
                            && !m.getSellerId().equals(exceptSellerId))
                    .anyMatch(m -> Optional.ofNullable(sellers.get(t() + "|" + m.getSellerId()))
                            .map(Seller::isActive).orElse(false));
        }
        @Override public SellerInvitation saveInvitation(SellerInvitation i) {
            invitations.put(t() + "|" + i.getId(), i);
            return i;
        }
        @Override public Optional<SellerInvitation> findInvitationByTokenHash(String hash) {
            return invitations.entrySet().stream().filter(e -> e.getKey().startsWith(t() + "|"))
                    .map(Map.Entry::getValue).filter(i -> i.getTokenHash().equals(hash)).findFirst();
        }
        @Override public List<SellerInvitation> findInvitations(String sellerId) {
            return invitations.entrySet().stream().filter(e -> e.getKey().startsWith(t() + "|"))
                    .map(Map.Entry::getValue).filter(i -> i.getSellerId().equals(sellerId)).toList();
        }
        @Override public boolean markInvitationAccepted(String id, String accountId, Instant at) {
            SellerInvitation i = invitations.get(t() + "|" + id);
            if (i == null || i.getStatus() != SellerInvitationStatus.PENDING) {
                return false;
            }
            invitations.put(t() + "|" + id, SellerInvitation.reconstitute(i.getId(), i.getSellerId(), i.getEmail(),
                    i.getTokenHash(), SellerInvitationStatus.ACCEPTED, i.getExpiresAt(), i.getInvitedBy(),
                    i.getCreatedAt(), at, accountId));
            return true;
        }
    };

    final SellerSiteRoleGateway gateway = new SellerSiteRoleGateway() {
        @Override public void grant(String tenant, String accountId, String invitedEmail) {
            String email = iamEmails.get(accountId);
            if (email == null || !email.equalsIgnoreCase(invitedEmail.trim())) {
                throw new SellerInvitationEmailMismatchException();
            }
            sellerRoles.add(tenant + "|" + accountId);
            afterGrant.run();
        }
        @Override public boolean revoke(String tenant, String accountId) {
            revokeCalls.add(tenant + "|" + accountId);
            if (failRevokes) {
                return false;
            }
            sellerRoles.remove(tenant + "|" + accountId);
            return true;
        }
    };

    SellerMemberService memberService(java.time.Clock clock) {
        SellerMemberPersistence persistence = new SellerMemberPersistence(memberRepository, sellerRepository);
        return new SellerMemberService(sellerRepository, memberRepository, persistence, gateway, clock,
                java.time.Duration.ofDays(7));
    }

    void putSeller(String tenant, Seller seller) {
        sellers.put(tenant + "|" + seller.getSellerId(), seller);
    }

    Seller seller(String tenant, String sellerId) {
        return sellers.get(tenant + "|" + sellerId);
    }

    SellerMember member(String tenant, String sellerId, String accountId) {
        return members.get(tenant + "|" + sellerId + "|" + accountId);
    }
}
