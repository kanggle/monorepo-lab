package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.command.DelegationFactCommand;
import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Maps a raw Kafka record value (delegation envelope JSON) to a
 * {@link DelegationFactCommand} (TASK-ERP-BE-015). The {@link DelegationFactStatus}
 * is derived from the caller-supplied topic (one handler per topic) — NOT trusted
 * from the payload — so the projected status is tied to the subscribed topic. A
 * malformed JSON, an invalid envelope (null {@code eventId}/{@code aggregateId}/
 * {@code payload}/{@code grantId}), or an envelope carrying <b>no</b> tenant at
 * all is rejected with {@link InvalidEnvelopeException} so the consumer routes it
 * straight to the DLT without retry. All Kafka / Jackson types stay in this
 * adapter — the application layer receives a pure command (E5 boundary).
 *
 * <p><b>ADR-ERP-001 — D (TASK-ERP-BE-043) — the tenant is carried, not compared.</b>
 * This mapper used to reject any envelope whose tenant was not
 * {@code erpplatform.oauth2.required-tenant-id} (default {@code "erp"}). That
 * rejection fired on <b>every real event</b>: erp records carry the customer
 * tenant ({@code demo-corp}) because the console operator reaches erp by
 * assume-tenant, and the property is the HTTP domain key rather than a tenant
 * value. The gate was also the <b>only</b> one of the six read-model consumers to
 * have it, so the invariant it claimed to protect was already broken by 16 rows in
 * the five ungated projections while this one projection stayed at zero. The
 * resolved tenant now flows into {@link DelegationFactCommand} and is persisted on
 * the projection row, so {@code delegation_fact_proj.tenant_id} agrees with its
 * source of record ({@code approval-service.delegation_grant.tenant_id}) instead
 * of falling back to the column's legacy {@code DEFAULT 'erp'}. erp staying
 * single-tenant is enforced by the distinct-{@code tenant_id}-≥-2 ratchet
 * (AC-7) rather than by refusing events here.
 *
 * <p>The {@code delegated} payload carries the validity window
 * ({@code validFrom}/{@code validTo}); the {@code revoked} payload does not (a
 * revoke does not restate the window — the projection keeps what the
 * {@code delegated} event set, or leaves it ABSENT when revoke arrives first).
 * {@code revokedAt} = the revoke event's {@code occurredAt}.
 */
@Component
public class DelegationEnvelopeToCommandMapper {

    private final ObjectMapper objectMapper;

    public DelegationEnvelopeToCommandMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DelegationFactCommand map(String rawValue, String topic, DelegationFactStatus status) {
        DelegationEventEnvelope envelope = EnvelopeParsing.parseAndValidate(
                objectMapper, rawValue, topic, DelegationEventEnvelope.class, "delegation ",
                "eventId/aggregateId/payload/grantId", DelegationEventEnvelope::isValid);
        String tenantId = envelope.resolvedTenantId();
        if (tenantId == null) {
            throw new InvalidEnvelopeException("Missing tenantId (envelope and payload) on topic "
                    + topic + " — a fact that cannot name its tenant is not projectable");
        }

        Instant occurredAt = envelope.effectiveOccurredAt();
        boolean granted = status == DelegationFactStatus.ACTIVE;
        Instant validFrom = granted ? envelope.payloadInstant("validFrom") : null;
        Instant validTo = granted ? envelope.payloadInstant("validTo") : null;
        Instant revokedAt = granted ? null : occurredAt;
        // scope/scopeRequestId are grant-time metadata — present only on a delegated
        // event (the revoke payload restates neither; TASK-ERP-BE-018).
        String scope = granted ? envelope.payloadString("scope") : null;
        String scopeRequestId = granted ? envelope.payloadString("scopeRequestId") : null;

        return new DelegationFactCommand(
                envelope.eventId(),
                tenantId,
                topic,
                envelope.payloadString("grantId"),
                status,
                envelope.payloadString("delegatorId"),
                envelope.payloadString("delegateId"),
                validFrom,
                validTo,
                envelope.payloadString("reason"),
                occurredAt,
                revokedAt,
                scope,
                scopeRequestId);
    }
}
