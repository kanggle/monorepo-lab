package com.wms.outbound.adapter.in.webhook.erp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.outbound.adapter.in.webhook.erp.dto.WebhookAckResponse;
import com.wms.outbound.application.command.ErpOrderWebhookRequest;
import com.wms.outbound.application.command.IngestWebhookEventCommand;
import com.wms.outbound.application.port.in.IngestWebhookEventUseCase;
import com.wms.outbound.application.port.in.IngestWebhookEventUseCase.IngestResult;
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
 *       {@link IngestWebhookEventUseCase}. Conflict → 200
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
    private final IngestWebhookEventUseCase ingestUseCase;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ErpOrderWebhookController(HmacVerifier hmacVerifier,
                                     TimestampWindowValidator timestampValidator,
                                     WebhookSecretPort secretPort,
                                     IngestWebhookEventUseCase ingestUseCase,
                                     ObjectMapper objectMapper,
                                     Validator validator) {
        this.hmacVerifier = hmacVerifier;
        this.timestampValidator = timestampValidator;
        this.secretPort = secretPort;
        this.ingestUseCase = ingestUseCase;
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
            // Step 1: resolve secret (unknown source → 401 before timestamp is evaluated).
            Optional<String> secretOpt = resolveSecret(source);
            if (secretOpt.isEmpty()) {
                log.warn("webhook_signature_invalid eventId={} source={} reason=secret_missing",
                        eventId, source);
                return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
                        "HMAC signature mismatch");
            }

            // Step 2: timestamp window check.
            if (!validateTimestampWindow(timestampHeader)) {
                log.warn("webhook_timestamp_invalid eventId={} source={}", eventId, source);
                return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_TIMESTAMP_INVALID",
                        "X-Erp-Timestamp missing or outside acceptance window");
            }

            // Step 3: HMAC verification over raw body.
            byte[] body = rawBody == null ? new byte[0] : rawBody;
            if (!verifySignature(body, signatureHeader, secretOpt.get())) {
                log.warn("webhook_signature_invalid eventId={} source={}", eventId, source);
                return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
                        "HMAC signature mismatch");
            }

            // Step 4: parse + validate body schema.
            ParseResult parseResult = parseAndValidate(body);
            if (parseResult.error() != null) {
                return parseResult.error();
            }
            ErpOrderWebhookRequest parsed = parseResult.request();

            // Step 5: dedupe + inbox in one TX (delegated to the application service).
            IngestResult result = ingest(eventId, body, source);
            if (result instanceof IngestResult.Duplicate dup) {
                return ResponseEntity.ok(
                        WebhookAckResponse.ignoredDuplicate(eventId, dup.previouslyReceivedAt()));
            }
            IngestResult.Accepted acc = (IngestResult.Accepted) result;
            log.info("webhook_accepted eventId={} source={} orderNo={}",
                    eventId, source, parsed.orderNo());
            return ResponseEntity.ok(
                    WebhookAckResponse.accepted(eventId, parsed.orderNo(), acc.receivedAt()));
        } finally {
            MDC.remove("eventId");
            MDC.remove("source");
        }
    }

    /** Step 1: look up the HMAC secret for the given source environment. */
    private Optional<String> resolveSecret(String source) {
        return secretPort.getSecret(source);
    }

    /** Step 2: verify the request timestamp is within the configured acceptance window. */
    private boolean validateTimestampWindow(String timestampHeader) {
        return timestampValidator.isWithinWindow(timestampHeader);
    }

    /** Step 3: verify HMAC-SHA256 signature over the raw request body. */
    private boolean verifySignature(byte[] body, String signatureHeader, String secret) {
        return hmacVerifier.verify(body, secret, signatureHeader);
    }

    /**
     * Step 4: parse + bean-validate the raw body.
     *
     * @return a {@link ParseResult} carrying either the validated request object or a
     *         422 error response (never both non-null).
     */
    private ParseResult parseAndValidate(byte[] body) {
        ErpOrderWebhookRequest parsed;
        try {
            parsed = objectMapper.readValue(body, ErpOrderWebhookRequest.class);
        } catch (JsonProcessingException e) {
            return ParseResult.failure(error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Webhook payload is not valid JSON"));
        } catch (Exception e) {
            return ParseResult.failure(error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Webhook payload is malformed"));
        }
        Set<ConstraintViolation<ErpOrderWebhookRequest>> violations = validator.validate(parsed);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));
            return ParseResult.failure(error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message));
        }
        return ParseResult.success(parsed);
    }

    /** Carries either a successfully parsed request or a 422 error response (never both). */
    private record ParseResult(ErpOrderWebhookRequest request, ResponseEntity<?> error) {
        static ParseResult success(ErpOrderWebhookRequest req) { return new ParseResult(req, null); }
        static ParseResult failure(ResponseEntity<?> err)       { return new ParseResult(null, err); }
    }

    /** Step 5: write to the webhook inbox via the application use-case port. */
    private IngestResult ingest(String eventId, byte[] body, String source) {
        String rawPayloadJson = new String(body, StandardCharsets.UTF_8);
        return ingestUseCase.ingest(new IngestWebhookEventCommand(eventId, rawPayloadJson, source));
    }

    private static ResponseEntity<ApiErrorEnvelope> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorEnvelope.of(code, message));
    }
}
