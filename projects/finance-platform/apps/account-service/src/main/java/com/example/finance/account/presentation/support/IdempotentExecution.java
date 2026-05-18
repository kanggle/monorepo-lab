package com.example.finance.account.presentation.support;

import com.example.finance.account.application.port.outbound.IdempotencyStore;
import com.example.finance.account.domain.error.DomainErrors.IdempotencyKeyConflictException;
import com.example.finance.account.presentation.dto.ApiEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/**
 * Endpoint-level idempotency wrapper (fintech F1, transactional T1;
 * account-api.md). Wraps every mutating handler:
 *
 * <ol>
 *   <li>same key + identical payload hash → replay the first stored response
 *       (NO fund re-movement);</li>
 *   <li>same key + different payload hash → 409
 *       {@code IDEMPOTENCY_KEY_CONFLICT};</li>
 *   <li>otherwise execute the use case (its own single {@code @Transactional}
 *       boundary moves funds at most once), then store the response AFTER the
 *       commit so a replay returns the exact first body.</li>
 * </ol>
 *
 * <p>Body type is intentionally erased to {@link ApiEnvelope}: a replay must
 * be byte-faithful to the first response, not type-rich — the stored JSON is
 * deserialized back into the envelope shape. The store itself is fail-CLOSED
 * (Redis + DB both down → 503 {@code IDEMPOTENCY_STORE_UNAVAILABLE}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentExecution {

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    public ResponseEntity<?> run(String tenantId,
                                 String endpoint,
                                 String idempotencyKey,
                                 Object requestPayload,
                                 Supplier<ResponseEntity<?>> action) {
        String payloadHash = sha256(toJson(requestPayload));

        IdempotencyStore.Lookup lookup = idempotencyStore.findExisting(
                tenantId, endpoint, idempotencyKey, payloadHash);
        switch (lookup.outcome()) {
            case CONFLICT -> conflict(idempotencyKey);
            case REPLAY -> {
                IdempotencyStore.StoredResponse stored = lookup.storedResponse();
                return ResponseEntity.status(stored.status())
                        .body(fromJson(stored.body()));
            }
            case MISS -> { /* fall through to execute */ }
        }

        ResponseEntity<?> response = action.get();
        // Only cache success responses (2xx). Errors are NOT idempotency-
        // stored — a retry should re-attempt the operation.
        if (response.getStatusCode().is2xxSuccessful()) {
            idempotencyStore.store(tenantId, endpoint, idempotencyKey, payloadHash,
                    new IdempotencyStore.StoredResponse(
                            response.getStatusCode().value(),
                            toJson(response.getBody())));
        }
        return response;
    }

    /** Same key reused with a different payload → 409 (account-api.md). */
    public static void conflict(String key) {
        throw new IdempotencyKeyConflictException(
                "Idempotency-Key '" + key + "' reused with a different payload");
    }

    private String toJson(Object o) {
        try {
            return o == null ? "null" : objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("idempotency payload serialize failed", e);
        }
    }

    private ApiEnvelope<?> fromJson(String json) {
        try {
            return objectMapper.readValue(json, ApiEnvelope.class);
        } catch (Exception e) {
            throw new IllegalStateException("idempotency response deserialize failed", e);
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha-256 unavailable", e);
        }
    }
}
