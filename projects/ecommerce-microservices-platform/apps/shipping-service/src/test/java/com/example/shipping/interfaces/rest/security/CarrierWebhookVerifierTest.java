package com.example.shipping.interfaces.rest.security;

import com.example.shipping.application.exception.WebhookSignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CarrierWebhookVerifierTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String BODY = "{\"deliveryId\":\"d-1\",\"shippingId\":\"s-1\",\"status\":\"DELIVERED\"}";

    private static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void validSignature_passes() {
        CarrierWebhookVerifier verifier = new CarrierWebhookVerifier(SECRET);
        assertThatCode(() -> verifier.verify(BODY, sign(SECRET, BODY))).doesNotThrowAnyException();
    }

    @Test
    void mismatchedSignature_isRejected() {
        CarrierWebhookVerifier verifier = new CarrierWebhookVerifier(SECRET);
        assertThatThrownBy(() -> verifier.verify(BODY, sign("wrong-secret", BODY)))
                .isInstanceOf(WebhookSignatureException.class);
    }

    @Test
    void missingSignature_isRejected() {
        CarrierWebhookVerifier verifier = new CarrierWebhookVerifier(SECRET);
        assertThatThrownBy(() -> verifier.verify(BODY, null))
                .isInstanceOf(WebhookSignatureException.class);
    }

    @Test
    void unconfiguredSecret_rejectsEveryWebhook_failClosed() {
        CarrierWebhookVerifier verifier = new CarrierWebhookVerifier("");
        // a blank secret rejects every webhook regardless of the presented signature (fail-closed)
        assertThatThrownBy(() -> verifier.verify(BODY, "sha256=anything"))
                .isInstanceOf(WebhookSignatureException.class);
    }
}
