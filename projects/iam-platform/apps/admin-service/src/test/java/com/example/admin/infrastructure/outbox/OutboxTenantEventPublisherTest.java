package com.example.admin.infrastructure.outbox;

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
 * Unit test for {@link OutboxTenantEventPublisher} (TASK-BE-452).
 *
 * <p>The v1 {@code TenantEventPublisher} SELF-BUILT the full canonical 7-field
 * envelope and passed it to {@code saveEvent} (so the wire IS an envelope, NOT a flat
 * payload). The v2 adapter reproduces those EXACT bytes (eventId/eventType/source/
 * occurredAt/schemaVersion/partitionKey/payload) — NO double-wrap — and reuses the
 * envelope's own {@code eventId} as the {@code admin_outbox} row PK. Writes into the
 * SAME table as admin.action.performed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OutboxTenantEventPublisherTest {

    @Mock
    private AdminOutboxJpaRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxTenantEventPublisher publisher() {
        return new OutboxTenantEventPublisher(outboxRepository, objectMapper,
                Clock.fixed(Instant.parse("2026-04-14T10:00:00Z"), ZoneOffset.UTC));
    }

    private AdminOutboxJpaEntity captureRow() {
        ArgumentCaptor<AdminOutboxJpaEntity> captor = ArgumentCaptor.forClass(AdminOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("tenant.created — self-built 7-field envelope reproduced; eventId == row PK; inner payload nested")
    void publishTenantCreated_selfBuiltEnvelope() throws Exception {
        Instant createdAt = Instant.parse("2026-04-14T10:00:00Z");
        publisher().publishTenantCreated("acme-corp", "Acme Corp", "B2B", "op-1", createdAt);

        AdminOutboxJpaEntity row = captureRow();
        assertThat(row.getAggregateType()).isEqualTo("Tenant");
        assertThat(row.getAggregateId()).isEqualTo("acme-corp");
        assertThat(row.getEventType()).isEqualTo("tenant.created");
        assertThat(row.getPartitionKey()).isEqualTo("acme-corp");

        JsonNode env = objectMapper.readTree(row.getPayload());
        // The full 7-field envelope (NOT double-wrapped).
        assertThat(env.get("eventType").asText()).isEqualTo("tenant.created");
        assertThat(env.get("source").asText()).isEqualTo("admin-service");
        assertThat(env.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(env.get("partitionKey").asText()).isEqualTo("acme-corp");
        assertThat(env.get("occurredAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
        // Row PK == the envelope's own eventId.
        assertThat(row.getId()).isEqualTo(UUID.fromString(env.get("eventId").asText()));
        // No double-wrap: env.payload is the inner tenant payload, NOT another envelope.
        JsonNode inner = env.get("payload");
        assertThat(inner.has("eventType")).isFalse();
        assertThat(inner.get("tenantId").asText()).isEqualTo("acme-corp");
        assertThat(inner.get("displayName").asText()).isEqualTo("Acme Corp");
        assertThat(inner.get("tenantType").asText()).isEqualTo("B2B");
        assertThat(inner.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(inner.get("actor").get("type").asText()).isEqualTo("operator");
        assertThat(inner.get("actor").get("id").asText()).isEqualTo("op-1");
        assertThat(inner.get("createdAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
    }

    @Test
    @DisplayName("tenant.suspended — previousStatus/currentStatus + reason in inner payload")
    void publishTenantSuspended() throws Exception {
        publisher().publishTenantSuspended("acme-corp", "op-2", "billing-overdue",
                Instant.parse("2026-04-14T10:00:00Z"));

        JsonNode env = objectMapper.readTree(captureRow().getPayload());
        assertThat(env.get("eventType").asText()).isEqualTo("tenant.suspended");
        JsonNode inner = env.get("payload");
        assertThat(inner.get("previousStatus").asText()).isEqualTo("ACTIVE");
        assertThat(inner.get("currentStatus").asText()).isEqualTo("SUSPENDED");
        assertThat(inner.get("reason").asText()).isEqualTo("billing-overdue");
        assertThat(inner.get("suspendedAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
    }

    @Test
    @DisplayName("tenant.updated — displayName change nested under changes")
    void publishTenantUpdated() throws Exception {
        publisher().publishTenantUpdated("acme-corp", "Old Name", "New Name", "op-3",
                Instant.parse("2026-04-14T10:00:00Z"));

        JsonNode env = objectMapper.readTree(captureRow().getPayload());
        assertThat(env.get("eventType").asText()).isEqualTo("tenant.updated");
        JsonNode change = env.get("payload").get("changes").get("displayName");
        assertThat(change.get("from").asText()).isEqualTo("Old Name");
        assertThat(change.get("to").asText()).isEqualTo("New Name");
    }
}
