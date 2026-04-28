package com.wms.inbound.adapter.in.webhook.erp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.inbound.adapter.in.webhook.erp.dto.ErpAsnWebhookRequest;
import com.wms.inbound.adapter.in.webhook.erp.dto.ErpAsnWebhookResponse;
import com.wms.inbound.application.port.out.WebhookSecretPort;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP ASN push webhook entrypoint.
 *
 * <p>Authoritative wire contract:
 * {@code specs/contracts/webhooks/erp-asn-webhook.md}.
 *
 * <h2>Processing order (security-first)</h2>
 *
 * <ol>
 *   <li>Resolve secret via {@link WebhookSecretPort} from {@code X-Erp-Source}.
 *       Missing secret → 401 {@code WEBHOOK_SIGNATURE_INVALID}.</li>
 *   <li>{@code X-Erp-Timestamp} window check. Out-of-window → 401
 *       {@code WEBHOOK_TIMESTAMP_INVALID}.</li>
 *   <li>HMAC verification over <em>raw request bytes</em>. Mismatch → 401
 *       {@code WEBHOOK_SIGNATURE_INVALID}.</li>
 *   <li>JSON parse + bean validation. Failure → 422 {@code VALIDATION_ERROR}.</li>
 *   <li>Atomic dedupe + inbox write via {@link ErpWebhookIngestService}.
 *       Conflict → 200 {@code ignored_duplicate}; first delivery → 200
 *       {@code accepted}.</li>
 * </ol>
 *
 * <h2>Why {@code byte[]} body</h2>
 *
 * <p>HMAC must be computed over the exact bytes received on the wire (per
 * {@code erp-asn-webhook.md} § Signature Computation — body bytes, not a
 * re-serialised JSON). Re-serialisation changes whitespace and breaks
 * verification. The {@code @RequestBody byte[]} parameter gives Spring the
 * raw body without parsing it as JSON; we manually parse + validate inside
 * the controller after the signature passes.
 */
@RestController
@RequestMapping("/webhooks/erp")
public class ErpAsnWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ErpAsnWebhookController.class);

    private final HmacSignatureVerifier hmacVerifier;
    private final TimestampWindowVerifier timestampVerifier;
    private final WebhookSecretPort secretPort;
    private final ErpWebhookIngestService ingestService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ErpAsnWebhookController(HmacSignatureVerifier hmacVerifier,
                                   TimestampWindowVerifier timestampVerifier,
                                   WebhookSecretPort secretPort,
                                   ErpWebhookIngestService ingestService,
                                   ObjectMapper objectMapper,
                                   Validator validator) {
        this.hmacVerifier = hmacVerifier;
        this.timestampVerifier = timestampVerifier;
        this.secretPort = secretPort;
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping(value = "/asn", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receive(
            @RequestBody(required = false) byte[] rawBody,
            @RequestHeader(value = "X-Erp-Event-Id", required = false) String eventId,
            @RequestHeader(value = "X-Erp-Timestamp", required = false) String timestampHeader,
            @RequestHeader(value = "X-Erp-Signature", required = false) String signatureHeader,
            @RequestHeader(value = "X-Erp-Source", required = false) String source) {

        if (eventId == null || eventId.isBlank()) {
            // Required header — treat absence as schema validation failure
            // (matches the controller's defensive posture: don't accept a
            // request that can't be deduplicated).
            return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "X-Erp-Event-Id header is required");
        }

        MDC.put("eventId", eventId);
        if (source != null) {
            MDC.put("source", source);
        }
        try {
            // Step 1: timestamp window. Per spec ordering, timestamp comes
            // before signature so we don't waste HMAC compute on stale calls.
            if (!timestampVerifier.isWithinWindow(timestampHeader)) {
                log.warn("webhook_timestamp_invalid eventId={} source={}", eventId, source);
                return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_TIMESTAMP_INVALID",
                        "X-Erp-Timestamp missing or outside acceptance window");
            }

            // Step 2: secret + HMAC. Missing secret means we cannot verify; per
            // spec failure-mode "Source header references unknown env" → 401
            // signature_invalid.
            Optional<String> secret = secretPort.resolveSecret(source);
            if (secret.isEmpty()) {
                log.warn("webhook_signature_invalid eventId={} source={} reason=secret_missing",
                        eventId, source);
                return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
                        "HMAC signature mismatch");
            }
            byte[] body = rawBody == null ? new byte[0] : rawBody;
            if (!hmacVerifier.verify(body, secret.get(), signatureHeader)) {
                log.warn("webhook_signature_invalid eventId={} source={}", eventId, source);
                return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
                        "HMAC signature mismatch");
            }

            // Step 3: parse + validate body schema.
            ErpAsnWebhookRequest parsed;
            try {
                parsed = objectMapper.readValue(body, ErpAsnWebhookRequest.class);
            } catch (JsonProcessingException e) {
                return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                        "Webhook payload is not valid JSON");
            } catch (Exception e) {
                return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                        "Webhook payload is malformed");
            }
            Set<ConstraintViolation<ErpAsnWebhookRequest>> violations = validator.validate(parsed);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .collect(Collectors.joining("; "));
                return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message);
            }

            // Step 4: dedupe + inbox in one TX.
            String rawPayloadJson = new String(body, StandardCharsets.UTF_8);
            ErpWebhookIngestService.Result result = ingestService.ingest(
                    eventId, rawPayloadJson, signatureHeader, source);
            if (result instanceof ErpWebhookIngestService.Result.Duplicate dup) {
                return ResponseEntity.ok(
                        ErpAsnWebhookResponse.ignoredDuplicate(eventId, dup.previouslyReceivedAt()));
            }
            ErpWebhookIngestService.Result.Accepted acc = (ErpWebhookIngestService.Result.Accepted) result;
            return ResponseEntity.ok(
                    ErpAsnWebhookResponse.accepted(eventId, parsed.asnNo(), acc.receivedAt()));
        } finally {
            MDC.remove("eventId");
            MDC.remove("source");
        }
    }

    private static ResponseEntity<ApiErrorEnvelope> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorEnvelope.of(code, message));
    }
}
