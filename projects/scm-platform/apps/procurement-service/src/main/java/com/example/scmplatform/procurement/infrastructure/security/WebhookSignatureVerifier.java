package com.example.scmplatform.procurement.infrastructure.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;

/**
 * Verifies inbound supplier webhooks via HMAC-SHA256 over the raw request body,
 * a signed timestamp (freshness window), and signature-nonce replay rejection.
 *
 * <p>The signing input is {@code timestamp + "." + rawBody}, keyed with the
 * shared secret. The {@code X-Supplier-Signature} header carries the
 * lowercase-hex digest; {@code X-Supplier-Timestamp} carries the epoch-seconds
 * signing time. Verification order: signature presence → timestamp validity →
 * freshness → HMAC (constant-time) → replay. Replay is checked LAST, only after
 * the signature and timestamp are proven valid, so a forged signature cannot
 * poison the replay store.
 *
 * <p>Pure and unit-testable: the replay store and clock are injected. No HTTP /
 * servlet types appear here — {@link WebhookSignatureFilter} adapts the request.
 */
@Component
public class WebhookSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    private final long freshnessSeconds;
    private final SeenSignatureStore store;
    private final Clock clock;

    public WebhookSignatureVerifier(
            @Value("${scmplatform.procurement.supplier.webhook-secret:scm-supplier-webhook-secret}")
            String secret,
            @Value("${scmplatform.procurement.supplier.webhook-freshness-seconds:300}")
            long freshnessSeconds,
            SeenSignatureStore store,
            @Qualifier("webhookClock") Clock clock) {
        this.secret = secret;
        this.freshnessSeconds = freshnessSeconds;
        this.store = store;
        this.clock = clock;
    }

    /**
     * Verifies a webhook delivery. Throws {@link WebhookVerificationException}
     * (carrying one of the three reason codes) on any failure.
     *
     * @param rawBody         the raw request body bytes (pre-deserialization)
     * @param timestampHeader value of {@code X-Supplier-Timestamp} (epoch seconds)
     * @param signatureHeader value of {@code X-Supplier-Signature} (lowercase hex)
     */
    public void verify(byte[] rawBody, String timestampHeader, String signatureHeader) {
        // (a) signature must be present.
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new WebhookVerificationException("WEBHOOK_SIGNATURE_INVALID");
        }

        // (b) timestamp must be present and numeric.
        long ts;
        if (timestampHeader == null || timestampHeader.isBlank()) {
            throw new WebhookVerificationException("WEBHOOK_TIMESTAMP_INVALID");
        }
        try {
            ts = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            throw new WebhookVerificationException("WEBHOOK_TIMESTAMP_INVALID");
        }

        // (c) freshness window (covers both stale and future-skewed deliveries).
        long nowEpochSec = clock.instant().getEpochSecond();
        if (Math.abs(nowEpochSec - ts) > freshnessSeconds) {
            throw new WebhookVerificationException("WEBHOOK_TIMESTAMP_INVALID");
        }

        // (d) compute the expected HMAC over (timestamp + "." + rawBody).
        byte[] expected = computeHmac(timestampHeader, rawBody);

        // (e) decode the provided hex signature.
        String providedHex = signatureHeader.trim().toLowerCase(java.util.Locale.ROOT);
        byte[] providedBytes = decodeHex(providedHex);

        // (f) constant-time compare. MessageDigest.isEqual is the constant-time
        // primitive — it does NOT short-circuit on the first differing byte.
        if (!MessageDigest.isEqual(expected, providedBytes)) {
            throw new WebhookVerificationException("WEBHOOK_SIGNATURE_INVALID");
        }

        // (g) replay LAST — only after sig + ts are valid, so a bad signature
        // cannot poison the store. TTL = window + 60s so a replay cannot outlive
        // the nonce memory.
        if (!store.markIfFresh(providedHex, Duration.ofSeconds(freshnessSeconds + 60))) {
            throw new WebhookVerificationException("WEBHOOK_REPLAY_DETECTED");
        }
    }

    private byte[] computeHmac(String timestampHeader, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            mac.update(timestampHeader.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(rawBody);
            return mac.doFinal();
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is a JCA-mandated algorithm; this is unreachable in
            // practice. Treat any crypto-provider failure as a signature failure
            // rather than leaking a 500 from the filter.
            throw new WebhookVerificationException("WEBHOOK_SIGNATURE_INVALID");
        }
    }

    /**
     * Decodes a lowercase-hex string to bytes. Odd length or any non-hex
     * character → {@code WEBHOOK_SIGNATURE_INVALID}.
     */
    private static byte[] decodeHex(String hex) {
        int len = hex.length();
        if (len == 0 || (len & 1) == 1) {
            throw new WebhookVerificationException("WEBHOOK_SIGNATURE_INVALID");
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new WebhookVerificationException("WEBHOOK_SIGNATURE_INVALID");
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
