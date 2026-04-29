package com.wms.outbound.adapter.in.webhook.erp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.outbound.adapter.in.webhook.erp.dto.ErpOrderWebhookRequest;
import com.wms.outbound.adapter.in.webhook.erp.dto.WebhookAckResponse;
import com.wms.outbound.adapter.out.persistence.adapter.WebhookInboxPersistenceAdapter;
import com.wms.outbound.application.port.out.WebhookSecretPort;
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
 * ERP order push webhook entrypoint.
 *
 * <p>Authoritative wire contract:
 * {@code specs/contracts/webhooks/erp-order-webhook.md}.
 *
 * <h2>Processing order (spec: erp-order-webhook.md § Processing Order)</h2>
 *
 * <ol>
 *   <li>Resolve secret via {@link WebhookSecretPort} from {@code X-Erp-Source}.
 *       Unknown source → 401 {@code WEBHOOK_SIGNATURE_INVALID} before timestamp
 *       is evaluated.</li>
 *   <li>{@code X-Erp-Timestamp} window check. Out-of-window → 401
 *       {@code WEBHOOK_TIMESTAMP_INVALID}.</li>
 *   <li>HMAC verification over <em>raw request bytes</em>. Mismatch → 401
 *       {@code WEBHOOK_SIGNATURE_INVALID}.</li>
 *   <li>JSON parse + bean validation. Failure → 422 {@code VALIDATION_ERROR}.</li>
 *   <li>Atomic dedupe + inbox write via
 *       {@link WebhookInboxPersistenceAdapter}. Conflict → 200
 *       {@code ignored_duplicate}; first delivery → 200 {@code accepted}.</li>
 * </ol>
 *
 * <p>Logging: only event-id + source + sanitized status are logged. Raw body
 * and signature value are NEVER logged (per AC-19).
 */
@RestController
@RequestMapping("/webhooks/erp")
public class ErpOrderWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ErpOrderWebhookController.class);

    private final HmacVerifier hmacVerifier;
    private final TimestampWindowValidator timestampValidator;
    private final WebhookSecretPort secretPort;
    private final WebhookInboxPersistenceAdapter inboxAdapter;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ErpOrderWebhookController(HmacVerifier hmacVerifier,
                                     TimestampWindowValidator timestampValidator,
                                     WebhookSecretPort secretPort,
                                     WebhookInboxPersistenceAdapter inboxAdapter,
                                     ObjectMapper objectMapper,
                                     Validator validator) {
        this.hmacVerifier = hmacVerifier;
        this.timestampValidator = timestampValidator;
        this.secretPort = secretPort;
        this.inboxAdapter = inboxAdapter;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping(value = "/order", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receive(
            @RequestBody(required = false) byte[] rawBody,
            @RequestHeader(value = "X-Erp-Event-Id", required = false) String eventId,
            @RequestHeader(value = "X-Erp-Timestamp", required = false) String timestampHeader,
            @RequestHeader(value = "X-Erp-Signature", required = false) String signatureHeader,
            @RequestHeader(value = "X-Erp-Source", required = false) String source) {

        if (eventId == null || eventId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "X-Erp-Event-Id header is required");
        }

        MDC.put("eventId", eventId);
        if (source != null) {
            MDC.put("source", source);
        }
        try {
            // Step 1: resolve secret from X-Erp-Source.
            // Unknown source → 401 WEBHOOK_SIGNATURE_INVALID before timestamp is evaluated.
            Optional<String> secret = secretPort.getSecret(source);
            if (secret.isEmpty()) {
                log.warn("webhook_signature_invalid eventId={} source={} reason=secret_missing",
                        eventId, source);
                return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
                        "HMAC signature mismatch");
            }

            // Step 2: timestamp window check.
            if (!timestampValidator.isWithinWindow(timestampHeader)) {
                log.warn("webhook_timestamp_invalid eventId={} source={}", eventId, source);
                return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_TIMESTAMP_INVALID",
                        "X-Erp-Timestamp missing or outside acceptance window");
            }

            // Step 3: HMAC verification over raw body.
            byte[] body = rawBody == null ? new byte[0] : rawBody;
            if (!hmacVerifier.verify(body, secret.get(), signatureHeader)) {
                log.warn("webhook_signature_invalid eventId={} source={}", eventId, source);
                return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
                        "HMAC signature mismatch");
            }

            // Step 4: parse + validate body schema.
            ErpOrderWebhookRequest parsed;
            try {
                parsed = objectMapper.readValue(body, ErpOrderWebhookRequest.class);
            } catch (JsonProcessingException e) {
                return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                        "Webhook payload is not valid JSON");
            } catch (Exception e) {
                return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                        "Webhook payload is malformed");
            }
            Set<ConstraintViolation<ErpOrderWebhookRequest>> violations = validator.validate(parsed);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .collect(Collectors.joining("; "));
                return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message);
            }

            // Step 5: dedupe + inbox in one TX.
            String rawPayloadJson = new String(body, StandardCharsets.UTF_8);
            WebhookInboxPersistenceAdapter.Result result = inboxAdapter.ingest(
                    eventId, rawPayloadJson, source);
            if (result instanceof WebhookInboxPersistenceAdapter.Result.Duplicate dup) {
                return ResponseEntity.ok(
                        WebhookAckResponse.ignoredDuplicate(eventId, dup.previouslyReceivedAt()));
            }
            WebhookInboxPersistenceAdapter.Result.Accepted acc =
                    (WebhookInboxPersistenceAdapter.Result.Accepted) result;
            log.info("webhook_accepted eventId={} source={} orderNo={}",
                    eventId, source, parsed.orderNo());
            return ResponseEntity.ok(
                    WebhookAckResponse.accepted(eventId, parsed.orderNo(), acc.receivedAt()));
        } finally {
            MDC.remove("eventId");
            MDC.remove("source");
        }
    }

    private static ResponseEntity<ApiErrorEnvelope> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorEnvelope.of(code, message));
    }
}
