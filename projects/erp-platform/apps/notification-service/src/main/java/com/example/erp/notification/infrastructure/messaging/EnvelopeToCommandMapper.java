package com.example.erp.notification.infrastructure.messaging;

import com.example.erp.notification.application.command.NotifyOnApprovalCommand;
import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.render.ApprovalEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Maps a raw Kafka record value (approval envelope JSON) to a
 * {@link NotifyOnApprovalCommand}. Malformed JSON, an invalid envelope (null
 * {@code eventId} / {@code aggregateId} / {@code payload}), a non-{@code erp}
 * tenant (single-tenant invariant), or a null on the type's resolved-recipient
 * field is rejected with {@link InvalidEnvelopeException} so the consumer routes
 * it straight to the DLT without retry (Failure Modes 2 / 3 / 5). All Kafka /
 * Jackson types stay in this adapter — the application layer receives a pure
 * command.
 */
@Component
public class EnvelopeToCommandMapper {

    private final ObjectMapper objectMapper;
    private final String requiredTenantId;

    public EnvelopeToCommandMapper(
            ObjectMapper objectMapper,
            @Value("${erpplatform.oauth2.required-tenant-id:erp}") String requiredTenantId) {
        this.objectMapper = objectMapper;
        this.requiredTenantId = requiredTenantId == null || requiredTenantId.isBlank()
                ? "erp" : requiredTenantId;
    }

    public NotifyOnApprovalCommand map(String rawValue, String topic, NotificationType type) {
        ApprovalEventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(rawValue, ApprovalEventEnvelope.class);
        } catch (Exception e) {
            throw new InvalidEnvelopeException("Unparseable envelope on topic " + topic
                    + ": " + e.getMessage());
        }
        if (envelope == null || !envelope.isValid()) {
            throw new InvalidEnvelopeException("Invalid envelope (missing eventId/aggregateId/"
                    + "payload) on topic " + topic);
        }
        String tenantId = envelope.payloadString("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = envelope.tenantId();
        }
        if (tenantId == null || !requiredTenantId.equals(tenantId)) {
            throw new InvalidEnvelopeException("Out-of-contract tenantId '" + tenantId
                    + "' on topic " + topic + " (single-tenant invariant: " + requiredTenantId + ")");
        }

        String approvalRequestId = envelope.payloadString("approvalRequestId");
        if (approvalRequestId == null || approvalRequestId.isBlank()) {
            approvalRequestId = envelope.aggregateId();
        }
        String approverId = envelope.payloadString("approverId");
        String submitterId = envelope.payloadString("submitterId");

        // The resolved-recipient field for the event's type must be present.
        requireRecipientField(type, approverId, submitterId, topic);

        ApprovalEvent event = new ApprovalEvent(
                envelope.eventId(),
                type,
                tenantId,
                approvalRequestId,
                envelope.payloadString("subjectType"),
                envelope.payloadString("subjectId"),
                approverId,
                submitterId,
                envelope.payloadString("finalizedAt"),
                envelope.payloadString("reason"));
        return new NotifyOnApprovalCommand(event, topic);
    }

    private void requireRecipientField(NotificationType type, String approverId,
                                       String submitterId, String topic) {
        boolean approverRecipient = type == NotificationType.APPROVAL_SUBMITTED
                || type == NotificationType.APPROVAL_WITHDRAWN;
        String recipient = approverRecipient ? approverId : submitterId;
        if (recipient == null || recipient.isBlank()) {
            throw new InvalidEnvelopeException("Null resolved-recipient field for type " + type
                    + " on topic " + topic + " (cannot deliver to an absent recipient)");
        }
        // Both ids are required on every payload (erp-approval-events.md); guard
        // the other one too so the renderer never NPEs.
        String other = approverRecipient ? submitterId : approverId;
        if (other == null || other.isBlank()) {
            throw new InvalidEnvelopeException("Missing approver/submitter id for type " + type
                    + " on topic " + topic);
        }
    }
}
