package com.example.admin.infrastructure.outbox;

import com.example.admin.domain.rbac.ScopeSet;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * TASK-BE-477 / ADR-MONO-045 — unit test for {@link OutboxPartnershipEventPublisher}.
 * Mirrors {@code OutboxTenantEventPublisher}: each method self-builds the full 7-field
 * envelope (partitionKey = partnershipId), reuses its eventId as the row PK, and nests
 * the inner payload (NO double-wrap).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OutboxPartnershipEventPublisherTest {

    private static final String PID = "00000000-0000-7000-8000-00000000p001";

    @Mock
    private AdminOutboxJpaRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxPartnershipEventPublisher publisher() {
        return new OutboxPartnershipEventPublisher(outboxRepository, objectMapper,
                Clock.fixed(Instant.parse("2026-07-04T10:00:00Z"), ZoneOffset.UTC));
    }

    private AdminOutboxJpaEntity captureRow() {
        ArgumentCaptor<AdminOutboxJpaEntity> captor = ArgumentCaptor.forClass(AdminOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("partnership.invited — 7-field envelope, partitionKey=partnershipId, nested payload")
    void invited() throws Exception {
        Instant at = Instant.parse("2026-07-04T10:00:00Z");
        publisher().publishInvited(PID, "acme-corp", "globex",
                ScopeSet.of(List.of("wms", "scm"), List.of("WMS_OP")), "op-host", at);

        AdminOutboxJpaEntity row = captureRow();
        assertThat(row.getAggregateType()).isEqualTo("Partnership");
        assertThat(row.getAggregateId()).isEqualTo(PID);
        assertThat(row.getEventType()).isEqualTo("partnership.invited");
        assertThat(row.getPartitionKey()).isEqualTo(PID);

        JsonNode env = objectMapper.readTree(row.getPayload());
        assertThat(env.get("eventType").asText()).isEqualTo("partnership.invited");
        assertThat(env.get("source").asText()).isEqualTo("admin-service");
        assertThat(env.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(env.get("partitionKey").asText()).isEqualTo(PID);
        assertThat(row.getId()).isEqualTo(UUID.fromString(env.get("eventId").asText()));

        JsonNode inner = env.get("payload");
        assertThat(inner.has("eventType")).isFalse();
        assertThat(inner.get("partnershipId").asText()).isEqualTo(PID);
        assertThat(inner.get("hostTenantId").asText()).isEqualTo("acme-corp");
        assertThat(inner.get("partnerTenantId").asText()).isEqualTo("globex");
        assertThat(inner.get("status").asText()).isEqualTo("PENDING");
        assertThat(inner.get("delegatedScope").get("domains").get(0).asText()).isEqualTo("wms");
        assertThat(inner.get("delegatedScope").get("roles").get(0).asText()).isEqualTo("WMS_OP");
        assertThat(inner.get("actor").get("type").asText()).isEqualTo("operator");
        assertThat(inner.get("actor").get("id").asText()).isEqualTo("op-host");
    }

    @Test
    @DisplayName("partnership.terminated — one-shot event carries participantCountAtTermination + previousStatus")
    void terminated() throws Exception {
        publisher().publishTerminated(PID, "acme-corp", "globex", "ACTIVE", "ended", 3,
                "op-host", Instant.parse("2026-07-04T18:00:00Z"));

        JsonNode inner = objectMapper.readTree(captureRow().getPayload()).get("payload");
        assertThat(inner.get("previousStatus").asText()).isEqualTo("ACTIVE");
        assertThat(inner.get("currentStatus").asText()).isEqualTo("TERMINATED");
        assertThat(inner.get("participantCountAtTermination").asInt()).isEqualTo(3);
        assertThat(inner.get("reason").asText()).isEqualTo("ended");
    }

    @Test
    @DisplayName("partnership.participant_added — participantScope omitted when null")
    void participantAdded_nullScope() throws Exception {
        publisher().publishParticipantAdded(PID, "acme-corp", "globex", "op-b", null,
                "op-partner", Instant.parse("2026-07-04T13:00:00Z"));

        JsonNode inner = objectMapper.readTree(captureRow().getPayload()).get("payload");
        assertThat(inner.get("operatorId").asText()).isEqualTo("op-b");
        assertThat(inner.has("participantScope")).isFalse();
    }

    @Test
    @DisplayName("partnership.participant_added — participantScope present when non-null")
    void participantAdded_withScope() throws Exception {
        publisher().publishParticipantAdded(PID, "acme-corp", "globex", "op-b",
                ScopeSet.of(List.of("wms"), List.of("WMS_OP")),
                "op-partner", Instant.parse("2026-07-04T13:00:00Z"));

        JsonNode inner = objectMapper.readTree(captureRow().getPayload()).get("payload");
        assertThat(inner.get("participantScope").get("domains").get(0).asText()).isEqualTo("wms");
    }
}
