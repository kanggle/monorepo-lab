package com.wms.inbound.adapter.in.webhook.erp;

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
 * <p>Per {@code specs/contracts/webhooks/erp-asn-webhook.md} § Signature
 * Computation:
 * <pre>
 *   signature = "sha256=" + lower(hex(HMAC_SHA256(secret, raw_request_body)))
 * </pre>
 *
 * <h2>Critical contract</h2>
 *
 * <p>The signature is over the <em>raw request body bytes</em> — never over
 * a re-serialised JSON. Whitespace differences from JSON re-serialisation
 * break the signature. The webhook controller captures the raw bytes via
 * {@link org.springframework.web.util.ContentCachingRequestWrapper} (wired by
 * {@code ContentCachingRequestFilter}) and passes them in.
 *
 * <p>The header comparison uses {@link MessageDigest#isEqual(byte[], byte[])}
 * for constant-time compare — never {@code String.equals} (timing attack).
 *
 * <p>Uppercase-hex headers fail verification because the expected signature is
 * always lowercase per spec; the byte-by-byte compare rejects them.
 */
@Component
public class HmacSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String HEADER_PREFIX = "sha256=";

    /**
     * Compute the canonical signature string for {@code body} using {@code secret}.
     *
     * @return {@code "sha256=" + lowercase-hex} of the HMAC-SHA256
     */
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
            // HmacSHA256 is a JDK-mandated algorithm; absence is fatal.
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalArgumentException("invalid HMAC secret", e);
        }
    }

    /**
     * Constant-time compare {@code receivedSignature} (header value) against
     * the signature recomputed from {@code body} + {@code secret}.
     *
     * @return {@code true} when the signatures match exactly (bytewise),
     *         {@code false} for any mismatch including malformed prefix or
     *         uppercase hex
     */
    public boolean verify(byte[] body, String secret, String receivedSignature) {
        if (receivedSignature == null || receivedSignature.isBlank()) {
            return false;
        }
        if (!receivedSignature.startsWith(HEADER_PREFIX)) {
            return false;
        }
        String expected = compute(body, secret);
        // Constant-time compare; both are ASCII so UTF-8 byte length matches the
        // logical length, and a timing-side-channel via early-exit comparison is
        // not exposed.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                receivedSignature.getBytes(StandardCharsets.UTF_8));
    }
}
