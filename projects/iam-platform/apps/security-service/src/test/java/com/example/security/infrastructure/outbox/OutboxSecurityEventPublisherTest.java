package com.example.security.infrastructure.outbox;

import com.example.security.domain.Tenants;
import com.example.security.domain.detection.AccountLockClient;
import com.example.security.domain.detection.RiskLevel;
import com.example.security.domain.pii.PiiMaskingRecord;
import com.example.security.domain.suspicious.SuspiciousEvent;
import com.example.security.infrastructure.persistence.SecurityOutboxJpaEntity;
import com.example.security.infrastructure.persistence.SecurityOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link OutboxSecurityEventPublisher} (TASK-BE-453 — outbox v1 → v2
 * write adapter). Replaces the v1 {@code SecurityEventPublisherTest} which mocked
 * the lib {@code OutboxWriter}; now we mock the per-service
 * {@link SecurityOutboxJpaRepository}, capture the persisted row, and assert the
 * envelope JSON is the byte-identical 7-field shape the v1
 * {@code BaseEventPublisher.writeEvent} path produced (wire-preservation
 * invariant): {@code eventId == row.id}, {@code source == "security-service"},
 * {@code schemaVersion == 1}, {@code partitionKey == aggregateId}, payload fields
 * verbatim.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OutboxSecurityEventPublisher 단위 테스트")
class OutboxSecurityEventPublisherTest {

    @Mock
    private SecurityOutboxJpaRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxSecurityEventPublisher publisher() {
        return new OutboxSecurityEventPublisher(outboxRepository, objectMapper,
                Clock.fixed(Instant.parse("2026-04-14T10:00:00Z"), ZoneOffset.UTC));
    }

