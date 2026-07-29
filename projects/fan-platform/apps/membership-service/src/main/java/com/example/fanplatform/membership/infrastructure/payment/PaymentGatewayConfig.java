package com.example.fanplatform.membership.infrastructure.payment;

import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.RecurringBillingGateway;
import com.example.libs.payment.portone.PortOnePaymentAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

/**
 * Wires the shared PortOne verify-model adapter (ADR-MONO-056, {@code libs/payment-portone})
 * as the {@link PaymentGatewayPort} under {@code @Profile("portone")} — replacing the
 * in-service {@code PortOnePaymentAdapter} deleted by TASK-MONO-479.
 *
 * <p><b>Why a {@code @Bean}, not component-scan.</b> The lib adapter is a profile-agnostic
 * plain {@code @Component} in {@code com.example.libs.payment.portone}, which this service's
 * {@code @SpringBootApplication} (base package {@code com.example.fanplatform.membership}) does
 * NOT scan. Scanning it would register the real adapter unconditionally — a double-bean against
 * {@link MockPaymentGatewayAdapter} under the default profile, and a real PortOne dependency in
 * keyless CI. Instead this factory registers it ONLY under {@code portone}, preserving the exact
 * profile selection the service had before the migration: mock is the keyless/CI/test default;
 * the real PG is reached only with the {@code portone} profile + an injected API secret.
 *
 * <p>The membership {@code fan.payment.portone.*} config keys are bound here and passed to the
 * lib constructor (the lib's own {@code @Value} defaults are bypassed by manual construction).
 * Keeping the existing {@code fan.payment.portone.*} namespace avoids renaming the runtime env
 * ({@code FAN_PAYMENT_PORTONE_API_SECRET}) the local demo override + gitignored {@code .env}
 * already set — a behavior-preserving choice with no deployment blast radius.
 *
 * <p><b>{@code @Qualifier} on both beans (TASK-FAN-BE-033-fix-02).</b> {@link PortOnePaymentAdapter}
 * implements BOTH {@link PaymentGatewayPort} and {@link RecurringBillingGateway}, so Spring's
 * eager type-matching during autowiring finds each bean method's instance assignable to BOTH
 * ports, not just its declared return type — every unqualified injection point of either port
 * throws {@code NoUniqueBeanDefinitionException} under the {@code portone} profile (caught live,
 * never exercised by CI since no test boots the full context under {@code portone}). The
 * {@code "paymentGateway"} / {@code "recurringBillingGateway"} qualifier values are mirrored on
 * {@link MockPaymentGatewayAdapter} / {@link MockRecurringBillingGateway} and on every consumer
 * constructor parameter of these types, so resolution is identical and unambiguous under both
 * profiles.
 *
 * <p><b>Amendment (TASK-FAN-BE-034).</b> fix-02's consumer-side change placed
 * {@code @Qualifier("paymentGateway")} on the FIELD of three Lombok {@code @RequiredArgsConstructor}
 * use cases ({@code SubscribeUseCase}, {@code RenewMembershipUseCase}, {@code WebhookReconcileUseCase}).
 * Lombok does not copy that annotation onto the constructor parameter it generates (confirmed via
 * javap: zero {@code RuntimeVisibleParameterAnnotations}), so those three injection points stayed
 * effectively unqualified and still threw {@code NoUniqueBeanDefinitionException} under
 * {@code portone} — discovered by the {@code PortOneProfileContextBootIntegrationTest} guard this
 * task adds, on its very first (unmutated) run. Fixed by converting all three to an explicit
 * constructor with {@code @Qualifier} on the parameter (the pattern {@link
 * com.example.fanplatform.membership.application.billing.AutoRenewMembershipsUseCase} already
 * used correctly) — no behavior change, DI wiring only.
 */
@Configuration
public class PaymentGatewayConfig {

    @Bean
    @Profile("portone")
    @Qualifier("paymentGateway")
    PaymentGatewayPort portOnePaymentGateway(
            @Value("${fan.payment.portone.api-base:https://api.portone.io}") String apiBase,
            @Value("${fan.payment.portone.api-secret}") String apiSecret,
            RestClient.Builder builder) {
        return new PortOnePaymentAdapter(apiBase, apiSecret, builder);
    }

    /**
     * The recurring-billing capability under {@code portone} (ADR-MONO-057 / ADR-002).
     * The shared {@link PortOnePaymentAdapter} implements BOTH {@link PaymentGatewayPort}
     * and {@link RecurringBillingGateway}, so we expose the SAME already-wired instance
     * (same RestClient / base URL / API secret) under the second port rather than
     * constructing a duplicate adapter. Under the default mock profile,
     * {@code MockRecurringBillingGateway} supplies this port instead.
     */
    @Bean
    @Profile("portone")
    @Qualifier("recurringBillingGateway")
    RecurringBillingGateway portOneRecurringBillingGateway(
            @Qualifier("paymentGateway") PaymentGatewayPort portOnePaymentGateway) {
        return (RecurringBillingGateway) portOnePaymentGateway;
    }
}
