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
import com.example.fanplatform.membership.domain.time.ClockPort;
import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PaymentVerificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Subscribe (Idempotency-Key + PG authorize/verify → ACTIVE), with tier-aware
 * pricing and MEMBERS_ONLY → PREMIUM upgrade proration (TASK-FAN-BE-032).
 *
 * <p>Flow (architecture.md § PG Boundary, § State Machine):
 * <ol>
 *   <li>Idempotency lookup — same key+payload replays the stored result (no
 *       re-charge, no re-supersede); a different payload → 409.</li>
 *   <li><b>Upgrade assessment</b> ({@link UpgradeQuoter}) — a PREMIUM subscribe while
 *       an ACTIVE, in-window MEMBERS_ONLY membership is held prorates the charge
 *       ({@code 17,900×planMonths − remainingDays×7,900/30}, floored at 0); the SAME
 *       assessment backs the upgrade-quote endpoint so the client-paid amount equals
 *       what is re-verified here. Otherwise the plain tier list price.</li>
 *   <li>PG authorize the computed amount (skipped for a 0-won upgrade — PortOne
 *       cannot process a zero payment). Decline → NO row, 422.</li>
 *   <li>Approve → (upgrade: cancel the members-only row + emit canceled) create the
 *       ACTIVE membership + idempotency row + activated event, all in ONE
 *       transaction — a decline/rollback leaves the members-only intact.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SubscribeUseCase {

    /** Cancel reason recorded on the members-only row when a PREMIUM upgrade supersedes it. */
    static final String CANCEL_REASON_UPGRADE = "SUPERSEDED_BY_UPGRADE";

    private final MembershipRepository membershipRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final PaymentGatewayPort paymentGateway;
    private final MembershipEventPublisher eventPublisher;
    private final UpgradeQuoter upgradeQuoter;
    private final ClockPort clock;

    @Transactional
    public MembershipView execute(SubscribeCommand cmd) {
        ActorContext actor = cmd.actor();
        String tenantId = actor.tenantId();
        String accountId = actor.accountId();
        String fingerprint = fingerprint(cmd.tier(), cmd.planMonths(), cmd.paymentId());

        // 1) Idempotency check ---------------------------------------------------
        Optional<IdempotencyKey> existing =
                idempotencyKeyRepository.find(tenantId, accountId, cmd.idempotencyKey());
        if (existing.isPresent()) {
            IdempotencyKey key = existing.get();
            if (!key.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyKeyConflictException(cmd.idempotencyKey());
            }
            // Same key + same payload → replay the stored result (no re-auth, no dup, no re-supersede).
            Membership stored = membershipRepository
                    .findByIdScoped(key.getMembershipId(), accountId, tenantId)
                    .orElseThrow(() -> new MembershipNotFoundException(key.getMembershipId()));
            return MembershipView.from(stored, clock.now());
        }

        Instant now = clock.now();

        // 2) Upgrade assessment (shared with the quote endpoint) + prorated charge.
        UpgradeQuoter.UpgradeAssessment assessment =
                upgradeQuoter.assess(cmd.tier(), cmd.planMonths(), accountId, tenantId, now);
        Optional<Membership> upgradeFrom = assessment.supersedes();
        long amountMinor = assessment.quote().chargeMinor();

        // 3) PG authorize --------------------------------------------------------
        // A 0-won upgrade (credit ≥ list price) approves without a PG call — PortOne
        // cannot process a zero-amount payment.
        String paymentRef;
        if (amountMinor == 0L) {
            paymentRef = "upgrade_credit_" + UuidV7.randomString();
        } else {
            PaymentAuthorization result = paymentGateway.verify(new PaymentVerificationRequest(
                    cmd.paymentId(), amountMinor, "KRW", null));
            if (!result.approved()) {
                // Decline → NO row created, NO supersede, NO event (TX not yet mutated).
                throw new PaymentDeclinedException();
            }
            paymentRef = result.vendorPaymentRef();
        }

        // 4) Supersede the members-only (upgrade only) + create the new membership.
        upgradeFrom.ifPresent(members -> {
            members.cancel(now);
            membershipRepository.save(members);
            eventPublisher.publishCanceled(
                    members.getId(), members.getTenantId(), members.getAccountId(),
                    members.getTier(), CANCEL_REASON_UPGRADE, now, now);
        });

        Instant validFrom = now;
        Instant validTo = validFrom.plus(cmd.planMonths() * 30L, ChronoUnit.DAYS);
        String membershipId = UuidV7.randomString();

        Membership membership = Membership.activate(
                membershipId, tenantId, accountId, cmd.tier(),
                validFrom, validTo, cmd.planMonths(), paymentRef, now);
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
    private static String fingerprint(MembershipTier tier, int planMonths, String paymentId) {
        String raw = tier.name() + "|" + planMonths + "|" + (paymentId == null ? "" : paymentId);
        return Sha256.hex(raw);
    }
}
