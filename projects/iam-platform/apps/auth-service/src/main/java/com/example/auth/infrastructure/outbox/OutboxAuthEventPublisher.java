package com.example.auth.infrastructure.outbox;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.infrastructure.persistence.AuthOutboxJpaEntity;
import com.example.auth.infrastructure.persistence.AuthOutboxJpaRepository;
import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AuthEventPublisher} implementation (TASK-BE-450 — outbox v1 → v2).
 *
 * <p>Builds the canonical event envelope and persists an {@code auth_outbox}
 * row in the caller's transaction (the {@code AbstractOutboxPublisher} /
 * {@code OutboxRow} path — ADR-MONO-004 § 5, mirroring finance account-service's
 * {@code OutboxAccountEventPublisher} + erp approval-service's
 * {@code OutboxApprovalEventPublisher}). The {@code AuthOutboxPublisher} relay
 * forwards the row to Kafka asynchronously; downstream consumers (security-service,
 * account-service) dedupe on the envelope {@code eventId} (at-least-once).
 *
 * <p><b>Wire-shape preserved.</b> The envelope is the EXACT 7-field shape the
 * previous {@code BaseEventPublisher.writeEvent} path emitted —
 * {@code {eventId, eventType, source, occurredAt, schemaVersion, partitionKey,
 * payload}}, {@code source = "auth-service"}, every payload field/order + every
 * conditional omission unchanged — so consumers are unaffected. Each publish
 * method's payload-Map construction is copied VERBATIM from the v1
 * {@code AuthEventPublisher} (TASK-BE-131 field-order invariants preserved). The
 * only change: the envelope {@code eventId} now equals the {@code auth_outbox} PK
 * (both UUIDv7) so the Kafka {@code eventId} header matches the payload.
 */
@Component
public class OutboxAuthEventPublisher implements AuthEventPublisher {

    private static final String AGGREGATE_TYPE = "auth";
    private static final String SOURCE = "auth-service";
    private static final int SCHEMA_VERSION = 1;

