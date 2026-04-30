package com.example.security.application.event;

import com.example.messaging.outbox.OutboxWriter;
import com.example.security.domain.detection.AccountLockClient;
import com.example.security.domain.detection.RiskLevel;
import com.example.security.domain.suspicious.SuspiciousEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link SecurityEventPublisher} verifying that each publish
 * method writes a canonical envelope to the outbox with the expected payload
 * fields. Mockito-based — does not start the Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("SecurityEventPublisher 단위 테스트")
class SecurityEventPublisherTest {

    @Mock
    private OutboxWriter outboxWriter;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SecurityEventPublisher publisher;

    private SuspiciousEvent newEvent(String accountId) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("ip", "203.0.113.10");
        evidence.put("country", "KR");
        return SuspiciousEvent.create(
                "evt-" + UUID.randomUUID(),
                accountId,
                "RULE_GEO_VELOCITY",
                75,
                RiskLevel.ALERT,
                evidence,
                "trigger-1",
                Instant.parse("2026-04-14T10:00:00Z"));
    }

    @Test
    @DisplayName("publishSuspiciousDetected_normalEvent_writesEnvelopeWithDetectionPayload")
    void publishSuspiciousDetected_normalEvent_writesEnvelopeWithDetectionPayload() throws Exception {
        SuspiciousEvent event = newEvent("acc-1");

        publisher.publishSuspiciousDetected(event);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("security"),
                eq("acc-1"),
                eq(SecurityEventPublisher.TOPIC_SUSPICIOUS_DETECTED),
                jsonCaptor.capture());

        JsonNode root = objectMapper.readTree(jsonCaptor.getValue());
        assertThat(root.get("eventId").asText()).isNotBlank();
        assertThat(root.get("eventType").asText()).isEqualTo("security.suspicious.detected");
        assertThat(root.get("source").asText()).isEqualTo("security-service");
        assertThat(root.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(root.get("partitionKey").asText()).isEqualTo("acc-1");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("suspiciousEventId").asText()).isEqualTo(event.getId());
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(payload.get("ruleCode").asText()).isEqualTo("RULE_GEO_VELOCITY");
        assertThat(payload.get("riskScore").asInt()).isEqualTo(75);
        assertThat(payload.get("actionTaken").asText()).isEqualTo("ALERT");
        assertThat(payload.get("triggerEventId").asText()).isEqualTo("trigger-1");
        assertThat(payload.get("detectedAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
        assertThat(payload.get("evidence").get("ip").asText()).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("publishAutoLockTriggered_successStatus_emitsLockRequestResultSuccess")
    void publishAutoLockTriggered_successStatus_emitsLockRequestResultSuccess() throws Exception {
        SuspiciousEvent event = newEvent("acc-2");

        publisher.publishAutoLockTriggered(event, AccountLockClient.Status.SUCCESS);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("security"),
                eq("acc-2"),
                eq(SecurityEventPublisher.TOPIC_AUTO_LOCK_TRIGGERED),
                jsonCaptor.capture());

        JsonNode payload = objectMapper.readTree(jsonCaptor.getValue()).get("payload");
        assertThat(payload.get("suspiciousEventId").asText()).isEqualTo(event.getId());
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-2");
        assertThat(payload.get("ruleCode").asText()).isEqualTo("RULE_GEO_VELOCITY");
        assertThat(payload.get("riskScore").asInt()).isEqualTo(75);
        assertThat(payload.get("lockRequestResult").asText()).isEqualTo("SUCCESS");
        assertThat(payload.get("lockRequestedAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("publishAutoLockTriggered_invalidTransitionStatus_emitsFailureResult")
    void publishAutoLockTriggered_invalidTransitionStatus_emitsFailureResult() throws Exception {
        SuspiciousEvent event = newEvent("acc-3");

        publisher.publishAutoLockTriggered(event, AccountLockClient.Status.INVALID_TRANSITION);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("security"),
                eq("acc-3"),
                eq(SecurityEventPublisher.TOPIC_AUTO_LOCK_TRIGGERED),
                jsonCaptor.capture());

        JsonNode payload = objectMapper.readTree(jsonCaptor.getValue()).get("payload");
        // INVALID_TRANSITION (409) collapses to FAILURE in the contract vocabulary.
        assertThat(payload.get("lockRequestResult").asText()).isEqualTo("FAILURE");
    }

    @Test
    @DisplayName("publishAutoLockPending_exhaustedRetries_emitsAccountServiceUnreachableReason")
    void publishAutoLockPending_exhaustedRetries_emitsAccountServiceUnreachableReason() throws Exception {
        SuspiciousEvent event = newEvent("acc-4");

        publisher.publishAutoLockPending(event);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("security"),
                eq("acc-4"),
                eq(SecurityEventPublisher.TOPIC_AUTO_LOCK_PENDING),
                jsonCaptor.capture());

        JsonNode root = objectMapper.readTree(jsonCaptor.getValue());
        assertThat(root.get("eventType").asText()).isEqualTo("security.auto.lock.pending");
        JsonNode payload = root.get("payload");
        assertThat(payload.get("suspiciousEventId").asText()).isEqualTo(event.getId());
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-4");
        assertThat(payload.get("reason").asText()).isEqualTo("ACCOUNT_SERVICE_UNREACHABLE");
        assertThat(payload.get("raisedAt").asText()).isNotBlank();
    }
}
