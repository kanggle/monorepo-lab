package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.command.DelegationFactCommand;
import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Maps a raw Kafka record value (delegation envelope JSON) to a
 * {@link DelegationFactCommand} (TASK-ERP-BE-015). The {@link DelegationFactStatus}
 * is derived from the caller-supplied topic (one handler per topic) — NOT trusted
 * from the payload — so the projected status is tied to the subscribed topic. A
 * malformed JSON, an invalid envelope (null {@code eventId}/{@code aggregateId}/
 * {@code payload}/{@code grantId}), or a non-{@code erp} tenant is rejected with
 * {@link InvalidEnvelopeException} so the consumer routes it straight to the DLT
 * without retry. All Kafka / Jackson types stay in this adapter — the application
 * layer receives a pure command (E5 boundary).
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
    private final String requiredTenant;

    public DelegationEnvelopeToCommandMapper(
            ObjectMapper objectMapper,
            @Value("${erpplatform.oauth2.required-tenant-id:erp}") String requiredTenant) {
        this.objectMapper = objectMapper;
        this.requiredTenant = requiredTenant == null || requiredTenant.isBlank()
                ? "erp" : requiredTenant;
    }

    public DelegationFactCommand map(String rawValue, String topic, DelegationFactStatus status) {
        DelegationEventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(rawValue, DelegationEventEnvelope.class);
        } catch (Exception e) {
            throw new InvalidEnvelopeException("Unparseable delegation envelope on topic " + topic
                    + ": " + e.getMessage());
        }
        if (envelope == null || !envelope.isValid()) {
            throw new InvalidEnvelopeException("Invalid delegation envelope (missing eventId/"
                    + "aggregateId/payload/grantId) on topic " + topic);
        }
        if (!envelope.hasTenant(requiredTenant)) {
            throw new InvalidEnvelopeException("Non-" + requiredTenant + " tenant '"
                    + envelope.tenantId() + "' on topic " + topic);
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
