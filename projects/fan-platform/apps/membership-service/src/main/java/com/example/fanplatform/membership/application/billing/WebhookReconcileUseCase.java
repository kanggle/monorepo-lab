package com.example.fanplatform.membership.application.billing;

import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.pricing.MembershipPricing;
import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PaymentVerificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reconciles a verified PortOne webhook (ADR-002 §D3). The webhook is a durability
 * backstop, NOT a source of truth: the caller has already proven the message is
 * authentically from PortOne (HMAC signature), but its <em>payload</em>
 * (amount/status) is never trusted here. The truth always comes from a
 * {@link PaymentGatewayPort#verify} read.
 *
 * <h2>The "which candidate does this paymentId belong to?" problem — design choice</h2>
 *
 * A scheduler tick generates an ephemeral {@code paymentId} and stores it nowhere
 * (ADR-002 §D3 / ADR-MONO-057 §7: no new association/dedupe table). So a webhook's
 * bare {@code paymentId} cannot, on its own, be mapped back to a fan+tier+amount.
 * We deliberately do NOT try to infer that context from the untrusted payload (that
 * would repeat the ADR-001 mistake), and we cannot safely search enrollments by
 * amount (amounts collide across fans → could renew the wrong account). Therefore:
 *
 * <ul>
 *   <li><b>A membership already carries this {@code paymentId} as its
 *       {@code paymentRef}</b> → a scheduler tick already renewed for this charge.
 *       This is the common at-least-once duplicate delivery. We re-run
 *       {@code verify(paymentId, expected)} purely as a confirmation read (idempotent,
 *       no state change) and return — the webhook is absorbed as a no-op (ADR-002
 *       §D3: duplicates are absorbed by the renew idempotency, no dedupe table).</li>
 *   <li><b>No membership carries this {@code paymentId}</b> → either an
 *       unknown/irrelevant delivery, or a charge whose renewal has not (yet) been
 *       created. We have no trustworthy context to renew from, so we ack (return
 *       200). The scheduler's candidate re-selection is the durability path that
 *       completes any genuinely-pending renewal on its next tick (the membership's
 *       {@code validTo} is still within the look-ahead).</li>
 * </ul>
 *
 * <p><b>Known residual (flagged, not fixed in v1):</b> if a lost-response charge
 * captured money but produced no membership, this handler cannot complete THAT
 * charge's renewal (no stored context); the next scheduler tick re-charges with a
 * fresh {@code paymentId}, a bounded double-charge window. Closing it fully needs a
 * persisted {@code paymentId→context} association, which ADR-002 §D3 deliberately
 * defers out of v1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookReconcileUseCase {

    private final MembershipRepository membershipRepository;
    private final PaymentGatewayPort paymentGateway;

    /**
     * Reconcile the outcome referenced by a verified webhook. Always safe to run
     * (idempotent, read-only side of a duplicate). Never throws for an
     * unknown/duplicate delivery.
     */
    public void reconcileByPaymentId(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            log.info("Webhook reconcile: no paymentId in payload -> nothing to do");
            return;
        }
        Optional<Membership> existing = membershipRepository.findByPaymentRef(paymentId);
        if (existing.isEmpty()) {
            // No membership for this charge. Ack; the scheduler completes any pending renewal.
            log.info("Webhook reconcile: paymentId={} not associated with any membership -> ack "
                    + "(scheduler candidate re-selection will reconcile)", paymentId);
            return;
        }

        // A renewal already committed for this charge. Confirm the truth via verify (never
        // the payload), purely as an idempotent read — no state change.
        Membership m = existing.get();
        long amountMinor = MembershipPricing.listChargeMinor(m.getTier(), m.getPlanMonths());
        PaymentAuthorization truth = paymentGateway.verify(
                new PaymentVerificationRequest(paymentId, amountMinor, "KRW", null));
        log.info("Webhook reconcile: paymentId={} already renewed membership={} (verify approved={}) -> no-op",
                paymentId, m.getId(), truth.approved());
    }
}
