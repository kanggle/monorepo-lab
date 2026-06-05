package com.example.erp.approval.application.event;

import com.example.erp.approval.domain.delegation.DelegationGrant;
import com.example.erp.approval.domain.delegation.DelegationScope;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ApprovalEventPublisher#publishRevoked} (TASK-ERP-BE-015):
 * the {@code erp.approval.delegation.revoked.v1} event's topic / aggregateType /
 * partition key (= grantId) + payload keys. The outbox row's JSON envelope is
 * captured and parsed back to assert the contract shape. {@code STRICT_STUBS}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApprovalEventPublisherTest {

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-30T00:00:00Z");

    @Mock OutboxWriter outboxWriter;
    @Captor ArgumentCaptor<String> aggregateTypeCaptor;
    @Captor ArgumentCaptor<String> aggregateIdCaptor;
    @Captor ArgumentCaptor<String> eventTypeCaptor;
    @Captor ArgumentCaptor<String> payloadCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ApprovalEventPublisher publisher;

    @BeforeEach
    void setup() {
        publisher = new ApprovalEventPublisher(outboxWriter, objectMapper);
    }

    private DelegationGrant grant(String reason) {
        return DelegationGrant.create("dgr-1", "erp", "emp-a", "emp-d", FROM, TO,
                reason, DelegationScope.GLOBAL, null, "emp-a", FROM);
    }

    private DelegationGrant requestGrant() {
        return DelegationGrant.create("dgr-2", "erp", "emp-a", "emp-d", FROM, TO,
                "cover R1", DelegationScope.REQUEST, "appr-1", "emp-a", FROM);
    }

    @Test
    @DisplayName("publishRevoked → topic + aggregateType=DelegationGrant + grantId key + payload")
    void publishRevoked() throws Exception {
        publisher.publishRevoked(grant("vacation"), "emp-a");

        verify(outboxWriter).save(aggregateTypeCaptor.capture(), aggregateIdCaptor.capture(),
                eventTypeCaptor.capture(), payloadCaptor.capture());

        assertThat(aggregateTypeCaptor.getValue()).isEqualTo("DelegationGrant");
        assertThat(aggregateIdCaptor.getValue()).isEqualTo("dgr-1");
        assertThat(eventTypeCaptor.getValue()).isEqualTo("erp.approval.delegation.revoked");

        JsonNode envelope = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(envelope.at("/eventType").asText()).isEqualTo("erp.approval.delegation.revoked");
        assertThat(envelope.at("/partitionKey").asText()).isEqualTo("dgr-1");

        JsonNode p = envelope.at("/payload");
        assertThat(p.at("/grantId").asText()).isEqualTo("dgr-1");
        assertThat(p.at("/delegatorId").asText()).isEqualTo("emp-a");
        assertThat(p.at("/delegateId").asText()).isEqualTo("emp-d");
        assertThat(p.at("/reason").asText()).isEqualTo("vacation");
        assertThat(p.at("/tenantId").asText()).isEqualTo("erp");
        assertThat(p.at("/actor").asText()).isEqualTo("emp-a");
        assertThat(p.hasNonNull("occurredAt")).isTrue();
        // validFrom/validTo are NOT in the revoke payload (a revoke does not restate the window).
        assertThat(p.has("validFrom")).isFalse();
        assertThat(p.has("validTo")).isFalse();
    }

    @Test
    @DisplayName("publishRevoked → reason ABSENT (NON_NULL) when grant has no reason")
    void publishRevokedNoReason() throws Exception {
        publisher.publishRevoked(grant(null), "emp-ops");

        verify(outboxWriter).save(aggregateTypeCaptor.capture(), aggregateIdCaptor.capture(),
                eventTypeCaptor.capture(), payloadCaptor.capture());

        JsonNode p = objectMapper.readTree(payloadCaptor.getValue()).at("/payload");
        assertThat(p.has("reason")).isFalse();
        assertThat(p.at("/actor").asText()).isEqualTo("emp-ops");
    }

    // ---- TASK-ERP-BE-017: delegated payload carries scope / scopeRequestId ----

    @Test
    @DisplayName("publishDelegated GLOBAL → scope=GLOBAL present, scopeRequestId ABSENT")
    void publishDelegatedGlobalScope() throws Exception {
        publisher.publishDelegated(grant("vacation"), "emp-a");

        verify(outboxWriter).save(aggregateTypeCaptor.capture(), aggregateIdCaptor.capture(),
                eventTypeCaptor.capture(), payloadCaptor.capture());

        assertThat(eventTypeCaptor.getValue()).isEqualTo("erp.approval.delegated");
        JsonNode p = objectMapper.readTree(payloadCaptor.getValue()).at("/payload");
        assertThat(p.at("/scope").asText()).isEqualTo("GLOBAL");
        assertThat(p.has("scopeRequestId")).isFalse();
    }

    @Test
    @DisplayName("publishDelegated REQUEST → scope=REQUEST + scopeRequestId present")
    void publishDelegatedRequestScope() throws Exception {
        publisher.publishDelegated(requestGrant(), "emp-a");

        verify(outboxWriter).save(aggregateTypeCaptor.capture(), aggregateIdCaptor.capture(),
                eventTypeCaptor.capture(), payloadCaptor.capture());

        JsonNode p = objectMapper.readTree(payloadCaptor.getValue()).at("/payload");
        assertThat(p.at("/scope").asText()).isEqualTo("REQUEST");
        assertThat(p.at("/scopeRequestId").asText()).isEqualTo("appr-1");
    }
}
