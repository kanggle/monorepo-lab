package com.wms.inbound.adapter.out.webhook;

import com.wms.inbound.application.port.out.WebhookSecretPort;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Resolves ERP webhook HMAC secrets from environment variables.
 *
 * <p>Per {@code specs/contracts/webhooks/erp-asn-webhook.md} § Security Notes,
 * v1 supports env-var fallback for {@code dev} profiles. A real Secret Manager
 * adapter is v2.
 *
 * <p>Lookup format: the {@code X-Erp-Source} header value (e.g.,
 * {@code erp-prod}) is upper-cased and embedded in
 * {@code ERP_WEBHOOK_SECRET_<UPPER_WITH_UNDERSCORES>}. Hyphens are converted
 * to underscores so {@code erp-prod} → {@code ERP_WEBHOOK_SECRET_ERP_PROD}.
 *
 * <p>Missing env var → {@link Optional#empty()} → controller responds 401
 * (no secret to verify against).
 */
@Component
public class EnvVarWebhookSecretAdapter implements WebhookSecretPort {

    private static final String ENV_PREFIX = "ERP_WEBHOOK_SECRET_";
    private static final String PROPERTY_PREFIX = "inbound.webhook.erp.secrets.";

    private final Environment environment;

    public EnvVarWebhookSecretAdapter(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> resolveSecret(String source) {
        if (source == null || source.isBlank()) {
            return Optional.empty();
        }
        String envKey = ENV_PREFIX + source.toUpperCase().replace('-', '_');
        String secret = environment.getProperty(envKey);
        if (secret != null && !secret.isBlank()) {
            return Optional.of(secret);
        }
        // Fallback: spring property style for test overrides without polluting
        // the OS env. Allows @TestPropertySource("inbound.webhook.erp.secrets.erp-stg=...").
        String propertyKey = PROPERTY_PREFIX + source;
        String fromProperty = environment.getProperty(propertyKey);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Optional.of(fromProperty);
        }
        return Optional.empty();
    }
}
