package com.example.shipping.interfaces.rest.security;

import com.example.shipping.application.exception.WebhookSignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Authenticates inbound carrier webhooks (TASK-BE-294) by verifying an HMAC-SHA256
 * signature over the raw request body against the shared secret
 * {@code shipping.carrier.webhook.secret}. The expected header form is
 * {@code X-Carrier-Signature: sha256=<lowercase-hex>}.
 *
 * <p><b>Fail-closed / net-zero.</b> When the secret is unset/blank the verifier rejects
 * <em>every</em> webhook (the integration is off by default — no shipment can be mutated
 * without explicit configuration). A missing or mismatched signature is rejected with a
 * constant-time comparison ({@link MessageDigest#isEqual}). Every rejection raises
 * {@link WebhookSignatureException} (→ HTTP 401) without revealing which check failed.
 */
@Slf4j
@Component
public class CarrierWebhookVerifier {

    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;

    public CarrierWebhookVerifier(@Value("${shipping.carrier.webhook.secret:}") String secret) {
        this.secret = secret;
    }

    public void verify(String rawBody, String signatureHeader) {
        if (secret == null || secret.isBlank()) {
            log.warn("Carrier webhook secret not configured; rejecting webhook (fail-closed)");
            throw new WebhookSignatureException("Carrier webhook verification not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new WebhookSignatureException("Missing carrier webhook signature");
        }
        String expected = SIGNATURE_PREFIX + hmacSha256Hex(secret, rawBody == null ? "" : rawBody);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = signatureHeader.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            throw new WebhookSignatureException("Carrier webhook signature mismatch");
        }
    }

    private static String hmacSha256Hex(String secret, String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            // HmacSHA256 is always available on a standard JRE; treat any failure as a rejection.
            throw new WebhookSignatureException("Carrier webhook signature computation failed");
        }
    }
}
