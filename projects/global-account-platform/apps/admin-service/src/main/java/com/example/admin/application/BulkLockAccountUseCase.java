package com.example.admin.application;

import com.example.admin.application.exception.AuditFailureException;
import com.example.admin.application.exception.BatchSizeExceededException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.application.port.BulkLockIdempotencyPort;
import com.example.admin.application.port.OperatorLookupPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bulk-lock orchestrator. Dedupes input, enforces the batch cap, delegates to
 * {@link AccountAdminUseCase#lock} for each accountId, and applies the
 * (operator, idempotency-key) replay contract persisted through
 * {@link BulkLockIdempotencyPort}.
 *
 * <p>Per-row outcomes:
 * <ul>
 *   <li>{@code LOCKED} — 200 from account-service</li>
 *   <li>{@code NOT_FOUND} — non-retryable 404</li>
 *   <li>{@code ALREADY_LOCKED} — non-retryable 400/409 with STATE_TRANSITION_INVALID</li>
 *   <li>{@code FAILURE} — any other error (5xx exhausted retries, circuit open, audit failure, etc.)</li>
 * </ul>
 *
 * <p>4xx classification is strictly type-driven: callers branch on
 * {@link NonRetryableDownstreamException#getHttpStatus()} and
 * {@link NonRetryableDownstreamException#getErrorCode()}, not on the exception
 * message. This keeps the behaviour stable across downstream wording changes.
 *
 * <p>Idempotency save is a find-or-save: a concurrent first request that loses
 * the INSERT race surfaces as {@link BulkLockIdempotencyPort.DuplicateKeyException},
 * at which point the winner's response body is replayed. A divergent payload
 * under the same key raises {@link IdempotencyKeyConflictException} (409).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkLockAccountUseCase {

    public static final int MAX_BATCH_SIZE = 100;
    public static final int MIN_REASON_LENGTH = 8;

    public static final String OUTCOME_LOCKED = "LOCKED";
    public static final String OUTCOME_NOT_FOUND = "NOT_FOUND";
    public static final String OUTCOME_ALREADY_LOCKED = "ALREADY_LOCKED";
    public static final String OUTCOME_FAILURE = "FAILURE";

    static final String DOWNSTREAM_CODE_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    static final String DOWNSTREAM_CODE_STATE_INVALID = "STATE_TRANSITION_INVALID";

    private final AccountAdminUseCase accountAdminUseCase;
    private final BulkLockIdempotencyPort idempotencyPort;
    private final OperatorLookupPort operatorLookupPort;
    private final ObjectMapper objectMapper;

    public BulkLockAccountResult execute(BulkLockAccountCommand cmd) {
        validate(cmd);

        List<String> deduped = dedupe(cmd.accountIds());

        Long operatorPk = operatorLookupPort.findInternalId(cmd.operator().operatorId())
                .orElseThrow(() -> new AuditFailureException(
                        "admin_operators row not found for operatorId=" + cmd.operator().operatorId()));

        String requestHash = computeRequestHash(deduped, cmd.reason(), cmd.ticketId());

        // Fast-path replay: identical request returns the stored response with
        // no further side-effects. Divergent payload under same key → 409.
        Optional<BulkLockIdempotencyPort.Record> existing =
                idempotencyPort.find(operatorPk, cmd.idempotencyKey());
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), requestHash);
        }

        // Execute sequentially; collect per-row outcomes.
        List<BulkLockAccountResult.Item> items = new ArrayList<>(deduped.size());
        for (String accountId : deduped) {
            items.add(processOne(cmd, accountId));
        }

        BulkLockAccountResult result = new BulkLockAccountResult(items, false);

        // Persist the canonical response body. On a concurrent first-request
        // race the PK collision is surfaced as DuplicateKeyException; we then
        // resolve the conflict deterministically by re-reading the winning row.
        try {
            idempotencyPort.save(operatorPk, cmd.idempotencyKey(), requestHash,
                    serialiseResults(items), Instant.now());
            return result;
        } catch (BulkLockIdempotencyPort.DuplicateKeyException race) {
            log.info("bulk-lock idempotency race detected; resolving via replay: operatorId={} key={}",
                    cmd.operator().operatorId(), cmd.idempotencyKey());
            BulkLockIdempotencyPort.Record winner = idempotencyPort
                    .find(operatorPk, cmd.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency DuplicateKeyException raised but winning row not readable: "
                                    + cmd.idempotencyKey(), race));
            return replayOrConflict(winner, requestHash);
        }
    }

    private BulkLockAccountResult replayOrConflict(BulkLockIdempotencyPort.Record row,
                                                   String requestHash) {
        if (!row.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key reused with a different request payload");
        }
        return new BulkLockAccountResult(parseStoredResults(row.responseBody()), true);
    }

    private BulkLockAccountResult.Item processOne(BulkLockAccountCommand cmd, String accountId) {
        String perRowIdempotency = cmd.idempotencyKey() + ":" + accountId;
        try {
            accountAdminUseCase.lock(new LockAccountCommand(
                    accountId, cmd.reason(), cmd.ticketId(), perRowIdempotency, cmd.operator()));
            return new BulkLockAccountResult.Item(accountId, OUTCOME_LOCKED, null, null);
        } catch (NonRetryableDownstreamException ex) {
            return classifyNonRetryable(accountId, ex);
        } catch (DownstreamFailureException ex) {
            return new BulkLockAccountResult.Item(accountId, OUTCOME_FAILURE,
                    "DOWNSTREAM_ERROR", ex.getMessage());
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
            return new BulkLockAccountResult.Item(accountId, OUTCOME_FAILURE,
                    "CIRCUIT_OPEN", "Downstream circuit is open");
        } catch (AuditFailureException ex) {
            // Audit path failed — fail-closed for this row but continue the batch.
            return new BulkLockAccountResult.Item(accountId, OUTCOME_FAILURE,
                    "AUDIT_FAILURE", "Audit write failed");
        } catch (RuntimeException ex) {
            log.warn("Unexpected error locking accountId={} in bulk batch", accountId, ex);
            return new BulkLockAccountResult.Item(accountId, OUTCOME_FAILURE,
                    "INTERNAL_ERROR", ex.getClass().getSimpleName());
        }
    }

    /**
     * Type-driven 4xx classification. Primary signal is the downstream error
     * code extracted from the response body; HTTP status is the fallback when
     * the body is missing or unparseable.
     */
    private BulkLockAccountResult.Item classifyNonRetryable(String accountId,
                                                            NonRetryableDownstreamException ex) {
        String code = ex.getErrorCode();
        int status = ex.getHttpStatus();

        if (DOWNSTREAM_CODE_NOT_FOUND.equals(code) || status == 404) {
            return new BulkLockAccountResult.Item(accountId, OUTCOME_NOT_FOUND,
                    "ACCOUNT_NOT_FOUND", "Account does not exist");
        }
        if (DOWNSTREAM_CODE_STATE_INVALID.equals(code) || status == 409 || status == 400) {
            return new BulkLockAccountResult.Item(accountId, OUTCOME_ALREADY_LOCKED,
                    "STATE_TRANSITION_INVALID", "Account is not in a lockable state");
        }
        return new BulkLockAccountResult.Item(accountId, OUTCOME_FAILURE,
                "DOWNSTREAM_ERROR", ex.getMessage());
    }

    private void validate(BulkLockAccountCommand cmd) {
        if (cmd.reason() == null || cmd.reason().trim().length() < MIN_REASON_LENGTH) {
            throw new ReasonRequiredException();
        }
        if (cmd.accountIds() == null || cmd.accountIds().isEmpty()) {
            throw new IllegalArgumentException("accountIds must not be empty");
        }
        if (cmd.accountIds().size() > MAX_BATCH_SIZE) {
            throw new BatchSizeExceededException(
                    "Batch exceeds maximum of " + MAX_BATCH_SIZE + " accountIds");
        }
        if (cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
    }

    private static List<String> dedupe(List<String> ids) {
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    /**
     * Visible for testing: canonical SHA-256 of the sorted dedup accountIds +
     * reason + ticketId. Tests should call this method rather than re-deriving
     * the same canonical form.
     */
    String computeRequestHash(List<String> accountIds, String reason, String ticketId) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        List<String> sorted = new ArrayList<>(accountIds);
        java.util.Collections.sort(sorted);
        canonical.put("accountIds", sorted);
        canonical.put("reason", reason);
        canonical.put("ticketId", ticketId);
        try {
            byte[] json = objectMapper.writeValueAsBytes(canonical);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute request hash", e);
        }
    }

    private String serialiseResults(List<BulkLockAccountResult.Item> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise bulk-lock result", e);
        }
    }

    private List<BulkLockAccountResult.Item> parseStoredResults(String body) {
        try {
            return objectMapper.readValue(
                    body.getBytes(StandardCharsets.UTF_8),
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, BulkLockAccountResult.Item.class));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to parse stored bulk-lock result", e);
        }
    }
}
