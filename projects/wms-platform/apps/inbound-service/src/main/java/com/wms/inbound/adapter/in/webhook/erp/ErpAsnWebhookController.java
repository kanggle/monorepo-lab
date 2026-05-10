package com.wms.inbound.adapter.in.webhook.erp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.inbound.adapter.in.webhook.erp.dto.ErpAsnWebhookResponse;
import com.wms.inbound.application.command.ErpAsnWebhookRequest;
import com.wms.inbound.application.command.IngestWebhookEventCommand;
import com.wms.inbound.application.port.in.IngestWebhookEventUseCase;
import com.wms.inbound.application.port.in.IngestWebhookEventUseCase.IngestResult;
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
 *   <li>Atomic dedupe + inbox write via {@link IngestWebhookEventUseCase}.
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
    private final IngestWebhookEventUseCase ingestUseCase;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ErpAsnWebhookController(HmacSignatureVerifier hmacVerifier,
                                   TimestampWindowVerifier timestampVerifier,
                                   WebhookSecretPort secretPort,
                                   IngestWebhookEventUseCase ingestUseCase,
                                   ObjectMapper objectMapper,
                                   Validator validator) {
        this.hmacVerifier = hmacVerifier;
        this.timestampVerifier = timestampVerifier;
        this.secretPort = secretPort;
        this.ingestUseCase = ingestUseCase;
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
            if (!validateTimestampWindow(timestampHeader)) {
                log.warn("webhook_timestamp_invalid eventId={} source={}", eventId, source);
                return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_TIMESTAMP_INVALID",
                        "X-Erp-Timestamp missing or outside acceptance window");
            }

            // Step 2: secret + HMAC. Missing secret means we cannot verify; per
            // spec failure-mode "Source header references unknown env" → 401
            // signature_invalid.
            Optional<String> secret = resolveSecret(source);
            if (secret.isEmpty()) {
                log.warn("webhook_signature_invalid eventId={} source={} reason=secret_missing",
                        eventId, source);
                return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
                        "HMAC signature mismatch");
            }
            byte[] body = rawBody == null ? new byte[0] : rawBody;
            if (!verifySignature(body, signatureHeader, secret.get())) {
                log.warn("webhook_signature_invalid eventId={} source={}", eventId, source);
                return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
                        "HMAC signature mismatch");
            }

            // Step 3: parse + validate body schema.
            ParseResult parseResult = parseAndValidate(body);
            if (parseResult.error() != null) {
                return parseResult.error();
            }
            ErpAsnWebhookRequest parsed = parseResult.request();

            // Step 4: dedupe + inbox in one TX (delegated to the application use-case port).
            IngestResult result = ingest(eventId, body, signatureHeader, source);
            if (result instanceof IngestResult.Duplicate dup) {
                return ResponseEntity.ok(
                        ErpAsnWebhookResponse.ignoredDuplicate(eventId, dup.previouslyReceivedAt()));
            }
            IngestResult.Accepted acc = (IngestResult.Accepted) result;
            return ResponseEntity.ok(
                    ErpAsnWebhookResponse.accepted(eventId, parsed.asnNo(), acc.receivedAt()));
        } finally {
            MDC.remove("eventId");
            MDC.remove("source");
        }
    }

    /** Step 1: verify the request timestamp is within the configured acceptance window. */
    private boolean validateTimestampWindow(String timestampHeader) {
        return timestampVerifier.isWithinWindow(timestampHeader);
    }

    /** Step 2a: look up the HMAC secret for the given source environment. */
    private Optional<String> resolveSecret(String source) {
        return secretPort.resolveSecret(source);
    }

    /** Step 2b: verify HMAC-SHA256 signature over the raw request body. */
    private boolean verifySignature(byte[] body, String signatureHeader, String secret) {
        return hmacVerifier.verify(body, secret, signatureHeader);
    }

    /**
     * Step 3: parse + bean-validate the raw body.
     *
     * @return a {@link ParseResult} carrying either the validated request object or a
     *         422 error response (never both non-null).
     */
    private ParseResult parseAndValidate(byte[] body) {
        ErpAsnWebhookRequest parsed;
        try {
            parsed = objectMapper.readValue(body, ErpAsnWebhookRequest.class);
        } catch (JsonProcessingException e) {
            return ParseResult.failure(error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Webhook payload is not valid JSON"));
        } catch (Exception e) {
            return ParseResult.failure(error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Webhook payload is malformed"));
        }
        Set<ConstraintViolation<ErpAsnWebhookRequest>> violations = validator.validate(parsed);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));
            return ParseResult.failure(error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message));
        }
        return ParseResult.success(parsed);
    }

    /** Carries either a successfully parsed request or a 422 error response (never both). */
    private record ParseResult(ErpAsnWebhookRequest request, ResponseEntity<?> error) {
        static ParseResult success(ErpAsnWebhookRequest req) { return new ParseResult(req, null); }
        static ParseResult failure(ResponseEntity<?> err)    { return new ParseResult(null, err); }
    }

    /** Step 4: write to the webhook inbox via the application use-case port. */
    private IngestResult ingest(String eventId, byte[] body, String signatureHeader, String source) {
        String rawPayloadJson = new String(body, StandardCharsets.UTF_8);
        return ingestUseCase.ingest(
                new IngestWebhookEventCommand(eventId, rawPayloadJson, signatureHeader, source));
    }

    private static ResponseEntity<ApiErrorEnvelope> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorEnvelope.of(code, message));
    }
}
