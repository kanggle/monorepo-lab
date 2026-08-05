package com.example.payment.config;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PaymentGatewayStatus;
import com.example.libs.payment.PaymentStatusReadPort;
import com.example.libs.payment.PaymentVerificationRequest;
import com.example.libs.payment.PgConfirmFailedException;
import com.example.libs.payment.RefundablePaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The {@code demo-pg} profile: a mock PG that approves, while <strong>every other collaborator
 * stays real</strong> — above all {@link com.example.payment.application.port.out.PaymentEventPublisher},
 * so {@code PaymentCompleted} really reaches Kafka and the order → shipping → settlement sagas run
 * (TASK-BE-572).
 *
 * <h2>Why {@code standalone} is not this</h2>
 *
 * {@link StandaloneConfig} also stubs the PG, and reaching for it is the obvious move — but it
 * additionally replaces {@code PaymentEventPublisher} with a <strong>no-op</strong>. Payment then
 * completes and nothing downstream ever hears about it: the order never confirms, no shipment, no
 * settlement. For a portfolio demo that is worse than no payment at all, because the screen says
 * "paid" and the rest of the system silently disagrees. {@code standalone} is for a
 * <em>no-DB / no-Kafka local run</em>; this profile is for a <em>fully wired demo whose only fake
 * is the money</em>. Two different fakes, deliberately not merged.
 *
 * <h2>Naming</h2>
 *
 * {@code demo-pg}, not {@code demo}: it swaps the <strong>payment gateway</strong> and nothing
 * else. A profile called {@code demo} would invite the next person to hang unrelated demo
 * behaviour off it, and then "is the demo profile on?" stops answering "is the money fake?".
 *
 * <h2>Declining is done by THROWING, never by returning {@code declined()}</h2>
 *
 * <strong>Do not "simplify" {@link DemoPaymentGateway#verify} to return
 * {@link PaymentAuthorization#declined()}.</strong> The original reason was a defect: measured
 * under TASK-BE-572 AC-0, {@code PaymentConfirmService.confirm} never read
 * {@code PaymentAuthorization.approved()}, so a gateway returning {@code declined()} here would
 * have been recorded as a <em>successful</em> payment. <b>TASK-BE-574 closed that hole</b> — the
 * confirm path now rejects a value-decline, so this class is no longer the only thing standing
 * between a declined demo payment and a confirmed order.
 *
 * <p>The instruction stands anyway, for a different and better reason. A {@code declined()} value
 * cannot say whether the decline was permanent or transient, so BE-574 must treat it
 * conservatively as indeterminate (503, row left PENDING for retry). This gateway <em>knows</em>
 * its declines are deliberate and permanent, and {@code PgConfirmFailedException} is the shape
 * that carries that — which is also exactly how {@code TossPaymentsAdapter} signals a real 4xx.
 * Throwing keeps the demo's rejection landing on the {@code FAILED} path and keeps the demo
 * indistinguishable from the real adapter to its caller, which is the whole point of this class.
 */
@Slf4j
@Configuration
@Profile("demo-pg")
public class DemoPaymentGatewayConfig {

    /**
     * Registered by concrete type so it satisfies every lib port a collaborator injects
     * ({@link PaymentGatewayPort} for verify, {@link RefundablePaymentGateway} for the
     * post-capture auto-refund, {@link PaymentStatusReadPort} for the double-refund status
     * guard) — the same three {@link StandaloneConfig}'s stub satisfies. Registering it as
     * {@code PaymentGatewayPort} only would leave the other two injection points unsatisfied and
     * the context would fail to start.
     */
    @Bean
    DemoPaymentGateway demoPaymentGateway() {
        return new DemoPaymentGateway();
    }

    /**
     * Approves everything except one reserved order reference, which throws — so the demo can
     * show the failure path too, not just the happy one.
     *
     * <p>The sentinel is the ORDER reference rather than the payment reference because the demo
     * checkout mints the payment reference itself (there is no PG to mint it), so the order id is
     * the only value a person driving the demo can actually control. It is reachable through the
     * normal confirm API; no UI is wired for it — showing a deliberately broken button in a
     * portfolio demo costs more than it gives.
     */
    public static class DemoPaymentGateway
            implements PaymentGatewayPort, RefundablePaymentGateway, PaymentStatusReadPort {

        /** Reserved order reference that forces a definitive PG rejection. */
        public static final String DECLINE_ORDER_REFERENCE = "demo-decline";

        private static final String VENDOR_REF_PREFIX = "demopg_";

        @Override
        public PaymentAuthorization verify(PaymentVerificationRequest request) {
            if (DECLINE_ORDER_REFERENCE.equals(request.orderReference())) {
                log.info("DEMO-PG: declining order {} (reserved sentinel)", request.orderReference());
                throw new PgConfirmFailedException("demo-pg: declined by reserved order reference");
            }
            String vendorRef = request.paymentReference() != null
                    ? request.paymentReference()
                    : VENDOR_REF_PREFIX + request.orderReference();
            log.info("DEMO-PG: approving order {} (amount={} {})",
                    request.orderReference(), request.expectedAmountMinor(), request.currency());
            return PaymentAuthorization.approved(vendorRef, "CARD", null);
        }

        @Override
        public void refund(String vendorPaymentRef, String reason) {
            log.info("DEMO-PG: refund {} ({})", vendorPaymentRef, reason);
        }

        @Override
        public void refund(String vendorPaymentRef, String reason, long amountMinor) {
            log.info("DEMO-PG: partial refund {} amount={} ({})", vendorPaymentRef, amountMinor, reason);
        }

        /**
         * The stranded-refund sweeper runs under this profile (it is only excluded from
         * {@code standalone}), so this must answer rather than throw. {@code CAPTURED} is the
         * honest answer: every payment this gateway approved was "captured" as far as the demo
         * is concerned, and it is the answer that makes the double-refund guard behave the same
         * way it would against a real PG.
         */
        @Override
        public PaymentGatewayStatus fetchStatus(String vendorPaymentRef) {
            log.info("DEMO-PG: status fetch {} -> CAPTURED", vendorPaymentRef);
            return PaymentGatewayStatus.CAPTURED;
        }
    }
}
