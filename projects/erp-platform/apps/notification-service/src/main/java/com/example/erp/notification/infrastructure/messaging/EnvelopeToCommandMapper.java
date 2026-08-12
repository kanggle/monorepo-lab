package com.example.erp.notification.infrastructure.messaging;

import com.example.erp.notification.application.command.NotifyOnApprovalCommand;
import com.example.erp.notification.application.command.NotifyOnDelegationCommand;
import com.example.erp.notification.application.command.NotifyOnDelegationRevokedCommand;
import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.render.ApprovalEvent;
import com.example.erp.notification.domain.render.DelegationEvent;
import com.example.erp.notification.domain.render.DelegationRevokedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Maps a raw Kafka record value (approval envelope JSON) to a
 * {@link NotifyOnApprovalCommand}. Malformed JSON, an invalid envelope (null
 * {@code eventId} / {@code aggregateId} / {@code payload}), an envelope that
 * names <b>no</b> tenant, or a null on the type's resolved-recipient field is
 * rejected with {@link InvalidEnvelopeException} so the consumer routes it
 * straight to the DLT without retry (Failure Modes 2 / 3 / 5). All Kafka /
 * Jackson types stay in this adapter — the application layer receives a pure
 * command.
 *
 * <p><b>ADR-ERP-001 — D (TASK-ERP-BE-043) — the tenant is carried, not compared.</b>
 * This mapper used to require {@code tenantId ==
 * erpplatform.oauth2.required-tenant-id} (default {@code "erp"}) and reject
 * everything else as an "out-of-contract tenantId (single-tenant invariant)". It
 * was the <b>second copy</b> of the same gate — the first is
 * {@code read-model-service}'s {@code DelegationEnvelopeToCommandMapper} — and
 * because this one guards <b>every</b> {@code erp.approval.*} type rather than
 * just the two delegation topics, it made the whole ERP inbox structurally empty
 * ({@code totalElements 0}, {@code notification} 0 rows). erp records carry the
 * customer tenant ({@code demo-corp}) because the console operator reaches erp by
 * assume-tenant, while that property is the HTTP <b>domain key</b> used by
 * {@code ServiceLevelOAuth2Config} / {@code ReadAuthorizationGate} — one value
 * could not mean both. The resolved tenant is now persisted verbatim on
 * {@code Notification} / {@code NotificationDelivery}; only its <b>absence</b> is
 * invalid. erp staying single-tenant is enforced by the
 * distinct-{@code tenant_id}-≥-2 ratchet instead.
 */
@Component
public class EnvelopeToCommandMapper {

    private final ObjectMapper objectMapper;

    public EnvelopeToCommandMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * The shared mapper prologue result: the parsed + validity-checked envelope
     * and its resolved {@code tenantId} (guaranteed non-blank).
     */
    private record ValidatedEnvelope(ApprovalEventEnvelope envelope, String tenantId) {
    }

    /**
     * Parses + validates the envelope and resolves the tenant (extracted dedup —
     * the byte-identical prologue of {@link #map} / {@link #mapDelegation} /
     * {@link #mapDelegationRevoked}).
     */
    private ValidatedEnvelope parseAndValidateTenant(String rawValue, String topic) {
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
        if (tenantId == null || tenantId.isBlank()) {
            throw new InvalidEnvelopeException("Missing tenantId (envelope and payload) on topic "
                    + topic + " — a fact that cannot name its tenant is not deliverable");
        }
        return new ValidatedEnvelope(envelope, tenantId);
    }

    public NotifyOnApprovalCommand map(String rawValue, String topic, NotificationType type) {
        ValidatedEnvelope validated = parseAndValidateTenant(rawValue, topic);
        ApprovalEventEnvelope envelope = validated.envelope();
        String tenantId = validated.tenantId();

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

    /**
     * Maps the {@code erp.approval.delegated.v1} envelope to a
     * {@link NotifyOnDelegationCommand} (TASK-ERP-BE-014). Parallel to {@link #map}
     * — the delegation payload has a different shape ({@code grantId} /
     * {@code delegatorId} / {@code delegateId} / {@code validFrom}; no
     * {@code approverId} / {@code submitterId}). A malformed / invalid envelope, a
     * non-{@code erp} tenant, or a null {@code delegateId} (the recipient) is
     * rejected with {@link InvalidEnvelopeException} → straight to the DLT, no
     * retry. {@code validTo} / {@code reason} are optional (NON_NULL absent).
     */
    public NotifyOnDelegationCommand mapDelegation(String rawValue, String topic) {
        ValidatedEnvelope validated = parseAndValidateTenant(rawValue, topic);
        ApprovalEventEnvelope envelope = validated.envelope();
        String tenantId = validated.tenantId();

        String grantId = envelope.payloadString("grantId");
        if (grantId == null || grantId.isBlank()) {
            grantId = envelope.aggregateId();
        }
        String delegateId = envelope.payloadString("delegateId");
        if (delegateId == null || delegateId.isBlank()) {
            throw new InvalidEnvelopeException("Null delegateId (recipient) on topic " + topic
                    + " (cannot deliver a delegation notification to an absent delegate)");
        }
        String delegatorId = envelope.payloadString("delegatorId");
        String validFrom = envelope.payloadString("validFrom");
        if (delegatorId == null || delegatorId.isBlank()
                || validFrom == null || validFrom.isBlank()) {
            throw new InvalidEnvelopeException("Missing delegatorId/validFrom on topic " + topic);
        }

        DelegationEvent event = new DelegationEvent(
                envelope.eventId(),
                tenantId,
                grantId,
                delegatorId,
                delegateId,
                validFrom,
                envelope.payloadString("validTo"),
                envelope.payloadString("reason"));
        return new NotifyOnDelegationCommand(event, topic);
    }

    /**
     * Maps the {@code erp.approval.delegation.revoked.v1} envelope to a
     * {@link NotifyOnDelegationRevokedCommand} (TASK-ERP-BE-016). Parallel to
     * {@link #mapDelegation} — the revoke payload has NO validity window
     * ({@code grantId} / {@code delegatorId} / {@code delegateId} / {@code reason?}).
     * Invalid envelope, non-{@code erp} tenant, or a null {@code delegateId}
     * (the recipient) → {@link InvalidEnvelopeException} → immediate DLT.
     */
    public NotifyOnDelegationRevokedCommand mapDelegationRevoked(String rawValue, String topic) {
        ValidatedEnvelope validated = parseAndValidateTenant(rawValue, topic);
        ApprovalEventEnvelope envelope = validated.envelope();
        String tenantId = validated.tenantId();

        String grantId = envelope.payloadString("grantId");
        if (grantId == null || grantId.isBlank()) {
            grantId = envelope.aggregateId();
        }
        String delegateId = envelope.payloadString("delegateId");
        if (delegateId == null || delegateId.isBlank()) {
            throw new InvalidEnvelopeException("Null delegateId (recipient) on topic " + topic
                    + " (cannot deliver a revoke notification to an absent delegate)");
        }
        String delegatorId = envelope.payloadString("delegatorId");
        if (delegatorId == null || delegatorId.isBlank()) {
            throw new InvalidEnvelopeException("Missing delegatorId on topic " + topic);
        }

        DelegationRevokedEvent event = new DelegationRevokedEvent(
                envelope.eventId(),
                tenantId,
                grantId,
                delegatorId,
                delegateId,
                envelope.payloadString("reason"));
        return new NotifyOnDelegationRevokedCommand(event, topic);
    }
}
