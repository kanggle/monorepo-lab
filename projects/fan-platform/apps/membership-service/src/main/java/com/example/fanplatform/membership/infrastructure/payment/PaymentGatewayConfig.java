package com.example.fanplatform.membership.infrastructure.payment;

import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.portone.PortOnePaymentAdapter;
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
 */
@Configuration
public class PaymentGatewayConfig {

    @Bean
    @Profile("portone")
    PaymentGatewayPort portOnePaymentGateway(
            @Value("${fan.payment.portone.api-base:https://api.portone.io}") String apiBase,
            @Value("${fan.payment.portone.api-secret}") String apiSecret,
            RestClient.Builder builder) {
        return new PortOnePaymentAdapter(apiBase, apiSecret, builder);
    }
}
