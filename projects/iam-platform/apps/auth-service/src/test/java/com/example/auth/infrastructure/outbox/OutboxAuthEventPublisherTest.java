package com.example.auth.infrastructure.outbox;

import com.example.auth.domain.session.SessionContext;
import com.example.auth.infrastructure.persistence.AuthOutboxJpaEntity;
import com.example.auth.infrastructure.persistence.AuthOutboxJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link OutboxAuthEventPublisher} (TASK-BE-450 — outbox v1 → v2
 * write adapter). Replaces the v1 {@code AuthEventPublisherTest} which mocked the
 * lib {@code OutboxWriter}; now we mock the per-service
 * {@link AuthOutboxJpaRepository}, capture the persisted row, and assert the
 * envelope JSON is the byte-identical 7-field shape the v1
 * {@code BaseEventPublisher.writeEvent} path produced (wire-preservation
 * invariant): {@code eventId == row.id}, {@code source == "auth-service"},
 * {@code schemaVersion == 1}, {@code partitionKey == aggregateId}, payload fields
 * + conditional omissions verbatim.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OutboxAuthEventPublisherTest {

    @Mock
    private AuthOutboxJpaRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private OutboxAuthEventPublisher publisher() {
        return new OutboxAuthEventPublisher(outboxRepository, objectMapper,
                Clock.fixed(Instant.parse("2026-04-12T10:00:00Z"), ZoneOffset.UTC));
    }

    private static final String ACCOUNT_ID = "acc-123";

    private AuthOutboxJpaEntity captureRow() {
        ArgumentCaptor<AuthOutboxJpaEntity> captor = ArgumentCaptor.forClass(AuthOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    private Map<String, Object> envelopeOf(AuthOutboxJpaEntity row) throws Exception {
        return objectMapper.readValue(row.getPayload(), new TypeReference<>() {});
    }

    @Test
    @DisplayName("publishTokenReuseDetected writes the canonical envelope; eventId == row id")
    void publishTokenReuseDetected_correctEnvelope() throws Exception {
        Instant originalRotationAt = Instant.parse("2026-04-12T09:50:00Z");
        Instant reuseAttemptAt = Instant.parse("2026-04-12T10:00:00Z");

        publisher().publishTokenReuseDetected(
                ACCOUNT_ID, "fan-platform", "reused-jti-001", originalRotationAt, reuseAttemptAt,
                "192.168.1.***", "fp-abc", true, 5);

        AuthOutboxJpaEntity row = captureRow();
        assertThat(row.getEventType()).isEqualTo("auth.token.reuse.detected");
        assertThat(row.getAggregateType()).isEqualTo("auth");
        assertThat(row.getAggregateId()).isEqualTo(ACCOUNT_ID);
        assertThat(row.getPartitionKey()).isEqualTo(ACCOUNT_ID);

        Map<String, Object> envelope = envelopeOf(row);
        assertThat(envelope).containsEntry("eventType", "auth.token.reuse.detected");
        assertThat(envelope).containsEntry("source", "auth-service");
        assertThat(envelope).containsEntry("schemaVersion", 1);
        assertThat(envelope).containsEntry("partitionKey", ACCOUNT_ID);
        // eventId in the payload must equal the outbox row PK.
        assertThat(envelope.get("eventId")).isEqualTo(row.getId().toString());
        assertThat(envelope).containsKey("occurredAt");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        assertThat(payload).containsEntry("accountId", ACCOUNT_ID);
        assertThat(payload).containsEntry("tenantId", "fan-platform");
        assertThat(payload).containsEntry("reusedJti", "reused-jti-001");
        assertThat(payload).containsEntry("originalRotationAt", originalRotationAt.toString());
        assertThat(payload).containsEntry("reuseAttemptAt", reuseAttemptAt.toString());
        assertThat(payload).containsEntry("sessionsRevoked", true);
        assertThat(payload).containsEntry("revokedCount", 5);
    }

    @Test
    @DisplayName("publishTokenReuseDetected serializes null originalRotationAt as JSON null")
    void publishTokenReuseDetected_nullOriginalRotationAt() throws Exception {
        publisher().publishTokenReuseDetected(
                ACCOUNT_ID, "fan-platform", "jti-001", null, Instant.now(),
                "10.0.0.***", "fp-x", true, 3);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelopeOf(captureRow()).get("payload");
        assertThat(payload.get("originalRotationAt")).isNull();
    }

    @Test
    @DisplayName("publishTokenReuseDetected — tenantId null/blank throws")
    void publishTokenReuseDetected_blankTenantId_throws() {
        assertThatThrownBy(() -> publisher().publishTokenReuseDetected(
                ACCOUNT_ID, null, "jti-001", null, Instant.now(),
                "10.0.0.***", "fp-x", true, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId required");
        assertThatThrownBy(() -> publisher().publishTokenReuseDetected(
                ACCOUNT_ID, "  ", "jti-001", null, Instant.now(),
                "10.0.0.***", "fp-x", true, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId required");
    }

    @Test
    @DisplayName("publishAuthSessionRevoked writes per-device payload with nested actor")
    void publishAuthSessionRevoked_correctPayload() throws Exception {
        List<String> revokedJtis = List.of("jti-1", "jti-2");
        Instant revokedAt = Instant.parse("2026-04-12T10:00:00Z");

        publisher().publishAuthSessionRevoked(
                ACCOUNT_ID, "fan-platform", "dev-1", "USER_REQUESTED",
                revokedJtis, revokedAt, "USER", ACCOUNT_ID);

        Map<String, Object> envelope = envelopeOf(captureRow());
        assertThat(envelope).containsEntry("eventType", "auth.session.revoked");
        assertThat(envelope).containsEntry("source", "auth-service");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        assertThat(payload).containsEntry("deviceId", "dev-1");
        assertThat(payload).containsEntry("reason", "USER_REQUESTED");
        assertThat(payload).containsEntry("revokedJtis", revokedJtis);
        assertThat(payload).containsEntry("revokedAt", revokedAt.toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> actor = (Map<String, Object>) payload.get("actor");
        assertThat(actor).containsEntry("type", "USER");
        assertThat(actor).containsEntry("accountId", ACCOUNT_ID);
    }

    @Test
    @DisplayName("publishLoginSucceeded (legacy 3-arg) emits exactly the 7 common fields in order; no deviceId/isNewDevice")
    void publishLoginSucceeded_3arg_exactlyCommonFields() throws Exception {
        SessionContext ctx = new SessionContext("127.0.0.1", "Chrome/120", "fp-123", "JP");

        publisher().publishLoginSucceeded(ACCOUNT_ID, "jti-005", ctx);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelopeOf(captureRow()).get("payload");
        assertThat(payload.keySet()).containsExactly(
                "accountId", "ipMasked", "userAgentFamily", "deviceFingerprint",
                "geoCountry", "sessionJti", "timestamp");
        assertThat(payload).containsEntry("geoCountry", "JP");
    }

    @Test
    @DisplayName("publishLoginSucceeded (6-arg) preserves field order with timestamp last")
    void publishLoginSucceeded_6arg_preservesOrder() throws Exception {
        SessionContext ctx = new SessionContext("127.0.0.1", "Chrome/120", "fp-123", "KR");

        publisher().publishLoginSucceeded(
                ACCOUNT_ID, "jti-003", ctx, "dev-oauth", true, "OAUTH_GOOGLE");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelopeOf(captureRow()).get("payload");
        assertThat(payload.keySet()).containsExactly(
                "accountId", "ipMasked", "userAgentFamily", "deviceFingerprint",
                "geoCountry", "sessionJti", "deviceId", "isNewDevice", "loginMethod",
                "timestamp");
        assertThat(payload).containsEntry("loginMethod", "OAUTH_GOOGLE");
    }

    @Test
    @DisplayName("publishLoginAttempted includes geoCountry; tenantId guard enforced")
    void publishLoginAttempted_includesGeoCountry_andGuard() throws Exception {
        SessionContext ctx = new SessionContext("127.0.0.1", "Chrome/120", "fp-123", "KR");
        publisher().publishLoginAttempted(ACCOUNT_ID, "email-hash", "fan-platform", ctx);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelopeOf(captureRow()).get("payload");
        assertThat(payload).containsEntry("geoCountry", "KR");

        assertThatThrownBy(() ->
                publisher().publishLoginAttempted(ACCOUNT_ID, "email-hash", null, ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId required");
    }
}
