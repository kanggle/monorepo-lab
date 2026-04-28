package com.wms.inbound.application.port.out;

import java.util.Optional;

/**
 * Out-port resolving the HMAC verification secret for an incoming ERP webhook.
 *
 * <p>Per {@code specs/contracts/webhooks/erp-asn-webhook.md} § Signature
 * Computation, the {@code X-Erp-Source} header selects which per-environment
 * secret should be used.
 *
 * <p>v1 implementation reads {@code ERP_WEBHOOK_SECRET_<SOURCE_UPPER>} env vars
 * (e.g., {@code ERP_WEBHOOK_SECRET_ERP_PROD} for source {@code erp-prod}). A
 * Secret Manager-backed adapter is v2 and intentionally not implemented here.
 *
 * <p>Missing env var → {@link Optional#empty()} → controller responds
 * {@code 401 WEBHOOK_SIGNATURE_INVALID} ("no secret to verify against").
 */
public interface WebhookSecretPort {

    /**
     * Resolve the HMAC secret for the given ERP source identifier (e.g.,
     * {@code erp-prod}, {@code erp-stg}).
     *
     * @param source value of the {@code X-Erp-Source} header
     * @return secret bytes (UTF-8) or empty if no secret is configured
     */
    Optional<String> resolveSecret(String source);
}
