package com.example.scmplatform.procurement.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

/**
 * Pure unit tests for {@link WebhookSignatureVerifier} — no Spring context.
 *
 * <p>The verifier is built directly with a known secret, a 300s window, a fake
 * {@link SeenSignatureStore}, and a FIXED {@link Clock} so the freshness check
 * is deterministic.
 *
 * <p>Test count: 11
 */
class WebhookSignatureVerifierTest {

    private static final String SECRET = "unit-test-secret";
    private static final long WINDOW = 300;
    private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");
    private static final byte[] BODY = "{\"tenantId\":\"scm\",\"poId\":\"po-1\"}".getBytes(StandardCharsets.UTF_8);

    private RecordingStore store;
    private WebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        store = new RecordingStore(true);
        verifier = new WebhookSignatureVerifier(
                SECRET, WINDOW, store, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- helpers ----

    /** Computes the valid lowercase-hex signature for (timestamp, body, secret). */
    private static String sign(String timestamp, byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);
            return toHex(mac.doFinal());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String nowTs() {
        return String.valueOf(NOW.getEpochSecond());
    }

    private void assertReason(Runnable call, String expectedCode) {
        assertThatThrownBy(call::run)
                .asInstanceOf(type(WebhookVerificationException.class))
                .extracting(WebhookVerificationException::getCode)
                .isEqualTo(expectedCode);
    }

    // ---- tests ----

    @Test
    @DisplayName("valid signature + fresh timestamp → passes and records the nonce")
    void validPasses() {
        String ts = nowTs();
        String sig = sign(ts, BODY, SECRET);

        assertThatCode(() -> verifier.verify(BODY, ts, sig)).doesNotThrowAnyException();
        assertThat(store.lastMarked).isEqualTo(sig);
        assertThat(store.lastTtl).isEqualTo(Duration.ofSeconds(WINDOW + 60));
    }

    @Test
    @DisplayName("wrong signature → WEBHOOK_SIGNATURE_INVALID")
    void wrongSignature() {
        String ts = nowTs();
        String wrong = sign(ts, BODY, "different-secret");

        assertReason(() -> verifier.verify(BODY, ts, wrong), "WEBHOOK_SIGNATURE_INVALID");
        assertThat(store.lastMarked).as("bad sig must not poison the store").isNull();
    }

    @Test
    @DisplayName("null signature → WEBHOOK_SIGNATURE_INVALID")
    void nullSignature() {
        assertReason(() -> verifier.verify(BODY, nowTs(), null), "WEBHOOK_SIGNATURE_INVALID");
    }

    @Test
    @DisplayName("blank signature → WEBHOOK_SIGNATURE_INVALID")
    void blankSignature() {
        assertReason(() -> verifier.verify(BODY, nowTs(), "   "), "WEBHOOK_SIGNATURE_INVALID");
    }

    @Nested
    @DisplayName("timestamp validation")
    class TimestampValidation {

        @Test
        @DisplayName("null timestamp → WEBHOOK_TIMESTAMP_INVALID")
        void nullTimestamp() {
            String sig = sign(nowTs(), BODY, SECRET);
            assertReason(() -> verifier.verify(BODY, null, sig), "WEBHOOK_TIMESTAMP_INVALID");
        }

        @Test
        @DisplayName("blank timestamp → WEBHOOK_TIMESTAMP_INVALID")
        void blankTimestamp() {
            String sig = sign(nowTs(), BODY, SECRET);
            assertReason(() -> verifier.verify(BODY, "  ", sig), "WEBHOOK_TIMESTAMP_INVALID");
        }

        @Test
        @DisplayName("non-numeric timestamp → WEBHOOK_TIMESTAMP_INVALID")
        void nonNumericTimestamp() {
            String sig = sign("not-a-number", BODY, SECRET);
            assertReason(() -> verifier.verify(BODY, "not-a-number", sig), "WEBHOOK_TIMESTAMP_INVALID");
        }

        @Test
        @DisplayName("stale timestamp (now - ts > window) → WEBHOOK_TIMESTAMP_INVALID")
        void staleTimestamp() {
            String stale = String.valueOf(NOW.getEpochSecond() - WINDOW - 1);
            String sig = sign(stale, BODY, SECRET);
            assertReason(() -> verifier.verify(BODY, stale, sig), "WEBHOOK_TIMESTAMP_INVALID");
        }

        @Test
        @DisplayName("future timestamp (ts - now > window) → WEBHOOK_TIMESTAMP_INVALID")
        void futureTimestamp() {
            String future = String.valueOf(NOW.getEpochSecond() + WINDOW + 1);
            String sig = sign(future, BODY, SECRET);
            assertReason(() -> verifier.verify(BODY, future, sig), "WEBHOOK_TIMESTAMP_INVALID");
        }

        @Test
        @DisplayName("timestamp at the window edge → passes")
        void edgeOfWindowPasses() {
            String edge = String.valueOf(NOW.getEpochSecond() - WINDOW);
            String sig = sign(edge, BODY, SECRET);
            assertThatCode(() -> verifier.verify(BODY, edge, sig)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("replay (store reports nonce already seen) → WEBHOOK_REPLAY_DETECTED")
    void replayDetected() {
        store = new RecordingStore(false); // markIfFresh → false (already seen)
        verifier = new WebhookSignatureVerifier(
                SECRET, WINDOW, store, Clock.fixed(NOW, ZoneOffset.UTC));
        String ts = nowTs();
        String sig = sign(ts, BODY, SECRET);

        assertReason(() -> verifier.verify(BODY, ts, sig), "WEBHOOK_REPLAY_DETECTED");
    }

    @Test
    @DisplayName("tampered body (valid sig for a DIFFERENT body) → WEBHOOK_SIGNATURE_INVALID")
    void tamperedBody() {
        String ts = nowTs();
        byte[] otherBody = "{\"tenantId\":\"scm\",\"poId\":\"po-2\"}".getBytes(StandardCharsets.UTF_8);
        String sigForOther = sign(ts, otherBody, SECRET);

        assertReason(() -> verifier.verify(BODY, ts, sigForOther), "WEBHOOK_SIGNATURE_INVALID");
    }

    @Test
    @DisplayName("odd-length / invalid hex signature → WEBHOOK_SIGNATURE_INVALID")
    void invalidHexSignature() {
        String ts = nowTs();
        assertReason(() -> verifier.verify(BODY, ts, "abc"), "WEBHOOK_SIGNATURE_INVALID");    // odd length
        assertReason(() -> verifier.verify(BODY, ts, "zzzz"), "WEBHOOK_SIGNATURE_INVALID");   // non-hex chars
    }

    // ---- fake store ----

    private static final class RecordingStore implements SeenSignatureStore {
        private final boolean fresh;
        private String lastMarked;
        private Duration lastTtl;

        private RecordingStore(boolean fresh) {
            this.fresh = fresh;
        }

        @Override
        public boolean markIfFresh(String signatureHex, Duration ttl) {
            this.lastMarked = signatureHex;
            this.lastTtl = ttl;
            return fresh;
        }
    }
}
