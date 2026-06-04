package com.example.erp.approval.presentation.support;

import com.example.erp.approval.application.port.outbound.IdempotencyStore;
import com.example.erp.approval.domain.error.ApprovalErrors.IdempotencyKeyConflictException;
import com.example.erp.approval.presentation.dto.ApiEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Endpoint-level idempotency wrapper (architecture.md § Idempotency, FIN-BE-004
 * final form). <b>Claim-before-execute</b>: an atomic claim is taken BEFORE the
 * mutating action so a concurrent same-key burst executes the transition
 * exactly once (E4 — same key returns the prior outcome, no re-transition).
 *
 * <ol>
 *   <li>EXECUTE — won the claim: run the use case, then {@code complete} (2xx)
 *       or {@code release} (failure / non-2xx) the claim;</li>
 *   <li>REPLAY — same key + identical payload already completed → replay stored
 *       response (NO re-mutation);</li>
 *   <li>CONFLICT — same key + different payload → 409 IDEMPOTENCY_KEY_CONFLICT;</li>
 *   <li>IN_PROGRESS — a concurrent duplicate holds the claim: bounded-wait for
 *       the winner's stored response then replay; a stall past the bound → 409.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentExecution {

    private static final Duration IN_PROGRESS_WAIT = Duration.ofSeconds(5);
    private static final long POLL_INTERVAL_MS = 50;

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    public ResponseEntity<?> run(String tenantId,
                                 String endpoint,
                                 String idempotencyKey,
                                 Object requestPayload,
                                 Supplier<ResponseEntity<?>> action) {
        String payloadHash = sha256(toJson(requestPayload));
        long deadlineNanos = System.nanoTime() + IN_PROGRESS_WAIT.toNanos();

        while (true) {
            IdempotencyStore.Claim claim = idempotencyStore.claim(
                    tenantId, endpoint, idempotencyKey, payloadHash);
            switch (claim.outcome()) {
                case CONFLICT -> conflict(idempotencyKey);
                case REPLAY -> {
                    return replay(claim.storedResponse());
                }
                case EXECUTE -> {
                    return executeAndStore(tenantId, endpoint, idempotencyKey,
                            payloadHash, action);
                }
                case IN_PROGRESS -> {
                    if (System.nanoTime() >= deadlineNanos) {
                        throw new IdempotencyKeyConflictException(
                                "Idempotency-Key '" + idempotencyKey
                                        + "' has a concurrent request still in progress");
                    }
                    sleepQuietly();
                }
            }
        }
    }

    private ResponseEntity<?> executeAndStore(String tenantId, String endpoint,
                                              String idempotencyKey,
                                              String payloadHash,
                                              Supplier<ResponseEntity<?>> action) {
        ResponseEntity<?> response;
        try {
            response = action.get();
        } catch (RuntimeException ex) {
            idempotencyStore.release(tenantId, endpoint, idempotencyKey);
            throw ex;
        }
        if (response.getStatusCode().is2xxSuccessful()) {
            idempotencyStore.complete(tenantId, endpoint, idempotencyKey,
                    payloadHash, new IdempotencyStore.StoredResponse(
                            response.getStatusCode().value(),
                            toJson(response.getBody())));
        } else {
            idempotencyStore.release(tenantId, endpoint, idempotencyKey);
        }
        return response;
    }

    private ResponseEntity<?> replay(IdempotencyStore.StoredResponse stored) {
        return ResponseEntity.status(stored.status()).body(fromJson(stored.body()));
    }

    public static void conflict(String key) {
        throw new IdempotencyKeyConflictException(
                "Idempotency-Key '" + key + "' reused with a different payload");
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("idempotency wait interrupted", ie);
        }
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
