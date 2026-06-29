package com.example.scmplatform.procurement.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the {@link WebhookSignatureFilter} scoped to
 * {@code /api/procurement/webhooks/*} at high precedence so it runs before the
 * security chain and never touches other routes.
 *
 * <p>The {@link WebhookSignatureVerifier} reads "now" from the application's
 * existing {@link java.time.Clock} bean (defined in {@code OutboxConfig}) — this
 * config intentionally does NOT define a second {@code Clock} bean, which would
 * make the unqualified {@code Clock} injections elsewhere ambiguous.
 */
@Configuration
public class WebhookSecurityConfig {

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
