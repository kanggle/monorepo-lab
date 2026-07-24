package com.example.payment.config;

import com.example.libs.payment.toss.TossPaymentsAdapter;
import com.example.libs.payment.toss.TossPaymentsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Wires the shared Toss Payments adapter (ADR-MONO-056 Phase 1 / TASK-MONO-480). The adapter and
 * its {@link TossPaymentsProperties} live in {@code libs:payment-toss} (outside this app's
 * component-scan base {@code com.example.payment}), so they are registered explicitly here rather
 * than picked up by scanning.
 *
 * <p>The single {@link TossPaymentsAdapter} bean implements the three lib ports
 * ({@code PaymentGatewayPort} for verify/capture, {@code RefundablePaymentGateway} for refund,
 * {@code PaymentStatusReadPort} for the double-refund status guard); each consuming service injects
 * only the port(s) it needs. Resilience4j (CircuitBreaker/Retry/Bulkhead, instance
 * {@code toss-payments}) is declared on the adapter's methods and still applies to this
 * app-registered bean; its configuration remains in this service's {@code application.yml} under
 * {@code resilience4j.*.instances.toss-payments} plus {@code toss.payments.*}.
 *
 * <p>Excluded in the {@code standalone} profile, where {@link StandaloneConfig} provides an
 * in-memory stub instead (no DB / no PG).
 */
@Configuration
@Profile("!standalone")
@EnableConfigurationProperties(TossPaymentsProperties.class)
@Import(TossPaymentsAdapter.class)
public class PaymentGatewayConfig {
}