    private SuspiciousEvent newEvent(String accountId) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("ip", "203.0.113.10");
        evidence.put("country", "KR");
        return SuspiciousEvent.create(
                "evt-" + UUID.randomUUID(),
                Tenants.DEFAULT_TENANT_ID,
                accountId,
                "RULE_GEO_VELOCITY",
                75,
                RiskLevel.ALERT,
                evidence,
                "trigger-1",
                Instant.parse("2026-04-14T10:00:00Z"));
    }

    private SecurityOutboxJpaEntity captureRow() {
        ArgumentCaptor<SecurityOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(SecurityOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("publishSuspiciousDetected writes the canonical envelope; eventId == row id")
    void publishSuspiciousDetected_writesEnvelopeWithDetectionPayload() throws Exception {
        SuspiciousEvent event = newEvent("acc-1");

        publisher().publishSuspiciousDetected(event);

        SecurityOutboxJpaEntity row = captureRow();
        assertThat(row.getEventType()).isEqualTo("security.suspicious.detected");
        assertThat(row.getAggregateType()).isEqualTo("security");
        assertThat(row.getAggregateId()).isEqualTo("acc-1");
        assertThat(row.getPartitionKey()).isEqualTo("acc-1");

        JsonNode root = objectMapper.readTree(row.getPayload());
        assertThat(root.get("eventId").asText()).isEqualTo(row.getId().toString());
        assertThat(root.get("eventType").asText()).isEqualTo("security.suspicious.detected");
        assertThat(root.get("source").asText()).isEqualTo("security-service");
        assertThat(root.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(root.get("partitionKey").asText()).isEqualTo("acc-1");
        assertThat(root.get("occurredAt").asText()).isEqualTo("2026-04-14T10:00:00Z");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("suspiciousEventId").asText()).isEqualTo(event.getId());
        assertThat(payload.get("tenantId").asText()).isEqualTo(Tenants.DEFAULT_TENANT_ID);
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(payload.get("ruleCode").asText()).isEqualTo("RULE_GEO_VELOCITY");
        assertThat(payload.get("riskScore").asInt()).isEqualTo(75);
        assertThat(payload.get("actionTaken").asText()).isEqualTo("ALERT");
        assertThat(payload.get("triggerEventId").asText()).isEqualTo("trigger-1");
        assertThat(payload.get("detectedAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
        assertThat(payload.get("evidence").get("ip").asText()).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("publishAutoLockTriggered SUCCESS status emits lockRequestResult=SUCCESS")
    void publishAutoLockTriggered_successStatus() throws Exception {
        SuspiciousEvent event = newEvent("acc-2");

        publisher().publishAutoLockTriggered(event, AccountLockClient.Status.SUCCESS);

        SecurityOutboxJpaEntity row = captureRow();
        assertThat(row.getEventType()).isEqualTo("security.auto.lock.triggered");

        JsonNode payload = objectMapper.readTree(row.getPayload()).get("payload");
        assertThat(payload.get("suspiciousEventId").asText()).isEqualTo(event.getId());
        assertThat(payload.get("tenantId").asText()).isEqualTo(Tenants.DEFAULT_TENANT_ID);
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-2");
        assertThat(payload.get("ruleCode").asText()).isEqualTo("RULE_GEO_VELOCITY");
        assertThat(payload.get("riskScore").asInt()).isEqualTo(75);
        assertThat(payload.get("lockRequestResult").asText()).isEqualTo("SUCCESS");
        assertThat(payload.get("lockRequestedAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("publishAutoLockTriggered INVALID_TRANSITION collapses to FAILURE")
    void publishAutoLockTriggered_invalidTransition() throws Exception {
        SuspiciousEvent event = newEvent("acc-3");

        publisher().publishAutoLockTriggered(event, AccountLockClient.Status.INVALID_TRANSITION);

        JsonNode payload = objectMapper.readTree(captureRow().getPayload()).get("payload");
        assertThat(payload.get("lockRequestResult").asText()).isEqualTo("FAILURE");
    }

    @Test
    @DisplayName("publishAutoLockPending emits ACCOUNT_SERVICE_UNREACHABLE reason")
    void publishAutoLockPending_exhaustedRetries() throws Exception {
        SuspiciousEvent event = newEvent("acc-4");

        publisher().publishAutoLockPending(event);

        SecurityOutboxJpaEntity row = captureRow();
        JsonNode root = objectMapper.readTree(row.getPayload());
        assertThat(root.get("eventType").asText()).isEqualTo("security.auto.lock.pending");
        JsonNode payload = root.get("payload");
        assertThat(payload.get("suspiciousEventId").asText()).isEqualTo(event.getId());
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-4");
        assertThat(payload.get("reason").asText()).isEqualTo("ACCOUNT_SERVICE_UNREACHABLE");
        assertThat(payload.get("raisedAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("publishPiiMasked writes envelope with accountId, tenantId, maskedAt, tableNames")
    void publishPiiMasked_writesCorrectEnvelope() throws Exception {
        Instant maskedAt = Instant.parse("2026-05-02T12:00:00Z");
        List<String> tables = List.of("login_history", "suspicious_events", "account_lock_history");
        PiiMaskingRecord record = new PiiMaskingRecord("acc-pii", "fan-platform", maskedAt, tables);

        publisher().publishPiiMasked(record, "source-event-id-001");

        SecurityOutboxJpaEntity row = captureRow();
        assertThat(row.getEventType()).isEqualTo("security.pii.masked");
        assertThat(row.getAggregateId()).isEqualTo("acc-pii");

        JsonNode root = objectMapper.readTree(row.getPayload());
        assertThat(root.get("eventId").asText()).isEqualTo(row.getId().toString());
        assertThat(root.get("eventType").asText()).isEqualTo("security.pii.masked");
        assertThat(root.get("source").asText()).isEqualTo("security-service");
        assertThat(root.get("partitionKey").asText()).isEqualTo("acc-pii");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-pii");
        assertThat(payload.get("tenantId").asText()).isEqualTo("fan-platform");
        assertThat(payload.get("maskedAt").asText()).isEqualTo("2026-05-02T12:00:00Z");
        assertThat(payload.get("tableNames").isArray()).isTrue();
        assertThat(payload.get("tableNames").get(0).asText()).isEqualTo("login_history");
        assertThat(payload.get("tableNames").get(1).asText()).isEqualTo("suspicious_events");
        assertThat(payload.get("tableNames").get(2).asText()).isEqualTo("account_lock_history");
    }

    @Test
    @DisplayName("publishPiiMasked throws IllegalArgumentException when tenantId is blank")
    void publishPiiMasked_blankTenantId_throws() {
        // PiiMaskingRecord itself guards blank tenantId at construction time.
        Instant maskedAt = Instant.now();
        assertThatThrownBy(() -> new PiiMaskingRecord("acc-x", "  ", maskedAt, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }
}
