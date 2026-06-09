package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.application.exception.MembershipNotFoundException;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.time.ClockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fetches a single membership by id, scoped to the caller (account + tenant).
 * Missing / cross-account / cross-tenant → 404 (existence not leaked).
 */
@Service
@RequiredArgsConstructor
public class GetMembershipUseCase {

    private final MembershipRepository membershipRepository;
    private final ClockPort clock;

    @Transactional(readOnly = true)
    public MembershipView execute(String membershipId, ActorContext actor) {
        Membership membership = membershipRepository
                .findByIdScoped(membershipId, actor.accountId(), actor.tenantId())
                .orElseThrow(() -> new MembershipNotFoundException(membershipId));
        return MembershipView.from(membership, clock.now());
    }
}
