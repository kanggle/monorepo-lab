package com.example.fanplatform.membership.integration;

import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.RecurringBillingGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-FAN-BE-034 — CI-gated guard for TASK-FAN-BE-033-fix-02.
 *
 * <p><b>Why this exists.</b> fix-02 fixed a {@code NoUniqueBeanDefinitionException} that only
 * manifested when {@code SPRING_PROFILES_ACTIVE=portone} was active in a LIVE deployment — both
 * {@code PaymentGatewayConfig} beans expose the SAME {@link com.example.libs.payment.portone
 * .PortOnePaymentAdapter} instance (which implements both {@link PaymentGatewayPort} AND
 * {@link RecurringBillingGateway}), so Spring's eager type-matching finds each bean assignable to
 * BOTH ports, and any UNQUALIFIED injection point of either port is ambiguous. Under the default
 * (mock) profile this is invisible — {@code MockPaymentGatewayAdapter} and
 * {@code MockRecurringBillingGateway} are separate single-interface classes, so there is nothing
 * to disambiguate — and no test booted a full context under {@code portone}, so CI never caught it
 * (see {@code PaymentGatewayConfig}'s class javadoc for the full mechanism).
 *
 * <p>This test closes that blind spot: it boots the FULL {@code membership-service} Spring context
 * with the {@code portone} profile active. Any unqualified/mis-qualified
 * {@code PaymentGatewayPort} / {@code RecurringBillingGateway} injection point anywhere in the
 * bean graph (a new use case, a {@code PaymentGatewayConfig} edit, a qualifier typo/removal, a
 * Lombok field-annotation trap — see the second finding below) throws
 * {@code NoUniqueBeanDefinitionException} (or a related wiring exception) at context-refresh
 * time, which fails this test's context load BEFORE {@link #contextLoadsUnderPortoneProfile()}
 * even runs — CI catches it immediately, without real PortOne keys.
 *
 * <p><b>Not asserted: {@code getBeanNamesForType} returning exactly one name per port.</b> That
 * was tried first and is the WRONG invariant — it fails even on the correctly-wired graph,
 * because {@code getBeanNamesForType(PaymentGatewayPort.class)} legitimately returns BOTH
 * {@code portOnePaymentGateway} and {@code portOneRecurringBillingGateway} (both beans' actual
 * runtime type is {@code PortOnePaymentAdapter}, which implements {@code PaymentGatewayPort}
 * regardless of which port each bean method declares as its return type) — that duality is the
 * adapter's intended design (§ {@code PaymentGatewayConfig}), not a defect. The real invariant is
 * that the two NAMED, QUALIFIED injection points below resolve unambiguously — the same
 * qualifier-based resolution every production consumer constructor uses.
 *
 * <p><b>Mutation-checked (TASK-FAN-BE-034 AC-2) — two independent findings, both proven locally:</b>
 * <ol>
 *   <li><b>Reversing a producer-side qualifier.</b> Locally changing
 *       {@code PaymentGatewayConfig.portOnePaymentGateway()}'s {@code @Qualifier("paymentGateway")}
 *       to {@code @Qualifier("recurringBillingGateway")} reproduces a fresh ambiguity and turns
 *       this test class RED at context refresh; reverting turns it back GREEN.</li>
 *   <li><b>A REAL regression this test found on its first (unmutated) run, fixed in the same
 *       change that adds this test.</b> {@code SubscribeUseCase}, {@code RenewMembershipUseCase},
 *       and {@code WebhookReconcileUseCase} used Lombok {@code @RequiredArgsConstructor} with
 *       {@code @Qualifier("paymentGateway")} on the FIELD (not the generated constructor
 *       parameter). Lombok does not copy that annotation onto the constructor parameter it
 *       generates (confirmed via {@code javap}: zero {@code RuntimeVisibleParameterAnnotations}
 *       on the compiled constructor) — so those three injection points were silently unqualified,
 *       and this test's very first local run threw {@code NoUniqueBeanDefinitionException} for
 *       {@code RenewMembershipUseCase}'s constructor. Fixed by converting all three to an
 *       explicit constructor with {@code @Qualifier} on the parameter — see
 *       {@code PaymentGatewayConfig}'s "Amendment (TASK-FAN-BE-034)" javadoc and each fixed
 *       class's own javadoc for the full writeup.</li>
 * </ol>
 * Neither mutation is committed — this class always runs against the fixed wiring.
 *
 * <p><b>No real network call at context-load time.</b> {@code PortOnePaymentAdapter}'s constructor
 * only builds a {@code RestClient} (base URL + default {@code Authorization} header) — it issues no
 * HTTP request eagerly (verified by reading {@code libs/payment-portone/.../PortOnePaymentAdapter
 * .java}: the constructor body has zero {@code restClient.get()/post()} calls). The adapter's REST
 * calls happen only inside {@code verify}/{@code chargeBillingKey}, neither of which this test
 * invokes. Asserting that outbound-call behaviour itself is Out of Scope here (TASK-FAN-BE-034
 * Scope) — that is {@code PortOnePaymentAdapterTest}'s concern (MockWebServer-backed).
 *
 * <p>Reuses {@link MembershipServiceIntegrationBase}'s {@code webEnvironment = RANDOM_PORT} +
 * Postgres + Kafka Testcontainers + JWKS wiring as-is (no HTTP call is made, and no DB/Kafka
 * *behaviour* is exercised or asserted here — Scope note). A full embedded-server context is
 * actually required, not merely convenient: {@code SecurityConfig} uses MVC-pattern
 * {@code securityMatcher}s, which need the {@code mvcHandlerMappingIntrospector} bean that only
 * Spring MVC autoconfiguration registers for a SERVLET web application — {@code webEnvironment =
 * NONE} was tried first and does NOT boot this context (confirmed locally: {@code
 * NoSuchBeanDefinitionException} for {@code mvcHandlerMappingIntrospector}), so this class
 * deliberately does NOT override {@code @SpringBootTest} and inherits the base's RANDOM_PORT.
 */
@ActiveProfiles("portone")
class PortOneProfileContextBootIntegrationTest extends MembershipServiceIntegrationBase {

    @DynamicPropertySource
    static void portoneProperties(DynamicPropertyRegistry registry) {
        // Placeholder-only — under `portone`, PaymentGatewayConfig requires
        // fan.payment.portone.api-secret (no default; fails fast if unset). Neither value is a
        // real PortOne credential, and (per class javadoc) no request is ever sent to either URL.
        registry.add("fan.payment.portone.api-base", () -> "https://portone-placeholder.invalid");
        registry.add("fan.payment.portone.api-secret", () -> "test-placeholder-secret");
    }

    @Autowired
    ApplicationContext context;

    // The exact qualifier-based resolution every production consumer constructor uses
    // (PaymentGatewayConfig, SubscribeUseCase, RenewMembershipUseCase,
    // WebhookReconcileUseCase, AutoRenewMembershipsUseCase). Injecting these directly is a
    // secondary, explicit confirmation of resolvability beyond "context refresh didn't throw".
    @Autowired
    @Qualifier("paymentGateway")
    PaymentGatewayPort paymentGateway;

    @Autowired
    @Qualifier("recurringBillingGateway")
    RecurringBillingGateway recurringBillingGateway;

    @Test
    @DisplayName("membership-service ApplicationContext loads cleanly under the portone profile")
    void contextLoadsUnderPortoneProfile() {
        // A NoUniqueBeanDefinitionException (the fix-02 defect class, and the Lombok-field-
        // qualifier variant this task's first run found) would have already failed context
        // refresh above, well before this method runs — that failure mode IS the guard.
        assertThat(context).isNotNull();
        assertThat(paymentGateway).isNotNull();
        assertThat(recurringBillingGateway).isNotNull();
    }
}
