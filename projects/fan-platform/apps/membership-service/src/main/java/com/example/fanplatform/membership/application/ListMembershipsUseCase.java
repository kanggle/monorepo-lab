package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.time.ClockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Lists the caller's memberships (account + tenant scoped), newest window first.
 * Cross-tenant queries return an empty list — existence is not leaked.
 */
@Service
@RequiredArgsConstructor
public class ListMembershipsUseCase {

    private final MembershipRepository membershipRepository;
    private final ClockPort clock;

    @Transactional(readOnly = true)
    public List<MembershipView> execute(ActorContext actor) {
        Instant now = clock.now();
        return membershipRepository.findByAccount(actor.accountId(), actor.tenantId()).stream()
                .map(m -> MembershipView.from(m, now))
                .toList();
    }
}
