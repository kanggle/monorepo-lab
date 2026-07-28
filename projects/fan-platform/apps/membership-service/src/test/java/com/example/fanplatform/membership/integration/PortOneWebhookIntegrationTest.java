package com.example.fanplatform.membership.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Webhook endpoint over HTTP (ADR-002 §D3). Proves the trust gate end-to-end with
 * a <b>synthetically-signed</b> payload (there is no live PortOne delivery in dev —
 * see {@code PortOneWebhookController} javadoc): a valid HMAC signature is accepted
 * (200) with NO JWT (the public bypass), and a tampered/absent signature is 401.
 */
class PortOneWebhookIntegrationTest extends MembershipServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Value("${fan.payment.portone.webhook-secret}")
    String webhookSecret;

    @BeforeEach
    void clean() {
        truncateAll();
    }

    @AfterEach
    void cleanUp() {
        truncateAll();
    }

    private ResponseEntity<String> post(HttpHeaders headers, String body) {
        return rest.exchange("http://localhost:" + port + "/webhooks/portone",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders signedHeaders(String id, String ts, String body) throws Exception {
        String raw = webhookSecret.startsWith("whsec_") ? webhookSecret.substring("whsec_".length()) : webhookSecret;
        byte[] key = Base64.getDecoder().decode(raw);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        String signedContent = id + "." + ts + "." + body;
        String sig = Base64.getEncoder().encodeToString(
                mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("webhook-id", id);
        h.set("webhook-timestamp", ts);
        h.set("webhook-signature", "v1," + sig);
        return h;
    }

    @Test
    @DisplayName("valid signature, NO JWT → 200 (public bypass + authentic message ack)")
    void validSignatureAccepted() throws Exception {
        String body = "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"pay-unknown\"}}";
        HttpHeaders headers = signedHeaders("wh-1", "1720742400", body);

        ResponseEntity<String> resp = post(headers, body);

        // Unknown paymentId → ack (no membership carries it) — still a 200, not an error.
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("tampered body (signature no longer matches) → 401")
    void tamperedBodyRejected() throws Exception {
        String signedBody = "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"pay-1\"}}";
        HttpHeaders headers = signedHeaders("wh-2", "1720742400", signedBody);

        // Post a DIFFERENT body than what was signed.
        ResponseEntity<String> resp = post(headers, "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"pay-TAMPERED\"}}");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("no signature headers → 401")
    void missingSignatureRejected() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = post(h, "{\"type\":\"Transaction.Paid\"}");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
