package com.wms.outbound.adapter.out.secret;

import com.wms.outbound.application.port.out.WebhookSecretPort;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Resolves ERP order webhook HMAC secrets from environment variables.
 *
 * <p>Per {@code specs/contracts/webhooks/erp-order-webhook.md} § Security
 * Notes, v1 supports env-var fallback for dev profiles.
 *
 * <p>Lookup format: the {@code X-Erp-Source} header value (e.g.,
 * {@code erp-prod}) is upper-cased and embedded in
 * {@code ERP_WEBHOOK_ORDER_SECRET_<UPPER_WITH_UNDERSCORES>}. Hyphens are
 * converted to underscores so {@code erp-prod} becomes
 * {@code ERP_WEBHOOK_ORDER_SECRET_ERP_PROD}.
 *
 * <p>Missing env var → {@link Optional#empty()} → controller responds 401.
 */
@Component
public class EnvWebhookSecretAdapter implements WebhookSecretPort {

    private static final String ENV_PREFIX = "ERP_WEBHOOK_ORDER_SECRET_";
    private static final String PROPERTY_PREFIX = "outbound.webhook.erp.secrets.";

    private final Environment environment;

    public EnvWebhookSecretAdapter(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> getSecret(String source) {
        if (source == null || source.isBlank()) {
            return Optional.empty();
        }
        String envKey = ENV_PREFIX + source.toUpperCase().replace('-', '_');
        String secret = environment.getProperty(envKey);
        if (secret != null && !secret.isBlank()) {
            return Optional.of(secret);
        }
        // Spring property fallback for test overrides without polluting OS env.
        String propertyKey = PROPERTY_PREFIX + source;
        String fromProperty = environment.getProperty(propertyKey);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Optional.of(fromProperty);
        }
        return Optional.empty();
    }
}
