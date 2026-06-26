package com.example.payment.config;

import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.event.PaymentRefundStrandedEvent;
import com.example.payment.application.event.PaymentRefundUnresolvedEvent;
import com.example.payment.application.event.PaymentRefundedEvent;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.example.payment.application.port.out.PaymentGatewayConfirmResult;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.port.out.PaymentGatewayStatus;
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

    @Bean
    PaymentGatewayPort paymentGatewayPort() {
        return new PaymentGatewayPort() {
            @Override
            public PaymentGatewayConfirmResult confirmPayment(String paymentKey, String orderId, long amount) {
                log.info("STANDALONE: Simulated payment confirm for order {}", orderId);
                return new PaymentGatewayConfirmResult("CARD", null);
            }

            @Override
            public void cancelPayment(String paymentKey, String cancelReason) {
                log.info("STANDALONE: Simulated payment cancel for paymentKey {}", paymentKey);
            }

            @Override
            public void cancelPayment(String paymentKey, String cancelReason, long cancelAmount) {
                log.info("STANDALONE: Simulated partial payment cancel for paymentKey {} (cancelAmount={})",
                        paymentKey, cancelAmount);
            }

            @Override
            public PaymentGatewayStatus fetchStatus(String paymentKey) {
                // The stranded-refund sweeper is @Profile("!standalone") and never runs here.
                log.info("STANDALONE: Simulated payment status fetch for paymentKey {}", paymentKey);
                return PaymentGatewayStatus.UNKNOWN;
            }
        };
    }
}
