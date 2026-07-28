package com.example.fanplatform.membership.presentation.controller;

import com.example.fanplatform.membership.application.billing.WebhookReconcileUseCase;
import com.example.libs.payment.portone.PortOneWebhookVerifier;
import com.example.libs.payment.portone.PortOneWebhookVerifier.VerifiedWebhookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * PortOne V2 webhook receiver (ADR-002 §D3). A public, unauthenticated surface —
 * {@code /webhooks/portone} is in {@code PublicPaths.EXACT}, so it bypasses BOTH
 * the end-user auth chain AND tenant-claim enforcement. That is correct: PortOne
 * cannot present a fan JWT; the webhook's OWN auth is the HMAC signature verified
 * here, not Spring Security.
 *
 * <h2>Trust discipline</h2>
 * <ol>
 *   <li>The raw request body is read as an untouched {@code String} (NOT
 *       auto-deserialised) — re-serialising parsed JSON would change the bytes and
 *       break the HMAC.</li>
 *   <li>{@link PortOneWebhookVerifier#verify} → {@link Optional#empty()} (missing /
 *       tampered / forged signature) yields <b>401 before any payload processing</b>.</li>
 *   <li>A present {@link VerifiedWebhookEvent} proves only authenticity, NOT that
 *       money moved. Its amount/status are never trusted; the payload's
 *       {@code paymentId} is handed to {@link WebhookReconcileUseCase}, which
 *       re-derives the truth via the existing {@code verify(paymentId, ...)}. Return
 *       200 once processed — even a duplicate/irrelevant delivery is a 200, not an
 *       error.</li>
 * </ol>
 *
 * <h2>NOT live-verified (honest limitation)</h2>
 * This endpoint <b>cannot be exercised by a real PortOne webhook in local/dev</b> —
 * there is no public URL reachable from the internet, and (per
 * {@link PortOneWebhookVerifier}'s own note) the exact header names / signature
 * format / secret prefix MUST be reconfirmed against PortOne's current V2 reference
 * before a live wiring. It is proven by an integration test posting a
 * synthetically-signed payload directly, NOT by live end-to-end delivery — same
 * honesty standard as TASK-MONO-482's "not live-verified" notes.
 */
@Slf4j
@RestController
public class PortOneWebhookController {

    private final PortOneWebhookVerifier verifier;
    private final WebhookReconcileUseCase reconcileUseCase;
    private final String webhookSecret;

    public PortOneWebhookController(
            PortOneWebhookVerifier verifier,
            WebhookReconcileUseCase reconcileUseCase,
            @Value("${fan.payment.portone.webhook-secret}") String webhookSecret) {
        this.verifier = verifier;
        this.reconcileUseCase = reconcileUseCase;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhooks/portone")
    public ResponseEntity<Void> receive(
            @RequestBody(required = false) String rawBody,
            @RequestHeader Map<String, String> headers) {
        Optional<VerifiedWebhookEvent> event = verifier.verify(webhookSecret, rawBody, headers);
        if (event.isEmpty()) {
            // Invalid / missing / tampered signature — reject before touching the payload.
            log.warn("PortOne webhook: signature verification failed -> 401");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Authentic. Reconcile via verify(paymentId, ...) — never trust the payload's fields.
        reconcileUseCase.reconcileByPaymentId(event.get().paymentId());
        return ResponseEntity.ok().build();
    }
}
