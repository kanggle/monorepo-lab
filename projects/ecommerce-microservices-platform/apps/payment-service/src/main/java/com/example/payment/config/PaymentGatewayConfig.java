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
 * in-memory stub instead (no DB / no PG), and in {@code demo-pg}, where
 * {@link DemoPaymentGatewayConfig} provides a mock that approves while every other collaborator —
 * crucially the event publisher — stays real (TASK-BE-572). Both exclusions are load-bearing, not
 * cosmetic: those stub beans implement the same three lib ports as {@link TossPaymentsAdapter}, so
 * leaving the real adapter registered alongside one of them makes every injection point of those
 * ports ambiguous and the context fails to start.
 *
 * <p><strong>The default is unchanged: no profile still means the real PG.</strong> fan-platform
 * makes its mock the default ({@code @Profile("!portone")}) because its real PG needs a secret CI
 * does not have; ecommerce is the other way round — the Toss adapter is the production path and
 * must be what you get when nothing is named. Parity with the sibling is about the mechanism
 * (profile selection, ADR-MONO-056 D2), not about which side the switch rests on
 * (TASK-BE-572 AC-3).
 */
@Configuration
@Profile("!standalone & !demo-pg")
@EnableConfigurationProperties(TossPaymentsProperties.class)
@Import(TossPaymentsAdapter.class)
public class PaymentGatewayConfig {
}
