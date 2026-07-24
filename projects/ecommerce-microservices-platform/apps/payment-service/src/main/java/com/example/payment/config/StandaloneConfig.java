package com.example.payment.config;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PaymentGatewayStatus;
import com.example.libs.payment.PaymentStatusReadPort;
import com.example.libs.payment.PaymentVerificationRequest;
import com.example.libs.payment.RefundablePaymentGateway;
import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.event.PaymentRefundStrandedEvent;
import com.example.payment.application.event.PaymentRefundUnresolvedEvent;
import com.example.payment.application.event.PaymentRefundedEvent;
import com.example.payment.application.port.out.PaymentEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("standalone")
public class StandaloneConfig {

    @Bean
    PaymentEventPublisher noOpPaymentEventPublisher() {
        return new PaymentEventPublisher() {
            @Override
            public void publishPaymentCompleted(PaymentCompletedEvent event) {
                log.debug("[standalone] Payment completed event (no-op): {}", event);
            }

            @Override
            public void publishPaymentRefunded(PaymentRefundedEvent event) {
                log.debug("[standalone] Payment refunded event (no-op): {}", event);
            }

            @Override
            public void publishPaymentRefundStranded(PaymentRefundStrandedEvent event) {
                log.debug("[standalone] Payment refund stranded event (no-op): {}", event);
            }

            @Override
            public void publishPaymentRefundUnresolved(PaymentRefundUnresolvedEvent event) {
                log.debug("[standalone] Payment refund unresolved event (no-op): {}", event);
            }
        };
    }

    /**
     * In-memory PG stub for the {@code standalone} profile (no real Toss adapter). Registered by
     * its concrete type so it satisfies every lib port a service injects
     * ({@link PaymentGatewayPort}, {@link RefundablePaymentGateway}, {@link PaymentStatusReadPort}).
     */
    @Bean
    StandalonePaymentGateway standalonePaymentGateway() {
        return new StandalonePaymentGateway();
    }

    static class StandalonePaymentGateway
            implements PaymentGatewayPort, RefundablePaymentGateway, PaymentStatusReadPort {

        @Override
        public PaymentAuthorization verify(PaymentVerificationRequest request) {
            log.info("STANDALONE: Simulated payment verify for order {}", request.orderReference());
            return PaymentAuthorization.approved(request.paymentReference(), "CARD", null);
        }

        @Override
        public void refund(String vendorPaymentRef, String reason) {
            log.info("STANDALONE: Simulated payment refund for paymentKey {}", vendorPaymentRef);
        }

        @Override
        public void refund(String vendorPaymentRef, String reason, long amountMinor) {
            log.info("STANDALONE: Simulated partial payment refund for paymentKey {} (amount={})",
                    vendorPaymentRef, amountMinor);
        }

        @Override
        public PaymentGatewayStatus fetchStatus(String vendorPaymentRef) {
            // The stranded-refund sweeper is @Profile("!standalone") and never runs here.
            log.info("STANDALONE: Simulated payment status fetch for paymentKey {}", vendorPaymentRef);
            return PaymentGatewayStatus.UNKNOWN;
        }
    }
}
