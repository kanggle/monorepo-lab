package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.SellerInvitation;
import com.example.product.domain.model.SellerInvitationStatus;
import com.example.product.domain.model.SellerMember;
import com.example.product.domain.model.SellerMemberStatus;
import com.example.product.domain.model.SellerStatus;
import com.example.product.domain.repository.SellerMemberRepository;
import com.example.product.domain.tenant.TenantContext;
import com.example.product.infrastructure.persistence.entity.SellerInvitationJpaEntity;
import com.example.product.infrastructure.persistence.entity.SellerMemberJpaEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Adapter for {@link SellerMemberRepository} (TASK-MONO-752). Every query starts with the current tenant. */
@Repository
class SellerMemberRepositoryImpl implements SellerMemberRepository {

    private final SellerMemberJpaRepository memberJpa;
    private final SellerInvitationJpaRepository invitationJpa;

    SellerMemberRepositoryImpl(SellerMemberJpaRepository memberJpa, SellerInvitationJpaRepository invitationJpa) {
        this.memberJpa = memberJpa;
        this.invitationJpa = invitationJpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SellerMember> findMembers(String sellerId) {
        return memberJpa.findBySeller(TenantContext.currentTenant(), sellerId).stream()
                .map(SellerMemberJpaEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SellerMember> findMember(String sellerId, String accountId) {
        return memberJpa.findOne(TenantContext.currentTenant(), sellerId, accountId)
                .map(SellerMemberJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public SellerMember upsertActive(SellerMember member) {
        String tenantId = TenantContext.currentTenant();
        return memberJpa.findOne(tenantId, member.getSellerId(), member.getAccountId())
                .map(existing -> {
                    if (!existing.toDomain().isActive()) {
                        existing.reactivate(member.getJoinedAt());
                    }
                    return memberJpa.save(existing).toDomain();
                })
                .orElseGet(() -> memberJpa.save(SellerMemberJpaEntity.from(member, tenantId)).toDomain());
    }

    @Override
    @Transactional
    public void markRevoked(String sellerId, String accountId) {
        memberJpa.findOne(TenantContext.currentTenant(), sellerId, accountId).ifPresent(entity -> {
            entity.revoke();
            memberJpa.save(entity);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOtherActiveMembershipInActiveSeller(String accountId, String exceptSellerId) {
        return memberJpa.existsActiveElsewhere(TenantContext.currentTenant(), accountId, exceptSellerId,
                SellerMemberStatus.ACTIVE, SellerStatus.ACTIVE);
    }

    @Override
    @Transactional
    public SellerInvitation saveInvitation(SellerInvitation invitation) {
        return invitationJpa.save(SellerInvitationJpaEntity.from(invitation, TenantContext.currentTenant())).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SellerInvitation> findInvitationByTokenHash(String tokenHash) {
        return invitationJpa.findByTokenHash(TenantContext.currentTenant(), tokenHash)
                .map(SellerInvitationJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SellerInvitation> findInvitations(String sellerId) {
        return invitationJpa.findBySeller(TenantContext.currentTenant(), sellerId).stream()
                .map(SellerInvitationJpaEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean markInvitationAccepted(String invitationId, String accountId, Instant acceptedAt) {
        return invitationJpa.markAccepted(TenantContext.currentTenant(), invitationId, accountId, acceptedAt,
                SellerInvitationStatus.ACCEPTED, SellerInvitationStatus.PENDING) == 1;
    }
}