    private final AuthOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxAuthEventPublisher(AuthOutboxJpaRepository outboxRepository,
                                    ObjectMapper objectMapper,
                                    Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Guard used by all publish methods that require a non-blank tenantId
     * (TASK-BE-248 Phase 2b). Verbatim from the v1 publisher.
     */
    private static String requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId required");
        }
        return tenantId;
    }

    @Override
    @Transactional
    public void publishLoginAttempted(String accountId, String emailHash, String tenantId,
                                      SessionContext ctx) {
        requireTenantId(tenantId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId);
        payload.put("emailHash", emailHash);
        payload.put("tenantId", tenantId);
        payload.put("ipMasked", ctx.ipMasked());
        payload.put("userAgentFamily", ctx.userAgentFamily());
        payload.put("deviceFingerprint", ctx.deviceFingerprint());
        payload.put("geoCountry", ctx.resolvedGeoCountry());
        payload.put("timestamp", Instant.now().toString());

        write("auth.login.attempted", accountId != null ? accountId : emailHash, payload);
    }

    @Override
    @Transactional
    public void publishLoginFailed(String accountId, String emailHash, String tenantId,
                                   String failureReason, int failCount, SessionContext ctx) {
        requireTenantId(tenantId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId);
        payload.put("emailHash", emailHash);
        payload.put("tenantId", tenantId);
        payload.put("failureReason", failureReason);
        payload.put("failCount", failCount);
        payload.put("ipMasked", ctx.ipMasked());
        payload.put("userAgentFamily", ctx.userAgentFamily());
        payload.put("deviceFingerprint", ctx.deviceFingerprint());
        payload.put("geoCountry", ctx.resolvedGeoCountry());
        payload.put("timestamp", Instant.now().toString());

        write("auth.login.failed", accountId != null ? accountId : emailHash, payload);
    }

    @Override
    @Transactional
    public void publishLoginSucceeded(String accountId, String sessionJti, SessionContext ctx) {
        Map<String, Object> payload = buildLoginSucceededBase(accountId, sessionJti, null, ctx);
        write("auth.login.succeeded", accountId, payload);
    }

    @Override
    @Transactional
    public void publishLoginSucceeded(String accountId, String sessionJti, SessionContext ctx,
                                      String deviceId, Boolean isNewDevice) {
        Map<String, Object> payload = buildLoginSucceededBase(accountId, sessionJti, null, ctx);
        Object timestamp = payload.remove("timestamp");
        payload.put("deviceId", deviceId);
        payload.put("isNewDevice", isNewDevice);
        payload.put("timestamp", timestamp);
        write("auth.login.succeeded", accountId, payload);
    }

    @Override
    @Transactional
    public void publishLoginSucceeded(String accountId, String sessionJti, String tenantId,
                                      SessionContext ctx, String deviceId, Boolean isNewDevice) {
        requireTenantId(tenantId);
        Map<String, Object> payload = buildLoginSucceededBase(accountId, sessionJti, tenantId, ctx);
        Object timestamp = payload.remove("timestamp");
        payload.put("deviceId", deviceId);
        payload.put("isNewDevice", isNewDevice);
        payload.put("timestamp", timestamp);
        write("auth.login.succeeded", accountId, payload);
    }

    @Override
    @Deprecated
    @Transactional
    public void publishLoginSucceeded(String accountId, String sessionJti, SessionContext ctx,
                                      String deviceId, Boolean isNewDevice, String loginMethod) {
        Map<String, Object> payload = buildLoginSucceededBase(accountId, sessionJti, null, ctx);
        Object timestamp = payload.remove("timestamp");
        payload.put("deviceId", deviceId);
        payload.put("isNewDevice", isNewDevice);
        payload.put("loginMethod", loginMethod);
        payload.put("timestamp", timestamp);
        write("auth.login.succeeded", accountId, payload);
    }

    @Override
    @Transactional
    public void publishLoginSucceeded(String accountId, String sessionJti, String tenantId,
                                      SessionContext ctx, String deviceId, Boolean isNewDevice,
                                      String loginMethod) {
        requireTenantId(tenantId);
        Map<String, Object> payload = buildLoginSucceededBase(accountId, sessionJti, tenantId, ctx);
        Object timestamp = payload.remove("timestamp");
        payload.put("deviceId", deviceId);
        payload.put("isNewDevice", isNewDevice);
        payload.put("loginMethod", loginMethod);
        payload.put("timestamp", timestamp);
        write("auth.login.succeeded", accountId, payload);
    }

    private Map<String, Object> buildLoginSucceededBase(String accountId, String sessionJti,
                                                        String tenantId, SessionContext ctx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId);
        if (tenantId != null) {
            payload.put("tenantId", tenantId);
        }
        payload.put("ipMasked", ctx.ipMasked());
        payload.put("userAgentFamily", ctx.userAgentFamily());
        payload.put("deviceFingerprint", ctx.deviceFingerprint());
        payload.put("geoCountry", ctx.resolvedGeoCountry());
        payload.put("sessionJti", sessionJti);
        payload.put("timestamp", Instant.now().toString());
        return payload;
    }

    @Override
    @Transactional
    public void publishTokenRefreshed(String accountId, String tenantId,
                                      String previousJti, String newJti, SessionContext ctx) {
        requireTenantId(tenantId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId);
        payload.put("tenantId", tenantId);
        payload.put("previousJti", previousJti);
        payload.put("newJti", newJti);
        payload.put("ipMasked", ctx.ipMasked());
        payload.put("deviceFingerprint", ctx.deviceFingerprint());
        payload.put("timestamp", Instant.now().toString());

        write("auth.token.refreshed", accountId, payload);
    }

    @Override
    @Transactional
    public void publishTokenReuseDetected(String accountId, String tenantId, String reusedJti,
                                          Instant originalRotationAt, Instant reuseAttemptAt,
                                          String ipMasked, String deviceFingerprint,
                                          boolean sessionsRevoked, int revokedCount) {
        requireTenantId(tenantId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId);
        payload.put("tenantId", tenantId);
        payload.put("reusedJti", reusedJti);
        payload.put("originalRotationAt", originalRotationAt != null ? originalRotationAt.toString() : null);
        payload.put("reuseAttemptAt", reuseAttemptAt.toString());
        payload.put("ipMasked", ipMasked);
        payload.put("deviceFingerprint", deviceFingerprint);
        payload.put("sessionsRevoked", sessionsRevoked);
        payload.put("revokedCount", revokedCount);

        write("auth.token.reuse.detected", accountId, payload);
    }

    @Override
    @Transactional
    public void publishTokenTenantMismatch(String accountId, String submittedTenantId,
                                           String expectedTenantId, String jti,
                                           String ipMasked, String deviceFingerprint) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId);
        payload.put("submittedTenantId", submittedTenantId);
        payload.put("expectedTenantId", expectedTenantId);
        payload.put("reusedJti", jti);
        payload.put("ipMasked", ipMasked);
        payload.put("deviceFingerprint", deviceFingerprint);
        payload.put("detectedAt", Instant.now().toString());

        write("auth.token.tenant.mismatch", accountId, payload);
    }

    @Override
    @Transactional
    public void publishAuthSessionCreated(String accountId, String tenantId, String deviceId,
                                          String sessionJti,
                                          String deviceFingerprintHash, String userAgentFamily,
                                          String ipMasked, String geoCountry, Instant issuedAt,
                                          List<String> evictedDeviceIds) {
        requireTenantId(tenantId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId);
        payload.put("tenantId", tenantId);
        payload.put("deviceId", deviceId);
        payload.put("sessionJti", sessionJti);
        payload.put("deviceFingerprintHash", deviceFingerprintHash);
        payload.put("userAgentFamily", userAgentFamily);
        payload.put("ipMasked", ipMasked);
        payload.put("geoCountry", geoCountry);
        payload.put("issuedAt", issuedAt.toString());
        payload.put("evictedDeviceIds", evictedDeviceIds != null ? evictedDeviceIds : List.of());

        write("auth.session.created", accountId, payload);
    }

    @Override
    @Transactional
    public void publishAuthSessionRevoked(String accountId, String tenantId, String deviceId,
                                          String reason,
                                          List<String> revokedJtis, Instant revokedAt,
                                          String actorType, String actorAccountId) {
        requireTenantId(tenantId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId);
        payload.put("tenantId", tenantId);
        payload.put("deviceId", deviceId);
        payload.put("reason", reason);
        payload.put("revokedJtis", revokedJtis != null ? revokedJtis : List.of());
        payload.put("revokedAt", revokedAt.toString());

        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("type", actorType);
        actor.put("accountId", actorAccountId);
        payload.put("actor", actor);

        write("auth.session.revoked", accountId, payload);
    }

    private void write(String eventType, String aggregateId, Map<String, Object> payload) {
        writeEvent(AGGREGATE_TYPE, aggregateId, eventType, payload);
    }

    /**
     * Wrap {@code payload} in the canonical 7-field envelope (preserved from the
     * v1 {@code BaseEventPublisher.writeEvent} path), serialise it, and persist a
     * pending {@code auth_outbox} row in the caller's transaction. The generated
     * {@link UuidV7} doubles as the envelope {@code eventId} and the row PK;
     * {@code partition_key = aggregateId} (the v1 Kafka key).
     */
    private void writeEvent(String aggregateType, String aggregateId,
                            String eventType, Map<String, Object> payload) {
        UUID eventId = UuidV7.randomUuid();
        Instant occurredAt = Instant.now(clock);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("source", SOURCE);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("partitionKey", aggregateId);
        envelope.put("payload", payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "failed to serialise " + eventType + " outbox envelope", e);
        }

        outboxRepository.save(AuthOutboxJpaEntity.create(
                eventId, aggregateType, aggregateId, eventType,
                json, aggregateId, occurredAt));
    }
}
