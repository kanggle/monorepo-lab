package com.example.product.application.service;

import com.example.product.domain.exception.SellerInvitationAlreadyUsedException;
import com.example.product.domain.exception.SellerNotActiveException;
import com.example.product.domain.model.Seller;
import com.example.product.domain.model.SellerMember;
import com.example.product.domain.repository.SellerMemberRepository;
import com.example.product.domain.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The accept's DB half as ONE short transaction (TASK-MONO-752), run after the IAM grant — a separate bean so
 * the {@code @Transactional} proxy applies (same reason as {@link SellerLifecyclePersistence}).
 */
@Component
@RequiredArgsConstructor
class SellerMemberPersistence {

    private final SellerMemberRepository memberRepository;
    private final SellerRepository sellerRepository;

    /**
     * The seller re-checked ACTIVE (it may have been suspended while IAM answered), the invitation PENDING →
     * ACCEPTED (conditional — a concurrent second accept loses with {@link SellerInvitationAlreadyUsedException}),
     * and the member written ACTIVE. Any failure rolls all of it back.
     */
    @Transactional
    SellerMember completeAcceptance(String invitationId, String sellerId, String accountId, Instant now) {
        boolean sellerActive = sellerRepository.findById(sellerId).map(Seller::isActive).orElse(false);
        if (!sellerActive) {
            throw new SellerNotActiveException();
        }
        if (!memberRepository.markInvitationAccepted(invitationId, accountId, now)) {
            throw new SellerInvitationAlreadyUsedException();
        }
        return memberRepository.upsertActive(SellerMember.join(sellerId, accountId, now));
    }
}
