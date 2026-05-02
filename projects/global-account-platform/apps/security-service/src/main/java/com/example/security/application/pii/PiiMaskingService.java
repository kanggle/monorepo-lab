package com.example.security.application.pii;

import com.example.security.application.event.SecurityEventPublisher;
import com.example.security.domain.pii.PiiMaskingRecord;
import com.example.security.infrastructure.persistence.PiiMaskingLogJpaEntity;
import com.example.security.infrastructure.persistence.PiiMaskingLogJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Application service that masks PII in security-service tables when an
 * {@code account.deleted(anonymized=true)} event is consumed (TASK-BE-258).
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Check idempotency via {@code pii_masking_log.event_id}.</li>
 *   <li>Execute three tenant-scoped UPDATE statements (login_history,
 *       suspicious_events, account_lock_history) in a single transaction.</li>
 *   <li>Insert the idempotency log row.</li>
 *   <li>Publish {@code security.pii.masked} outbox event (within the same
 *       transaction via {@link SecurityEventPublisher}).</li>
 * </ol>
 *
 * <p>Masking values:
 * <ul>
 *   <li>{@code ip_masked} → {@code "0.0.0.0"}</li>
 *   <li>{@code user_agent_family} → {@code "REDACTED"}</li>
 *   <li>{@code device_fingerprint} → SHA-256 of {@code accountId} (DB-side)</li>
 *   <li>{@code suspicious_events.evidence} → {@code "{}"} (clear PII context)</li>
 * </ul>
 *
 * <p>{@code tenant_id} and {@code account_id} are preserved in all tables —
 * audit-heavy A3 immutability of the row itself is maintained; only PII
 * value columns are overwritten.
 */
@Slf4j
@Service
public class PiiMaskingService {

    static final List<String> MASKED_TABLES =
            List.of("login_history", "suspicious_events", "account_lock_history");

    private final PiiMaskingLogJpaRepository piiMaskingLogRepository;
    private final SecurityEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final String fingerprintSalt;

    public PiiMaskingService(PiiMaskingLogJpaRepository piiMaskingLogRepository,
                             SecurityEventPublisher eventPublisher,
                             ObjectMapper objectMapper,
                             @Value("${app.pii.masking.fingerprint-salt}") String fingerprintSalt) {
        if (fingerprintSalt == null || fingerprintSalt.isBlank()) {
            throw new IllegalStateException(
                    "app.pii.masking.fingerprint-salt must be a non-blank value (TASK-BE-270)");
        }
        this.piiMaskingLogRepository = piiMaskingLogRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.fingerprintSalt = fingerprintSalt;
    }

    /**
     * Mask PII for the given account in a single transaction.
     *
     * @param eventId   the Kafka {@code eventId} used as idempotency key
     * @param tenantId  tenant that owns the account
     * @param accountId the account whose PII must be masked
     * @return {@code true} if masking was performed, {@code false} if already processed (duplicate)
     */
    @Transactional
    public boolean maskPii(String eventId, String tenantId, String accountId) {
        if (piiMaskingLogRepository.existsByEventId(eventId)) {
            log.info("PII masking skipped — already processed: eventId={}, accountId={}", eventId, accountId);
            return false;
        }

        Instant maskedAt = Instant.now();

        // Execute per-table masking (UPDATE returns affected row count; 0 is OK).
        int lhRows = piiMaskingLogRepository.maskLoginHistory(tenantId, accountId, fingerprintSalt);
        int seRows = piiMaskingLogRepository.maskSuspiciousEvents(tenantId, accountId);
        int alRows = piiMaskingLogRepository.touchAccountLockHistory(tenantId, accountId);

        log.info("PII masked: eventId={}, tenantId={}, accountId={}, " +
                        "login_history={} rows, suspicious_events={} rows, account_lock_history={} rows",
                eventId, tenantId, accountId, lhRows, seRows, alRows);

        // Record idempotency log.
        String tableNamesJson = toJson(MASKED_TABLES);
        PiiMaskingLogJpaEntity logEntry = PiiMaskingLogJpaEntity.create(
                eventId, tenantId, accountId, maskedAt, tableNamesJson);
        try {
            piiMaskingLogRepository.save(logEntry);
        } catch (DataIntegrityViolationException dup) {
            // Race condition: another thread processed the same event concurrently.
            // The masking UPDATE above is idempotent (replacing masked values is safe).
            log.info("PII masking log duplicate ignored (race): eventId={}", eventId);
            return false;
        }

        // Publish audit event within the same transaction (outbox pattern).
        PiiMaskingRecord record = new PiiMaskingRecord(accountId, tenantId, maskedAt, MASKED_TABLES);
        eventPublisher.publishPiiMasked(record, eventId);

        return true;
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            // Should never happen with a simple list of strings.
            return list.toString();
        }
    }
}
