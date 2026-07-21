package com.example.fanplatform.membership.application;

import com.example.common.id.UuidV7;
import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.fanplatform.membership.application.exception.IdempotencyKeyConflictException;
import com.example.fanplatform.membership.application.exception.MembershipNotFoundException;
import com.example.fanplatform.membership.application.exception.PaymentDeclinedException;
import com.example.fanplatform.membership.domain.idempotency.IdempotencyKey;
import com.example.fanplatform.membership.domain.idempotency.IdempotencyKeyRepository;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.payment.PaymentGatewayPort;
import com.example.fanplatform.membership.domain.time.ClockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Subscribe (Idempotency-Key + PG mock authorize → ACTIVE).
 *
 * <p>Flow (architecture.md § PG Mock Boundary, § State Machine):
 * <ol>
 *   <li>Look up {@code idempotency_keys} for (tenant, account, key).
 *       <ul>
 *         <li>Found + same fingerprint → return the stored membership (no
 *             re-authorization, no duplicate row).</li>
 *         <li>Found + different fingerprint → 409 IDEMPOTENCY_KEY_CONFLICT.</li>
 *       </ul></li>
 *   <li>New key → PG authorize. Decline → NO row, 422 PAYMENT_DECLINED.</li>
 *   <li>Approve → create ACTIVE membership (validFrom = now micros, validTo =
 *       validFrom + planMonths) + idempotency row + activated outbox event, all
 *       in ONE transaction.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SubscribeUseCase {

    /** Synthetic per-month price in minor units (mock — not externally exposed). */
    private static final long PRICE_PER_MONTH_MINOR = 9_900L;

    private final MembershipRepository membershipRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final PaymentGatewayPort paymentGateway;
    private final MembershipEventPublisher eventPublisher;
    private final ClockPort clock;

    @Transactional
    public MembershipView execute(SubscribeCommand cmd) {
        ActorContext actor = cmd.actor();
        String tenantId = actor.tenantId();
        String accountId = actor.accountId();
        String fingerprint = fingerprint(cmd.tier(), cmd.planMonths(), cmd.paymentToken());

        // 1) Idempotency check ---------------------------------------------------
        Optional<IdempotencyKey> existing =
                idempotencyKeyRepository.find(tenantId, accountId, cmd.idempotencyKey());
        if (existing.isPresent()) {
            IdempotencyKey key = existing.get();
            if (!key.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyKeyConflictException(cmd.idempotencyKey());
            }
            // Same key + same payload → replay the stored result (no re-auth, no dup).
            Membership stored = membershipRepository
                    .findByIdScoped(key.getMembershipId(), accountId, tenantId)
                    .orElseThrow(() -> new MembershipNotFoundException(key.getMembershipId()));
            return MembershipView.from(stored, clock.now());
        }

        // 2) PG authorize --------------------------------------------------------
        long amountMinor = PRICE_PER_MONTH_MINOR * cmd.planMonths();
        PaymentGatewayPort.PaymentResult result = paymentGateway.authorize(
                amountMinor, cmd.planMonths(), cmd.paymentToken(), cmd.idempotencyKey());
        if (!result.approved()) {
            // Decline → NO row created, NO event.
            throw new PaymentDeclinedException();
        }

        // 3) Create ACTIVE membership + idempotency row + activated event --------
        Instant now = clock.now();
        Instant validFrom = now;
        Instant validTo = validFrom.plus(cmd.planMonths() * 30L, ChronoUnit.DAYS);
        String membershipId = UuidV7.randomString();

        Membership membership = Membership.activate(
                membershipId, tenantId, accountId, cmd.tier(),
                validFrom, validTo, cmd.planMonths(), result.paymentRef(), now);
        Membership saved = membershipRepository.save(membership);

        idempotencyKeyRepository.save(IdempotencyKey.create(
                tenantId, accountId, cmd.idempotencyKey(), fingerprint, membershipId, now));

        eventPublisher.publishActivated(
                saved.getId(), saved.getTenantId(), saved.getAccountId(),
                saved.getTier(), saved.getPlanMonths(),
                saved.getValidFrom(), saved.getValidTo(), now);

        return MembershipView.from(saved, now);
    }

    /**
     * Stable hash of the subscribe payload — a mismatch on replay means the same
     * key is being reused for a different request (409). Excludes the
     * idempotency key itself (which is the lookup key).
     */
    private static String fingerprint(MembershipTier tier, int planMonths, String paymentToken) {
        String raw = tier.name() + "|" + planMonths + "|" + (paymentToken == null ? "" : paymentToken);
        return Sha256.hex(raw);
    }
}
