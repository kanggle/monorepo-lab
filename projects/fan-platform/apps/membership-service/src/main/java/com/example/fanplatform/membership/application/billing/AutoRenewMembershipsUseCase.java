package com.example.fanplatform.membership.application.billing;

import com.example.fanplatform.membership.application.ActorContext;
import com.example.fanplatform.membership.application.RenewCommand;
import com.example.fanplatform.membership.application.RenewMembershipUseCase;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollment;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollmentRepository;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.pricing.MembershipPricing;
import com.example.fanplatform.membership.domain.time.ClockPort;
import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PaymentVerificationRequest;
import com.example.libs.payment.PgGatewayUnavailableException;
import com.example.libs.payment.RecurringBillingGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-initiated recurring billing (ADR-002 §D2). Once per (daily) tick it finds
 * every active {@link BillingKeyEnrollment}, works out that fan's <em>current</em>
 * membership for the enrolled tier, and — if it is within the renewal look-ahead —
 * charges the billing key and drives the <b>unmodified</b>
 * {@link RenewMembershipUseCase} to extend it.
 *
 * <h2>Candidate selection — why NOT a flat {@code WHERE validTo <= now} query</h2>
 *
 * A renew never mutates the old row: {@code RenewMembershipUseCase} inserts a NEW
 * ACTIVE row and leaves the prior one ACTIVE with its now-past {@code validTo}
 * forever. A naive "ACTIVE and validTo due" query would therefore re-select that
 * already-superseded old row on <em>every</em> future tick — an infinite /
 * duplicate-renewal bug. Instead this use case drives off the enrollment set and,
 * per enrollment, asks {@link MembershipRepository#findActiveByAccount} for the
 * fan's ACTIVE rows and picks the one for the enrolled tier with the greatest
 * {@code validTo} — the genuinely-current membership. Correct by construction: a
 * freshly-renewed fan's new row (future {@code validTo}) becomes the max and is not
 * "due" until its own window approaches, so it is never re-charged the next tick.
 *
 * <h2>Failure policy (ADR-002 §D4 — fail-closed, no new state)</h2>
 *
 * <ul>
 *   <li><b>Charge approved</b> → drive {@link RenewMembershipUseCase} with the
 *       server-generated {@code paymentId} (it re-verifies the paymentId itself,
 *       so the money-safety machinery is reused verbatim).</li>
 *   <li><b>Charge declined</b> → no renewal. The candidate re-appears next tick
 *       (its {@code validTo} is still within the look-ahead) until it either
 *       succeeds or {@code validTo} passes and the membership read-time-expires.
 *       Ordinary calendar time bounds the retries — no explicit attempt counter,
 *       no PAST_DUE/GRACE state.</li>
 *   <li><b>Ambiguous ({@link PgGatewayUnavailableException})</b> → the charge may
 *       have captured. Do NOT retry the charge and do NOT renew yet. Reconcile via
 *       the existing {@link PaymentGatewayPort#verify} with the SAME paymentId; only
 *       if that now reports approved do we renew — otherwise leave it for the next
 *       tick. Never assume success, never blindly re-charge (the double-charge
 *       failure this task exists to avoid, mirroring the stranded-refund lesson).</li>
 * </ul>
 *
 * <p>A per-candidate exception never aborts the whole tick (mirrors the expiry
 * sweeper's try/catch, but per-candidate here since this loops).
 *
 * <p><b>Idempotency of a duplicate tick</b> (deploy overlap / clock skew): a fresh
 * {@code idempotencyKey} per attempt is correct — the guard against double-charge
 * is the candidate query, not the key: once a charge+renew succeeds, the now-current
 * membership has a future {@code validTo}, so the account+tier is simply not
 * re-selected next tick.
 */
@Slf4j
@Service
public class AutoRenewMembershipsUseCase {

    private final BillingKeyEnrollmentRepository enrollmentRepository;
    private final MembershipRepository membershipRepository;
    private final RecurringBillingGateway recurringBillingGateway;
    private final PaymentGatewayPort paymentGateway;
    private final RenewMembershipUseCase renewMembershipUseCase;
    private final ClockPort clock;
    private final long lookaheadDays;

    public AutoRenewMembershipsUseCase(
            BillingKeyEnrollmentRepository enrollmentRepository,
            MembershipRepository membershipRepository,
            @Qualifier("recurringBillingGateway") RecurringBillingGateway recurringBillingGateway,
            @Qualifier("paymentGateway") PaymentGatewayPort paymentGateway,
            RenewMembershipUseCase renewMembershipUseCase,
            ClockPort clock,
            @Value("${fanplatform.membership.auto-renew.lookahead-days:1}") long lookaheadDays) {
        this.enrollmentRepository = enrollmentRepository;
        this.membershipRepository = membershipRepository;
        this.recurringBillingGateway = recurringBillingGateway;
        this.paymentGateway = paymentGateway;
        this.renewMembershipUseCase = renewMembershipUseCase;
        this.clock = clock;
        this.lookaheadDays = lookaheadDays;
    }

    /**
     * Processes up to {@code maxBatch} active enrollments.
     *
     * @return the number of memberships auto-renewed this invocation.
     */
    public int runOnce(int maxBatch) {
        Instant now = clock.now();
        Instant lookahead = now.plus(lookaheadDays, ChronoUnit.DAYS);
        List<BillingKeyEnrollment> enrollments = enrollmentRepository.findAllActive(maxBatch);
        int renewed = 0;
        for (BillingKeyEnrollment enrollment : enrollments) {
            try {
                if (processEnrollment(enrollment, now, lookahead)) {
                    renewed++;
                }
            } catch (RuntimeException e) {
                // One candidate's failure never aborts the tick (§D4). Never log the billing key.
                log.warn("Auto-renew: enrollment {} (tier={}) failed this tick: {}",
                        enrollment.getId(), enrollment.getTier(), e.toString());
            }
        }
        return renewed;
    }

    /** @return true iff a renewal was driven for this enrollment. */
    private boolean processEnrollment(BillingKeyEnrollment enrollment, Instant now, Instant lookahead) {
        Membership current = currentMembershipForTier(
                enrollment.getAccountId(), enrollment.getTenantId(), enrollment.getTier());
        if (current == null) {
            // No membership to renew (never subscribed to this tier, or canceled). Skip.
            return false;
        }
        if (current.getValidTo().isAfter(lookahead)) {
            // Not due yet.
            return false;
        }

        int planMonths = current.getPlanMonths();
        long amountMinor = MembershipPricing.listChargeMinor(enrollment.getTier(), planMonths);
        String paymentId = "pay-" + UUID.randomUUID();
        String orderName = orderName(enrollment.getTier(), planMonths);

        PaymentAuthorization auth;
        try {
            auth = recurringBillingGateway.chargeBillingKey(
                    enrollment.getBillingKey(), paymentId, amountMinor, "KRW", orderName);
        } catch (PgGatewayUnavailableException ambiguous) {
            // Charge outcome UNKNOWN — money may have moved. Reconcile via verify with the
            // SAME paymentId; never re-charge, never assume success.
            log.warn("Auto-renew: ambiguous charge for account={} tier={} paymentId={} -> reconcile via verify",
                    enrollment.getAccountId(), enrollment.getTier(), paymentId);
            auth = paymentGateway.verify(new PaymentVerificationRequest(paymentId, amountMinor, "KRW", null));
            if (!auth.approved()) {
                // Still not proven paid — leave it; next tick re-selects and retries.
                log.info("Auto-renew: reconcile inconclusive for paymentId={} -> defer to next tick", paymentId);
                return false;
            }
        }

        if (!auth.approved()) {
            // Definitive decline → no renewal (§D4 fail-closed). Candidate reappears next tick.
            log.info("Auto-renew: charge declined for account={} tier={} -> no renewal",
                    enrollment.getAccountId(), enrollment.getTier());
            return false;
        }

        // Approved → drive the UNMODIFIED renew use case (system-driven: no end-user JWT).
        ActorContext actor = new ActorContext(enrollment.getAccountId(), enrollment.getTenantId(), Set.of());
        String idempotencyKey = "auto-" + UUID.randomUUID();
        RenewCommand cmd = new RenewCommand(actor, current.getId(), planMonths, paymentId, idempotencyKey);
        renewMembershipUseCase.execute(cmd);
        log.info("Auto-renew: renewed membership {} (account={} tier={})",
                current.getId(), enrollment.getAccountId(), enrollment.getTier());
        return true;
    }

    private Membership currentMembershipForTier(String accountId, String tenantId, MembershipTier tier) {
        List<Membership> active = membershipRepository.findActiveByAccount(accountId, tenantId);
        Optional<Membership> current = active.stream()
                .filter(m -> m.getTier() == tier)
                .max(Comparator.comparing(Membership::getValidTo));
        return current.orElse(null);
    }

    private static String orderName(MembershipTier tier, int planMonths) {
        return "Fan membership auto-renewal — " + tier.name() + " x" + planMonths + " month(s)";
    }
}
