package com.example.fanplatform.membership.infrastructure.payment;

import com.example.libs.payment.portone.PortOneWebhookVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the shared {@link PortOneWebhookVerifier} (ADR-MONO-057 / {@code libs/payment-portone}).
 * The verifier is a pure, stateless function of (secret, raw body, headers) — no
 * RestClient, no API secret, no profile — so it is a plain profile-agnostic bean
 * (the webhook endpoint exists in every profile; the webhook <em>secret</em> is
 * bound at the controller from {@code fan.payment.portone.webhook-secret}).
 */
@Configuration
public class WebhookConfig {

    @Bean
    PortOneWebhookVerifier portOneWebhookVerifier() {
        return new PortOneWebhookVerifier();
    }
}
