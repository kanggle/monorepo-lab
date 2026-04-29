package com.wms.outbound.adapter.in.webhook.erp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Computes and verifies the HMAC-SHA256 signature carried in the
 * {@code X-Erp-Signature} header.
 *
 * <p>Per {@code specs/contracts/webhooks/erp-order-webhook.md} § Signature
 * Computation:
 * <pre>
 *   signature = "sha256=" + lower(hex(HMAC_SHA256(secret, raw_request_body)))
 * </pre>
 *
 * <p>The header comparison uses {@link MessageDigest#isEqual(byte[], byte[])}
 * for constant-time compare.
 */
@Component
public class HmacVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String HEADER_PREFIX = "sha256=";

    public String compute(byte[] body, String secret) {
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("secret must not be empty");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hmac = mac.doFinal(body);
            return HEADER_PREFIX + HexFormat.of().withLowerCase().formatHex(hmac);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalArgumentException("invalid HMAC secret", e);
        }
    }

    public boolean verify(byte[] body, String secret, String receivedSignature) {
        if (receivedSignature == null || receivedSignature.isBlank()) {
            return false;
        }
        if (!receivedSignature.startsWith(HEADER_PREFIX)) {
            return false;
        }
        String expected = compute(body, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                receivedSignature.getBytes(StandardCharsets.UTF_8));
    }
}
