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
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Endpoint-level idempotency wrapper (fintech F1, transactional T1;
 * account-api.md). <b>Claim-before-execute</b>: an atomic claim is taken
 * BEFORE the fund-moving action, so a concurrent same-key burst executes the
 * movement <em>exactly once</em> (the prior check-then-act stored AFTER the
 * action and could not gate a concurrent burst).
 *
 * <ol>
 *   <li>EXECUTE — this request won the claim: run the use case (its own single
 *       {@code @Transactional} boundary moves funds at most once), then
 *       {@code complete} (2xx) or {@code release} (failure/non-2xx) the claim;</li>
 *   <li>REPLAY — same key + identical payload already completed → replay the
 *       first stored response (NO fund re-movement);</li>
 *   <li>CONFLICT — same key + different payload → 409
 *       {@code IDEMPOTENCY_KEY_CONFLICT};</li>
 *   <li>IN_PROGRESS — a concurrent duplicate holds the claim: bounded-wait for
 *       the winner's stored response then replay it; a pathological stall past
 *       the bound → 409 (deterministic, no re-movement — never a faked
 *       success).</li>
 * </ol>
 *
 * <p>Body type is intentionally erased to {@link ApiEnvelope}: a replay must
 * be byte-faithful to the first response. The store is fail-CLOSED (DB
 * unreachable on a fund-write claim → 503 {@code IDEMPOTENCY_STORE_UNAVAILABLE}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentExecution {

    /** Bounded wait for an in-flight winner — ≪ the request/test timeout. */
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
                case IN_PROGRESS -> waitOrThrow(idempotencyKey, deadlineNanos);
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
            // Action failed → release so a retry can re-claim and re-attempt
            // (errors are NOT idempotency-stored).
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

    /** Same key reused with a different payload → 409 (account-api.md). */
    public static void conflict(String key) {
        throw new IdempotencyKeyConflictException(
                "Idempotency-Key '" + key + "' reused with a different payload");
    }

    /**
     * Waits one poll interval for the in-flight winner to complete, or throws
     * if the deadline has been reached. The winner stalling past the bound is
     * deterministic and does not result in fund movement or a faked success
     * — 409 (client retries) is contract-faithful (account-api.md).
     */
    private static void waitOrThrow(String idempotencyKey, long deadlineNanos) {
        if (System.nanoTime() >= deadlineNanos) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key '" + idempotencyKey
                            + "' has a concurrent request still in progress");
        }
        sleepQuietly();
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
