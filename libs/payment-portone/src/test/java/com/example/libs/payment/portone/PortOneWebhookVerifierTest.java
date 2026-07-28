package com.example.libs.payment.portone;

import com.example.libs.payment.portone.PortOneWebhookVerifier.VerifiedWebhookEvent;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PortOneWebhookVerifier} — Standard-Webhooks HMAC verification matrix. The verifier's ONE
 * job is proving a webhook is authentically from PortOne; these tests assert it accepts a validly
 * signed payload and rejects (returns {@link Optional#empty()}, never throws) every unauthentic
 * shape: missing header, wrong signature, tampered body, wrong secret.
 */
class PortOneWebhookVerifierTest {

    private static final String KEY_MATERIAL = "this-is-a-shared-webhook-signing-key";
    // Standard-Webhooks secret: base64 key material, conventionally whsec_-prefixed.
    private static final String SECRET =
            "whsec_" + java.util.Base64.getEncoder().encodeToString(KEY_MATERIAL.getBytes(StandardCharsets.UTF_8));

    private static final String ID = "msg_2abc";
    private static final String TIMESTAMP = "1690000000";
    private static final String BODY =
            "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"pay-abc\",\"transactionId\":\"tx-1\"}}";

    private final PortOneWebhookVerifier verifier = new PortOneWebhookVerifier();

    /** Mirror of the production signing so the test builds a genuinely valid signature. */
    private static String sign(String id, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY_MATERIAL.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(digest);
    }

    private static Map<String, String> headers(String id, String timestamp, String signature) {
        Map<String, String> h = new HashMap<>();
        if (id != null) {
            h.put("webhook-id", id);
        }
        if (timestamp != null) {
            h.put("webhook-timestamp", timestamp);
        }
        if (signature != null) {
            h.put("webhook-signature", signature);
        }
        return h;
    }

    @Test
    void acceptsValidlySignedPayloadAndExtractsRoutingFields() throws Exception {
        Map<String, String> h = headers(ID, TIMESTAMP, "v1," + sign(ID, TIMESTAMP, BODY));

        Optional<VerifiedWebhookEvent> result = verifier.verify(SECRET, BODY, h);

        assertThat(result).isPresent();
        assertThat(result.get().paymentId()).isEqualTo("pay-abc");
        assertThat(result.get().eventType()).isEqualTo("Transaction.Paid");
    }

    @Test
    void acceptsWhenHeaderNamesDifferInCase() throws Exception {
        Map<String, String> h = new HashMap<>();
        h.put("Webhook-Id", ID);
        h.put("Webhook-Timestamp", TIMESTAMP);
        h.put("Webhook-Signature", "v1," + sign(ID, TIMESTAMP, BODY));

        assertThat(verifier.verify(SECRET, BODY, h)).isPresent();
    }

    @Test
    void acceptsWhenSignatureHeaderCarriesMultipleTokens() throws Exception {
        // Secret rotation: several `v1,<sig>` tokens space-separated; a match against any is valid.
        String good = "v1," + sign(ID, TIMESTAMP, BODY);
        Map<String, String> h = headers(ID, TIMESTAMP, "v1,AAAAstaleAAAA " + good);

        assertThat(verifier.verify(SECRET, BODY, h)).isPresent();
    }

    @Test
    void rejectsWhenSignatureHeaderMissing() {
        assertThat(verifier.verify(SECRET, BODY, headers(ID, TIMESTAMP, null))).isEmpty();
    }

    @Test
    void rejectsWhenIdHeaderMissing() throws Exception {
        assertThat(verifier.verify(SECRET, BODY, headers(null, TIMESTAMP, "v1," + sign(ID, TIMESTAMP, BODY))))
                .isEmpty();
    }

    @Test
    void rejectsWrongSignature() {
        assertThat(verifier.verify(SECRET, BODY, headers(ID, TIMESTAMP, "v1,not-the-real-signature"))).isEmpty();
    }

    @Test
    void rejectsTamperedBody() throws Exception {
        // Sign the original body, then verify against a tampered one — the HMAC no longer matches.
        String signature = "v1," + sign(ID, TIMESTAMP, BODY);
        String tampered = BODY.replace("pay-abc", "pay-attacker");
        assertThat(verifier.verify(SECRET, tampered, headers(ID, TIMESTAMP, signature))).isEmpty();
    }

    @Test
    void rejectsWhenSignedWithWrongSecret() throws Exception {
        // A signature computed over the right content but a different key must not verify.
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("attacker-key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String forged = java.util.Base64.getEncoder()
                .encodeToString(mac.doFinal((ID + "." + TIMESTAMP + "." + BODY).getBytes(StandardCharsets.UTF_8)));
        assertThat(verifier.verify(SECRET, BODY, headers(ID, TIMESTAMP, "v1," + forged))).isEmpty();
    }

    @Test
    void rejectsMalformedSecretWithoutThrowing() throws Exception {
        // A non-base64 secret cannot yield a key → reject, never throw.
        String badSecret = "whsec_!!!not-base64!!!";
        assertThat(verifier.verify(badSecret, BODY, headers(ID, TIMESTAMP, "v1," + sign(ID, TIMESTAMP, BODY))))
                .isEmpty();
    }
}
