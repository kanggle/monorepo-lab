package com.example.erp.approval.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.erp.approval.application.event.ApprovalEventPublisher;
import com.example.erp.approval.domain.delegation.DelegationGrant;
import com.example.erp.approval.domain.delegation.DelegationScope;
import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalOutboxJpaEntity;
import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for the {@link OutboxApprovalEventPublisher} write path
 * (TASK-ERP-BE-025 — outbox v2).
 *
 * <p>Asserts each domain event persists an {@code approval_outbox} row whose
 * wire-relevant fields are preserved exactly vs the v1
 * {@code BaseEventPublisher.writeEvent}: the canonical 7-field envelope
 * ({@code eventId, eventType, source, occurredAt, schemaVersion=1, partitionKey,
 * payload}) in that field order, the row {@code id} reused as the envelope
 * {@code eventId}, {@code aggregate_type}/{@code aggregate_id}/{@code event_type}
 * matching the v1 call, {@code partition_key} = aggregateId (the v1 Kafka key),
 * and the per-event payload NON_NULL omissions preserved.
 */
class OutboxApprovalEventPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T10:15:30Z"), ZoneOffset.UTC);
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-30T00:00:00Z");

    private final ApprovalOutboxJpaRepository repository = mock(ApprovalOutboxJpaRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutboxApprovalEventPublisher publisher =
            new OutboxApprovalEventPublisher(repository, objectMapper, CLOCK);

    private DelegationGrant grant(String reason) {
        return DelegationGrant.create("dgr-1", "erp", "emp-a", "emp-d", FROM, TO,
                reason, DelegationScope.GLOBAL, null, "emp-a", FROM);
    }

    private DelegationGrant requestGrant() {
        return DelegationGrant.create("dgr-2", "erp", "emp-a", "emp-d", FROM, TO,
                "cover R1", DelegationScope.REQUEST, "appr-1", "emp-a", FROM);
    }

    @Test
    @DisplayName("publishRevoked → v2 row + canonical envelope + grantId key + payload")
    void publishRevoked() throws Exception {
        publisher.publishRevoked(grant("vacation"), "emp-a");

        ApprovalOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(ApprovalEventPublisher.EVENT_APPROVAL_DELEGATION_REVOKED);
        assertThat(row.getAggregateType()).isEqualTo("DelegationGrant");
        assertThat(row.getAggregateId()).isEqualTo("dgr-1");
        assertThat(row.getPartitionKey()).isEqualTo("dgr-1");
        assertThat(row.getOccurredAt()).isEqualTo(CLOCK.instant());
        assertThat(row.getPublishedAt()).isNull();

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        // envelope eventId == row PK (header/payload identity)
        assertThat(envelope.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(envelope.get("eventType").asText())
                .isEqualTo(ApprovalEventPublisher.EVENT_APPROVAL_DELEGATION_REVOKED);
        assertThat(envelope.get("source").asText()).isEqualTo("erp-platform-approval-service");
        assertThat(envelope.get("occurredAt").asText()).isEqualTo(CLOCK.instant().toString());
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("partitionKey").asText()).isEqualTo("dgr-1");

        JsonNode p = envelope.get("payload");
        assertThat(p.get("grantId").asText()).isEqualTo("dgr-1");
        assertThat(p.get("delegatorId").asText()).isEqualTo("emp-a");
        assertThat(p.get("delegateId").asText()).isEqualTo("emp-d");
        assertThat(p.get("reason").asText()).isEqualTo("vacation");
        assertThat(p.get("tenantId").asText()).isEqualTo("erp");
        assertThat(p.get("actor").asText()).isEqualTo("emp-a");
        assertThat(p.hasNonNull("occurredAt")).isTrue();
        // validFrom/validTo are NOT in the revoke payload (a revoke does not restate the window).
        assertThat(p.has("validFrom")).isFalse();
        assertThat(p.has("validTo")).isFalse();
    }

    @Test
    @DisplayName("publishRevoked → reason ABSENT (NON_NULL) when grant has no reason")
    void publishRevokedNoReason() throws Exception {
        publisher.publishRevoked(grant(null), "emp-ops");

        JsonNode p = objectMapper.readTree(capturedRow().getPayload()).get("payload");
        assertThat(p.has("reason")).isFalse();
        assertThat(p.get("actor").asText()).isEqualTo("emp-ops");
    }

    @Test
    @DisplayName("publishDelegated GLOBAL → eventType + scope=GLOBAL present, scopeRequestId ABSENT")
    void publishDelegatedGlobalScope() throws Exception {
        publisher.publishDelegated(grant("vacation"), "emp-a");

        ApprovalOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(ApprovalEventPublisher.EVENT_APPROVAL_DELEGATED);
        assertThat(row.getAggregateType()).isEqualTo("DelegationGrant");
        assertThat(row.getAggregateId()).isEqualTo("dgr-1");

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(envelope.get("eventType").asText())
                .isEqualTo(ApprovalEventPublisher.EVENT_APPROVAL_DELEGATED);
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);

        JsonNode p = envelope.get("payload");
        assertThat(p.get("scope").asText()).isEqualTo("GLOBAL");
        assertThat(p.has("scopeRequestId")).isFalse();
    }

    @Test
    @DisplayName("publishDelegated REQUEST → scope=REQUEST + scopeRequestId present")
    void publishDelegatedRequestScope() throws Exception {
        publisher.publishDelegated(requestGrant(), "emp-a");

        JsonNode p = objectMapper.readTree(capturedRow().getPayload()).get("payload");
        assertThat(p.get("scope").asText()).isEqualTo("REQUEST");
        assertThat(p.get("scopeRequestId").asText()).isEqualTo("appr-1");
    }

    private ApprovalOutboxJpaEntity capturedRow() {
        ArgumentCaptor<ApprovalOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(ApprovalOutboxJpaEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
