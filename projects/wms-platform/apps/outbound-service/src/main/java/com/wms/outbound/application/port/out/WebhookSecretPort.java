package com.wms.outbound.application.port.out;

import java.util.Optional;

/**
 * Out-port resolving the HMAC verification secret for an incoming ERP webhook.
 *
 * <p>Per {@code specs/contracts/webhooks/erp-order-webhook.md} § Signature
 * Computation, the {@code X-Erp-Source} header selects which per-environment
 * secret should be used.
 *
 * <p>v1 implementation reads {@code ERP_WEBHOOK_ORDER_SECRET_<SOURCE_UPPER>}
 * env vars (e.g., {@code ERP_WEBHOOK_ORDER_SECRET_ERP_PROD} for source
 * {@code erp-prod}). A Secret Manager-backed adapter is v2.
 *
 * <p>Missing env var → {@link Optional#empty()} → controller responds
 * {@code 401 WEBHOOK_SIGNATURE_INVALID}.
 */
public interface WebhookSecretPort {

    /**
     * Resolve the HMAC secret for the given ERP source identifier (e.g.,
     * {@code erp-prod}, {@code erp-stg}).
     */
    Optional<String> getSecret(String source);
}
