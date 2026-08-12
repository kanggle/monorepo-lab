package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.command.DelegationFactCommand;
import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-service contract guard for the <b>delegation</b> seam (TASK-ERP-BE-043) —
 * the approval twin of {@link MasterEventEnvelopeProducerContractTest}.
 *
 * <p>This seam broke twice, both times because each side was green against its own
 * hand-written fixture and nothing compared the two:
 * <ol>
 *   <li>the producer omitted the top-level {@code aggregateId} (the 7-field legacy
 *       shape), so {@code isValid()} rejected every real event to {@code .DLT};</li>
 *   <li>once that was fixed, the consumer rejected the envelope's <b>tenant</b>
 *       because it compared it to {@code erpplatform.oauth2.required-tenant-id}
 *       (the HTTP domain key, {@code "erp"}) while every real record carries the
 *       customer tenant {@code demo-corp} — ADR-ERP-001 — D.</li>
 * </ol>
 *
 * <p>So the envelope below is a <b>verbatim mirror</b> of what
 * {@code approval-service}'s {@code OutboxApprovalEventPublisher.writeEvent} +
 * {@code publishDelegated} serialise (the two services are separate Gradle modules
 * with no shared test classpath, so the wire is mirrored here rather than
 * imported), and it carries the tenant production actually emits. Each historical
 * break has a negative case, so a regression to either shape fails here instead of
 * in a demo.
 */
class DelegationEnvelopeProducerContractTest {

    /** The tenant the approval producer stamps in production (grant.tenant_id). */
    private static final String TENANT = "demo-corp";
    private static final String TOPIC = "erp.approval.delegated.v1";
    private static final String REVOKE_TOPIC = "erp.approval.delegation.revoked.v1";

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());
    private final DelegationEnvelopeToCommandMapper mapper =
            new DelegationEnvelopeToCommandMapper(objectMapper);

    /** {@code OutboxApprovalEventPublisher.publishDelegated} — verbatim key order. */
    private Map<String, Object> delegatedPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("grantId", "dgr-019fd768-0000-7000-8000-000000000001");
        p.put("delegatorId", "emp-A");
        p.put("delegateId", "emp-D");
        p.put("validFrom", "2026-06-01T00:00:00Z");
        p.put("validTo", "2026-06-30T00:00:00Z");
        p.put("reason", "휴가 대결");
        p.put("scope", "REQUEST");
        p.put("scopeRequestId", "appr-1");
        p.put("tenantId", TENANT);
        p.put("occurredAt", "2026-06-01T00:00:00Z");
        p.put("actor", "platform-console-web");
        return p;
    }

    /** {@code OutboxApprovalEventPublisher.publishRevoked} — verbatim key order. */
    private Map<String, Object> revokedPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("grantId", "dgr-019fd768-0000-7000-8000-000000000001");
        p.put("delegatorId", "emp-A");
        p.put("delegateId", "emp-D");
        p.put("reason", "휴가 복귀");
        p.put("tenantId", TENANT);
        p.put("occurredAt", "2026-06-10T00:00:00Z");
        p.put("actor", "platform-console-web");
        return p;
    }

    /** {@code OutboxApprovalEventPublisher.writeEvent} — verbatim key order. */
    private String producerWire(String eventType, Map<String, Object> payload) throws Exception {
        String aggregateId = String.valueOf(payload.get("grantId"));
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", "0190aa00-0000-7000-8000-000000000001");
        env.put("eventType", eventType);
        env.put("source", "erp-platform-approval-service");
        env.put("occurredAt", "2026-06-01T00:00:00Z");
        env.put("schemaVersion", 1);
        env.put("tenantId", payload.get("tenantId"));
        env.put("aggregateType", "DelegationGrant");
        env.put("aggregateId", aggregateId);
        env.put("partitionKey", aggregateId);
        env.put("payload", payload);
        return objectMapper.writeValueAsString(env);
    }

    @Test
    void currentProducerWireIsAcceptedByConsumer() throws Exception {
        DelegationFactCommand cmd = mapper.map(
                producerWire("erp.approval.delegated", delegatedPayload()),
                TOPIC, DelegationFactStatus.ACTIVE);

        assertThat(cmd.grantId()).isEqualTo("dgr-019fd768-0000-7000-8000-000000000001");
        assertThat(cmd.delegatorId()).isEqualTo("emp-A");
        assertThat(cmd.delegateId()).isEqualTo("emp-D");
        assertThat(cmd.scope()).isEqualTo("REQUEST");
        // The producer's tenant reaches the projection unchanged (ADR-ERP-001 — D).
        assertThat(cmd.tenantId()).isEqualTo(TENANT);
    }

    @Test
    void currentRevokeWireIsAcceptedByConsumer() throws Exception {
        DelegationFactCommand cmd = mapper.map(
                producerWire("erp.approval.delegation.revoked", revokedPayload()),
                REVOKE_TOPIC, DelegationFactStatus.REVOKED);

        assertThat(cmd.status()).isEqualTo(DelegationFactStatus.REVOKED);
        assertThat(cmd.tenantId()).isEqualTo(TENANT);
    }

    /**
     * Break #1 — the legacy 7-field wire (no top-level {@code aggregateId}, only
     * {@code partitionKey}). Every {@code erp.approval.*} message took this shape
     * until TASK-ERP-BE-043 fixed the producer; documented here so a regression to
     * it fails the build rather than the read model going dark.
     */
    @Test
    void legacySevenFieldWireWithoutTopLevelAggregateIdIsRejected() throws Exception {
        Map<String, Object> payload = delegatedPayload();
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", "0190aa00-0000-7000-8000-000000000001");
        env.put("eventType", "erp.approval.delegated");
        env.put("source", "erp-platform-approval-service");
        env.put("occurredAt", "2026-06-01T00:00:00Z");
        env.put("schemaVersion", 1);
        env.put("partitionKey", payload.get("grantId"));
        env.put("payload", payload);

        assertThatThrownBy(() -> mapper.map(objectMapper.writeValueAsString(env),
                TOPIC, DelegationFactStatus.ACTIVE))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    /**
     * Break #2 — the shape that was <b>correct</b> and got rejected anyway. The
     * producer's real tenant must map cleanly; if a future change reintroduces a
     * comparison against a configured tenant constant, this is the case that
     * fails. It is deliberately asserted on the value {@code demo-corp} rather
     * than on "some tenant", because {@code erp} is the one value that would have
     * passed the old gate and the one value no erp row has ever carried.
     */
    @Test
    void theRealCustomerTenantIsNotRejected() throws Exception {
        DelegationFactCommand cmd = mapper.map(
                producerWire("erp.approval.delegated", delegatedPayload()),
                TOPIC, DelegationFactStatus.ACTIVE);

        assertThat(cmd.tenantId())
                .as("the producer's tenant must survive the consumer, not be compared to a constant")
                .isEqualTo("demo-corp")
                .isNotEqualTo("erp");
    }
}
