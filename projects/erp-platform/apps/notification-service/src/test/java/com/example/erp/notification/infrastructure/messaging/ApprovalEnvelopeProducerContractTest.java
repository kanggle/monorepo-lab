package com.example.erp.notification.infrastructure.messaging;

import com.example.erp.notification.application.command.NotifyOnApprovalCommand;
import com.example.erp.notification.application.command.NotifyOnDelegationCommand;
import com.example.erp.notification.domain.notification.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-service contract guard for the approval → notification seam
 * (TASK-ERP-BE-043). The read-model side has the same guard
 * ({@code DelegationEnvelopeProducerContractTest}) — <b>both</b> exist on purpose:
 * the envelope tenant gate lived in two services and only one of them was ever
 * counted, so fixing the read-model copy alone would have filled
 * {@code /erp/delegation} while the ERP inbox stayed structurally empty. A guard
 * that lives in only one of two copies is how the second copy survives a fix.
 *
 * <p>The envelope below mirrors {@code approval-service}'s
 * {@code OutboxApprovalEventPublisher.writeEvent} verbatim (separate Gradle
 * modules, no shared test classpath) and carries the tenant production actually
 * emits ({@code demo-corp}), not the literal {@code erp} that every hand-written
 * fixture used to assume.
 */
class ApprovalEnvelopeProducerContractTest {

    /** The tenant the approval producer stamps in production. */
    private static final String TENANT = "demo-corp";

    private final ObjectMapper om = new ObjectMapper();
    private final EnvelopeToCommandMapper mapper = new EnvelopeToCommandMapper(om);

    /** {@code OutboxApprovalEventPublisher.payload} — verbatim key order. */
    private Map<String, Object> submittedPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("approvalRequestId", "appr-019fd768-0000-7000-8000-000000000001");
        p.put("subjectType", "DEPARTMENT");
        p.put("subjectId", "dept-1");
        p.put("approverId", "emp-approver");
        p.put("submitterId", "emp-submitter");
        p.put("tenantId", TENANT);
        p.put("occurredAt", "2026-06-01T00:00:00Z");
        p.put("actor", "platform-console-web");
        p.put("currentStage", 0);
        p.put("totalStages", 1);
        return p;
    }

    /** {@code OutboxApprovalEventPublisher.publishDelegated} — verbatim key order. */
    private Map<String, Object> delegatedPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("grantId", "dgr-019fd768-0000-7000-8000-000000000001");
        p.put("delegatorId", "emp-A");
        p.put("delegateId", "emp-D");
        p.put("validFrom", "2026-06-01T00:00:00Z");
        p.put("validTo", "2026-06-30T00:00:00Z");
        p.put("reason", "휴가 대결");
        p.put("scope", "GLOBAL");
        p.put("tenantId", TENANT);
        p.put("occurredAt", "2026-06-01T00:00:00Z");
        p.put("actor", "platform-console-web");
        return p;
    }

    /** {@code OutboxApprovalEventPublisher.writeEvent} — verbatim key order. */
    private String producerWire(String eventType, String aggregateType, String aggregateId,
                                Map<String, Object> payload) throws Exception {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", "0190aa00-0000-7000-8000-000000000002");
        env.put("eventType", eventType);
        env.put("source", "erp-platform-approval-service");
        env.put("occurredAt", "2026-06-01T00:00:00Z");
        env.put("schemaVersion", 1);
        env.put("tenantId", payload.get("tenantId"));
        env.put("aggregateType", aggregateType);
        env.put("aggregateId", aggregateId);
        env.put("partitionKey", aggregateId);
        env.put("payload", payload);
        return om.writeValueAsString(env);
    }

    @Test
    void currentSubmittedWireIsAcceptedAndCarriesTheProducersTenant() throws Exception {
        NotifyOnApprovalCommand cmd = mapper.map(
                producerWire("erp.approval.submitted", "ApprovalRequest",
                        "appr-019fd768-0000-7000-8000-000000000001", submittedPayload()),
                "erp.approval.submitted.v1", NotificationType.APPROVAL_SUBMITTED);

        assertThat(cmd.event().approverId()).isEqualTo("emp-approver");
        assertThat(cmd.event().tenantId())
                .as("the notification row must record the fact's own tenant")
                .isEqualTo(TENANT)
                .isNotEqualTo("erp");
    }

    @Test
    void currentDelegatedWireIsAcceptedAndCarriesTheProducersTenant() throws Exception {
        NotifyOnDelegationCommand cmd = mapper.mapDelegation(
                producerWire("erp.approval.delegated", "DelegationGrant",
                        "dgr-019fd768-0000-7000-8000-000000000001", delegatedPayload()),
                "erp.approval.delegated.v1");

        assertThat(cmd.event().delegateId()).isEqualTo("emp-D");
        assertThat(cmd.event().tenantId()).isEqualTo(TENANT).isNotEqualTo("erp");
    }

    /**
     * The legacy 7-field wire (no top-level {@code aggregateId}) — the first of the
     * two historical breaks on this seam. Pinned so a producer regression fails
     * here rather than emptying the inbox.
     */
    @Test
    void legacySevenFieldWireWithoutTopLevelAggregateIdIsRejected() throws Exception {
        Map<String, Object> payload = submittedPayload();
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", "0190aa00-0000-7000-8000-000000000002");
        env.put("eventType", "erp.approval.submitted");
        env.put("source", "erp-platform-approval-service");
        env.put("occurredAt", "2026-06-01T00:00:00Z");
        env.put("schemaVersion", 1);
        env.put("partitionKey", payload.get("approvalRequestId"));
        env.put("payload", payload);

        assertThatThrownBy(() -> mapper.map(om.writeValueAsString(env),
                "erp.approval.submitted.v1", NotificationType.APPROVAL_SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }
}
