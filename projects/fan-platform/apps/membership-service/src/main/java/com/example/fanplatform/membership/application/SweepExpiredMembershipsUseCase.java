package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.time.ClockPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Expiry sweeper use case (TASK-FAN-BE-014). Finds ACTIVE memberships whose
 * window has ended and that have not yet been expiry-notified, marks each with a
 * one-time {@code expiry_notified_at} and emits {@code fan.membership.expired.v1}.
 *
 * <p><b>Exactly-once per membership.</b> The marker mutation (dirty-checked) and
 * the outbox append both run in this one {@link Transactional} unit, so the event
 * is enqueued atomically with the marker. A later tick no longer matches the
 * {@code expiry_notified_at IS NULL} predicate, so it never re-emits.
 *
 * <p><b>Status is unchanged.</b> The stored {@code status} stays {@code ACTIVE}
 * (read-time expiry — Option B, architecture.md § Expiry Sweeper); there is no
 * stored {@code EXPIRED} transition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SweepExpiredMembershipsUseCase {

    private final MembershipRepository membershipRepository;
    private final MembershipEventPublisher eventPublisher;
    private final ClockPort clock;

    /**
     * Sweeps up to {@code batchSize} expirable memberships in one transaction.
     *
     * @return the number of memberships swept (events emitted) this invocation.
     */
    @Transactional
    public int sweep(int batchSize) {
        Instant now = clock.now();
        List<Membership> expirable = membershipRepository.findExpirable(now, batchSize);
        for (Membership membership : expirable) {
            membership.markExpiryNotified(now);
            eventPublisher.publishExpired(
                    membership.getId(), membership.getTenantId(), membership.getAccountId(),
                    membership.getTier(), membership.getValidTo(), now);
        }
        if (!expirable.isEmpty()) {
            log.info("Expiry sweep emitted {} fan.membership.expired.v1 event(s).", expirable.size());
        }
        return expirable.size();
    }
}
