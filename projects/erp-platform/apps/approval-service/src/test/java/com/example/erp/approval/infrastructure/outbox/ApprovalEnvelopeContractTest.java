package com.example.erp.approval.infrastructure.outbox;

import com.example.erp.approval.application.event.ApprovalEventPublisher;
import com.example.erp.approval.domain.delegation.DelegationGrant;
import com.example.erp.approval.domain.delegation.DelegationScope;
import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.domain.request.ApprovalSubject;
import com.example.erp.approval.domain.request.SubjectType;
import com.example.erp.approval.domain.route.ApprovalRoute;
import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalOutboxJpaEntity;
import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * TASK-ERP-BE-043 — the envelope contract guard for <strong>every</strong>
 * {@code erp.approval.*} event.
 *
 * <p>The defect this pins: the publisher emitted a 7-field envelope with no top-level
 * {@code tenantId} / {@code aggregateType} / {@code aggregateId}, while
 * {@code erp-approval-events.md} § Envelope declares all three and both read-model
 * consumers require {@code aggregateId}. Every message routed straight to {@code .DLT}.
 *
 * <p><b>Why this test is table-driven over all six emitters rather than one example.</b>
 * The pre-existing {@code OutboxApprovalEventPublisherTest} covers the two delegation
 * events only — the four approval-transition emitters had <em>no</em> producer-side
 * envelope assertion at all, which is part of how the omission survived. A spot-check
 * would reproduce that gap: fixing one emitter and shipping would leave the other five
 * broken, and they are only observable once a human drives each transition. Driving the
 * list here means a seventh emitter added later fails this test until it is listed.
 *
 * <p><b>Bite</b>: delete any of the three {@code envelope.put(...)} lines in
 * {@code OutboxApprovalEventPublisher.writeEvent} and all six cases go RED.
 *
 * <p>This guard is producer-side and therefore does NOT satisfy the ticket's AC-4,
 * which requires the producer's real serialized output to be fed to the consumer's
 * mapper — the two services share no test classpath today, so that needs a
 * cross-service harness. What this pins is the half that is unambiguous: the shape the
 * contract declares.
 */
class ApprovalEnvelopeContractTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-06-27T10:15:30Z"), ZoneOffset.UTC);
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-30T00:00:00Z");
    private static final String TENANT = "erp";

    private final ApprovalOutboxJpaRepository repository = mock(ApprovalOutboxJpaRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutboxApprovalEventPublisher publisher =
            new OutboxApprovalEventPublisher(repository, objectMapper, CLOCK);

    /** One emitter: what to call, the eventType it must produce, and its aggregate identity. */
    private record Emitter(String eventType, String aggregateType, String aggregateId,
                           Consumer<OutboxApprovalEventPublisher> invoke) {
    }

    private List<Emitter> allSixEmitters() {
        ApprovalRequest request = ApprovalRequest.createDraft(
                "appr-1", TENANT, new ApprovalSubject(SubjectType.DEPARTMENT, "dept-1"),
                "title", null, ApprovalRoute.singleStage("emp-sub", "emp-app"),
                "emp-sub", FROM);
        DelegationGrant grant = DelegationGrant.create("dgr-1", TENANT, "emp-a", "emp-d",
                FROM, TO, "vacation", DelegationScope.GLOBAL, null, "emp-a", FROM);

        List<Emitter> emitters = new ArrayList<>();
        emitters.add(new Emitter(ApprovalEventPublisher.EVENT_APPROVAL_SUBMITTED,
                "ApprovalRequest", "appr-1", p -> p.publishSubmitted(request, "emp-sub")));
        emitters.add(new Emitter(ApprovalEventPublisher.EVENT_APPROVAL_APPROVED,
                "ApprovalRequest", "appr-1", p -> p.publishApproved(request, "emp-app", "ok", null)));
        emitters.add(new Emitter(ApprovalEventPublisher.EVENT_APPROVAL_REJECTED,
                "ApprovalRequest", "appr-1", p -> p.publishRejected(request, "emp-app", "no", null)));
        emitters.add(new Emitter(ApprovalEventPublisher.EVENT_APPROVAL_WITHDRAWN,
                "ApprovalRequest", "appr-1", p -> p.publishWithdrawn(request, "emp-sub", "oops")));
        emitters.add(new Emitter(ApprovalEventPublisher.EVENT_APPROVAL_DELEGATED,
                "DelegationGrant", "dgr-1", p -> p.publishDelegated(grant, "emp-a")));
        emitters.add(new Emitter(ApprovalEventPublisher.EVENT_APPROVAL_DELEGATION_REVOKED,
                "DelegationGrant", "dgr-1", p -> p.publishRevoked(grant, "emp-a")));
        return emitters;
    }

    @Test
    @DisplayName("every erp.approval.* envelope carries top-level tenantId/aggregateType/aggregateId")
    void everyEmitterProducesTheContractEnvelope() throws Exception {
        List<Emitter> emitters = allSixEmitters();
        // The contract names six topics; a seventh emitter must be added here or this fails.
        assertThat(emitters).hasSize(6);

        for (Emitter emitter : emitters) {
            emitter.invoke().accept(publisher);
        }

        ArgumentCaptor<ApprovalOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(ApprovalOutboxJpaEntity.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        List<ApprovalOutboxJpaEntity> rows = captor.getAllValues();
        assertThat(rows).hasSameSizeAs(emitters);

        for (int i = 0; i < emitters.size(); i++) {
            Emitter emitter = emitters.get(i);
            JsonNode envelope = objectMapper.readTree(rows.get(i).getPayload());

            assertThat(envelope.get("eventType").asText())
                    .as("eventType of emitter %s", emitter.eventType())
                    .isEqualTo(emitter.eventType());

            // The three fields TASK-ERP-BE-043 restored. `has(...)` first: a missing key
            // and a JSON null both read as "" through asText(), so asText() alone would
            // pass on exactly the shape that caused the DLT storm.
            assertThat(envelope.has("tenantId")).as("%s carries tenantId", emitter.eventType()).isTrue();
            assertThat(envelope.get("tenantId").asText())
                    .as("tenantId of %s", emitter.eventType()).isEqualTo(TENANT);

            assertThat(envelope.has("aggregateType")).as("%s carries aggregateType", emitter.eventType()).isTrue();
            assertThat(envelope.get("aggregateType").asText())
                    .as("aggregateType of %s", emitter.eventType()).isEqualTo(emitter.aggregateType());

            assertThat(envelope.has("aggregateId")).as("%s carries aggregateId", emitter.eventType()).isTrue();
            assertThat(envelope.get("aggregateId").asText())
                    .as("aggregateId of %s", emitter.eventType()).isEqualTo(emitter.aggregateId());

            // partitionKey stays the Kafka key and must keep agreeing with aggregateId.
            assertThat(envelope.get("partitionKey").asText())
                    .as("partitionKey of %s", emitter.eventType()).isEqualTo(emitter.aggregateId());
        }
    }

    @Test
    @DisplayName("the envelope tenantId is sourced from the payload, exactly as masterdata sources it")
    void tenantIdIsSourcedFromThePayloadNotHardcoded() throws Exception {
        // A grant on a non-default tenant: if the publisher hardcoded "erp" this passes
        // the test above and still emits the wrong tenant on every real message.
        DelegationGrant otherTenant = DelegationGrant.create("dgr-9", "demo-corp",
                "emp-a", "emp-d", FROM, TO, null, DelegationScope.GLOBAL, null, "emp-a", FROM);
        publisher.publishDelegated(otherTenant, "emp-a");

        ArgumentCaptor<ApprovalOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(ApprovalOutboxJpaEntity.class);
        verify(repository).save(captor.capture());
        JsonNode envelope = objectMapper.readTree(captor.getValue().getPayload());

        assertThat(envelope.get("tenantId").asText()).isEqualTo("demo-corp");
        assertThat(envelope.get("payload").get("tenantId").asText()).isEqualTo("demo-corp");
    }
}
