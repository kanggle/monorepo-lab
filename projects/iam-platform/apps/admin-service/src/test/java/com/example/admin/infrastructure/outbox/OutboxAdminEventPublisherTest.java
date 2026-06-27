package com.example.admin.infrastructure.outbox;

import com.example.admin.application.Outcome;
import com.example.admin.application.event.AdminEventPublisher;
import com.example.admin.infrastructure.persistence.AdminOutboxJpaEntity;
import com.example.admin.infrastructure.persistence.AdminOutboxJpaRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link OutboxAdminEventPublisher} (TASK-BE-452 — outbox v1 → v2
 * write adapter). Replaces the v1 {@code AdminEventPublisherTest} +
 * {@code AdminEventPublisherCanonicalEnvelopeTest} (both mocked the lib
 * {@code OutboxWriter}); now we mock the per-service {@link AdminOutboxJpaRepository},
 * capture the persisted row, and assert the FLAT canonical-action payload is
 * byte-identical to the v1 {@code saveEvent} wire (top-level
 * eventId/occurredAt/actor/action/target/outcome/reason — NOT a 7-field
 * {@code {eventType,source,schemaVersion,payload}} wrapper), with the centralised PII
 * displayHint masking preserved and the embedded eventId reused as the row PK.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OutboxAdminEventPublisherTest {

    @Mock
    private AdminOutboxJpaRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxAdminEventPublisher publisher() {
        return new OutboxAdminEventPublisher(outboxRepository, objectMapper,
                Clock.fixed(Instant.parse("2026-04-14T10:00:00Z"), ZoneOffset.UTC));
    }

    private AdminOutboxJpaEntity captureRow() {
        ArgumentCaptor<AdminOutboxJpaEntity> captor = ArgumentCaptor.forClass(AdminOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("flat canonical-action payload: all fields in order; eventId reused as row PK; NO envelope wrapper")
    void publishAdminActionPerformed_flatCanonicalFields() throws Exception {
        AdminEventPublisher.Envelope env = new AdminEventPublisher.Envelope(
                "op-uuid-1", "jti-xyz", "account.lock",
                "/api/admin/accounts/acc-1/lock", "POST", "ACCOUNT", "acc-1",
                Outcome.SUCCESS, null, Instant.parse("2026-04-14T10:00:00.123Z"));

        publisher().publishAdminActionPerformed(env);

        AdminOutboxJpaEntity row = captureRow();
        assertThat(row.getAggregateType()).isEqualTo("AdminAction");
        assertThat(row.getAggregateId()).isEqualTo("acc-1");
        assertThat(row.getEventType()).isEqualTo("admin.action.performed");
        assertThat(row.getPartitionKey()).isEqualTo("acc-1");

        JsonNode root = objectMapper.readTree(row.getPayload());
        // FLAT — no 7-field envelope wrapper.
        assertThat(root.has("eventType")).isFalse();
        assertThat(root.has("source")).isFalse();
        assertThat(root.has("schemaVersion")).isFalse();
        // Row PK == the flat payload's own eventId.
        assertThat(row.getId()).isEqualTo(UUID.fromString(root.get("eventId").asText()));
        assertThat(root.get("occurredAt").asText()).isEqualTo("2026-04-14T10:00:00.123Z");
        assertThat(root.get("actor").get("type").asText()).isEqualTo("operator");
        assertThat(root.get("actor").get("id").asText()).isEqualTo("op-uuid-1");
        assertThat(root.get("actor").get("sessionId").asText()).isEqualTo("jti-xyz");
        assertThat(root.get("action").get("permission").asText()).isEqualTo("account.lock");
        assertThat(root.get("action").get("endpoint").asText()).isEqualTo("/api/admin/accounts/acc-1/lock");
        assertThat(root.get("action").get("method").asText()).isEqualTo("POST");
        assertThat(root.get("target").get("type").asText()).isEqualTo("ACCOUNT");
        assertThat(root.get("target").get("id").asText()).isEqualTo("acc-1");
        assertThat(root.get("target").get("displayHint").isNull()).isTrue();
        assertThat(root.get("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(root.get("reason").isNull()).isTrue();
    }

    @Test
    @DisplayName("email account targetId → masked displayHint")
    void publishAdminActionPerformed_emailTarget_maskedDisplayHint() throws Exception {
        AdminEventPublisher.Envelope env = new AdminEventPublisher.Envelope(
                "op-uuid-1", null, "account.lock",
                "/api/admin/accounts/jane.doe@example.com/lock", "POST", "ACCOUNT",
                "jane.doe@example.com", Outcome.DENIED, "PERMISSION_NOT_GRANTED", Instant.now());

        publisher().publishAdminActionPerformed(env);

        JsonNode target = objectMapper.readTree(captureRow().getPayload()).get("target");
        assertThat(target.get("displayHint").asText()).isEqualTo("j***@example.com");
    }

    @Test
    @DisplayName("null targetId → aggregateId falls back to '-'; SESSION target never leaks displayHint")
    void publishAdminActionPerformed_nullTarget_and_sessionTarget() throws Exception {
        AdminEventPublisher.Envelope env = new AdminEventPublisher.Envelope(
                "op-uuid-9", "jti-9", "audit.query",
                "/api/admin/audit/queries", "GET", "AUDIT_QUERY", null,
                Outcome.SUCCESS, null, Instant.parse("2026-04-14T10:00:00Z"));

        publisher().publishAdminActionPerformed(env);

        AdminOutboxJpaEntity row = captureRow();
        assertThat(row.getAggregateId()).isEqualTo("-");
        JsonNode target = objectMapper.readTree(row.getPayload()).get("target");
        assertThat(target.get("type").asText()).isEqualTo("AUDIT_QUERY");
        assertThat(target.get("id").isNull()).isTrue();
        assertThat(target.get("displayHint").isNull()).isTrue();
    }
}
