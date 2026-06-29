package com.example.scmplatform.procurement.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;

/**
 * Wires the webhook HMAC verification stack:
 *
 * <ul>
 *   <li>{@link #webhookClock()} — the {@link Clock} the verifier reads "now"
 *       from (constructor-injected via {@code @Qualifier("webhookClock")}), so
 *       the freshness check is deterministic under test.</li>
 *   <li>{@link #webhookSignatureFilterRegistration} — registers the
 *       {@link WebhookSignatureFilter} scoped to {@code /api/procurement/webhooks/*}
 *       at high precedence so it runs before the security chain and never touches
 *       other routes.</li>
 * </ul>
 */
@Configuration
public class WebhookSecurityConfig {

    @Bean
    public Clock webhookClock() {
        return Clock.systemUTC();
    }

    @Bean
    public FilterRegistrationBean<WebhookSignatureFilter> webhookSignatureFilterRegistration(
            WebhookSignatureVerifier verifier, ObjectMapper objectMapper) {
        FilterRegistrationBean<WebhookSignatureFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new WebhookSignatureFilter(verifier, objectMapper));
        registration.addUrlPatterns("/api/procurement/webhooks/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("webhookSignatureFilter");
        return registration;
    }
}
