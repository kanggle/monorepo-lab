package com.example.scmplatform.procurement.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * Verifies the {@code X-Supplier-Signature} shared-secret header for inbound
 * supplier webhooks (PO-ack and ASN delivery).
 *
 * <p>Extracted from {@code AsnWebhookController} / {@code SupplierAckWebhookController}
 * per architecture rule L3 (infrastructure concern must not live in the
 * presentation layer). Both controllers inject this bean and delegate the
 * signature check — they contain no verification logic themselves.
 *
 * <p>v1 security note: the check is a fixed shared-secret comparison.
 * HMAC + timestamp + replay protection is tracked as a v2 follow-up
 * (rules/traits/integration-heavy.md I6 — currently "Partial").
 */
@Component
public class WebhookSignatureVerifier {

    @Value("${scmplatform.procurement.supplier.webhook-secret:scm-supplier-webhook-secret}")
    private String webhookSecret;

    /**
     * Throws {@code 401 UNAUTHORIZED} with code {@code WEBHOOK_SIGNATURE_INVALID}
     * if {@code signature} does not match the configured shared secret.
     *
     * @param signature value of the {@code X-Supplier-Signature} request header
     *                  (may be {@code null} when the header is absent)
     */
    public void verify(String signature) {
        if (!Objects.equals(signature, webhookSecret)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "WEBHOOK_SIGNATURE_INVALID");
        }
    }
}
