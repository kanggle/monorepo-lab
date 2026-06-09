package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.fanplatform.membership.application.exception.MembershipNotFoundException;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.time.ClockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Cancel a membership: ACTIVE → CANCELED (terminal). A cancel of an
 * already-CANCELED membership is an <strong>idempotent no-op</strong> — returns
 * the membership unchanged and emits NO new event (architecture.md § State
 * Machine). Unknown / cross-account / cross-tenant id → 404.
 */
@Service
@RequiredArgsConstructor
public class CancelMembershipUseCase {

    private final MembershipRepository membershipRepository;
    private final MembershipEventPublisher eventPublisher;
    private final ClockPort clock;

    @Transactional
    public MembershipView execute(String membershipId, ActorContext actor, String reason) {
        Membership membership = membershipRepository
                .findByIdScoped(membershipId, actor.accountId(), actor.tenantId())
                .orElseThrow(() -> new MembershipNotFoundException(membershipId));

        // Idempotent no-op: already CANCELED → return unchanged, emit nothing.
        if (membership.isCanceled()) {
            return MembershipView.from(membership, clock.now());
        }

        Instant now = clock.now();
        membership.cancel(now);
        Membership saved = membershipRepository.save(membership);

        eventPublisher.publishCanceled(
                saved.getId(), saved.getTenantId(), saved.getAccountId(),
                saved.getTier(), reason, saved.getCanceledAt(), now);

        return MembershipView.from(saved, now);
    }
}
