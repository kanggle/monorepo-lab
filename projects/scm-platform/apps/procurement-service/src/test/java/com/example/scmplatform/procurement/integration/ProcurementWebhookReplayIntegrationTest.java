package com.example.scmplatform.procurement.integration;

import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.PurchaseOrderJpaRepository;
import com.example.scmplatform.procurement.infrastructure.security.WebhookSignatureFilter;
import com.example.scmplatform.procurement.infrastructure.security.WebhookSignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT: webhook HMAC + replay protection across the REAL Redis nonce store.
 *
 * <p>Drives the supplier-ack webhook over the full HTTP stack (so the
 * {@code WebhookSignatureFilter} runs) against an already-ACKNOWLEDGED PO — the
 * application service treats a repeat ack as an idempotent 200 no-op, so the
 * test isolates the filter's signature + replay behaviour from PO state-machine
 * side effects.
 *
 * <p>Scenario:
 * <ol>
 *   <li>A valid signed delivery → 200.</li>
 *   <li>The IDENTICAL replay (same sig / ts / body) → 401 WEBHOOK_REPLAY_DETECTED
 *       (the Redis nonce remembers the first signature).</li>
 *   <li>A fresh distinct delivery (new timestamp ⇒ new signature) → 200.</li>
 * </ol>
 *
 * <p>Uses the default webhook secret {@code scm-supplier-webhook-secret}
 * (application.yml dev default; not overridden in the {@code test} profile).
 */
@Tag("integration")
@DisplayName("IT: webhook HMAC + replay protection (real Redis nonce store)")
class ProcurementWebhookReplayIntegrationTest extends AbstractProcurementIntegrationTest {

    private static final String URL = "/api/procurement/webhooks/supplier-ack";
    private static final String WEBHOOK_SECRET = "scm-supplier-webhook-secret";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private WebhookSignatureVerifier webhookSignatureVerifier;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseOrderJpaRepository poJpaForSetup;

    /**
     * Builds MockMvc with the {@link WebhookSignatureFilter} added explicitly, so
     * the filter deterministically runs in front of the controller regardless of
     * how {@code @AutoConfigureMockMvc} treats the {@code FilterRegistrationBean}
     * registration. The filter's own path guard keeps it scoped to the webhook
     * routes.
     */
    @BeforeEach
    void setUpMockMvcWithFilter() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(new WebhookSignatureFilter(webhookSignatureVerifier, objectMapper))
                .build();
    }

    // ---- helpers ----

    private static String hmacHex(String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);
            byte[] digest = mac.doFinal();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Persists a PO already in ACKNOWLEDGED status so a webhook ack is an
     * idempotent 200 no-op (no Kafka / supplier mock needed). Each
     * {@code save(...)} runs in its own repository-level transaction.
     */
    PurchaseOrder persistAcknowledgedPo() {
        Supplier supplier = persistActiveSupplier(TENANT_SCM);
        PurchaseOrder po = persistDraftPo(TENANT_SCM, supplier.getId());
        po.submit(ActorType.BUYER);
        po.acknowledge(ActorType.SUPPLIER);
        return poJpaForSetup.save(po);
    }

    private byte[] ackBody(String poId) throws Exception {
        return objectMapper.writeValueAsBytes(new java.util.LinkedHashMap<String, Object>() {{
            put("tenantId", TENANT_SCM);
            put("poId", poId);
            put("supplierAckRef", "ACK-REPLAY-IT-001");
        }});
    }

    // ---- test ----

    @Test
    @DisplayName("valid → 200; identical replay → 401 WEBHOOK_REPLAY_DETECTED; fresh → 200")
    void replayRejectedAcrossRealRedis() throws Exception {
        PurchaseOrder po = persistAcknowledgedPo();
        byte[] body = ackBody(po.getId());

        // (1) valid signed delivery → 200
        String ts1 = String.valueOf(Instant.now().getEpochSecond());
        String sig1 = hmacHex(ts1, body);
        mockMvc.perform(post(URL)
                        .header("X-Supplier-Signature", sig1)
                        .header("X-Supplier-Timestamp", ts1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(po.getId()))
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));

        // (2) identical replay (same sig + ts + body) → 401 REPLAY
        mockMvc.perform(post(URL)
                        .header("X-Supplier-Signature", sig1)
                        .header("X-Supplier-Timestamp", ts1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("WEBHOOK_REPLAY_DETECTED"));

        // (3) fresh distinct delivery (new ts ⇒ new sig ⇒ new nonce) → 200
        String ts2 = String.valueOf(Instant.now().getEpochSecond() + 1);
        String sig2 = hmacHex(ts2, body);
        mockMvc.perform(post(URL)
                        .header("X-Supplier-Signature", sig2)
                        .header("X-Supplier-Timestamp", ts2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(po.getId()));
    }
}
