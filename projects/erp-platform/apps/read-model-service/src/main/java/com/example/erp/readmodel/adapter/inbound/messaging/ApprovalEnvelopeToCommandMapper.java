package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.command.ApprovalFactCommand;
import com.example.erp.readmodel.domain.approval.ApprovalStatus;
import com.example.erp.readmodel.domain.approval.ApprovalSubjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Maps a raw Kafka record value (approval envelope JSON) to an
 * {@link ApprovalFactCommand}. The {@link ApprovalStatus} is derived from the
 * caller-supplied topic (one consumer per topic) — NOT trusted from the
 * payload — so the projected status is tied to the subscribed topic. A malformed
 * JSON, an invalid envelope (null {@code eventId}/{@code aggregateId}/
 * {@code payload}), or an unknown {@code subjectType} is rejected with
 * {@link InvalidEnvelopeException} so the consumer routes it straight to the DLT
 * without retry (Failure Mode 2). All Kafka / Jackson types stay in this adapter
 * — the application layer receives a pure command (E5 boundary).
 *
 * <p>{@code submittedAt} is the {@code submitted} event's {@code occurredAt}
 * (the submission instant — the contract carries no separate {@code submittedAt}
 * field); it is non-null only on a {@code submitted} event. {@code finalizedAt}
 * + {@code reason} are read from the terminal payload (ABSENT-or-present).
 */
@Component
public class ApprovalEnvelopeToCommandMapper {

    private final ObjectMapper objectMapper;

    public ApprovalEnvelopeToCommandMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ApprovalFactCommand map(String rawValue, String topic, ApprovalStatus status) {
        ApprovalEventEnvelope envelope = EnvelopeParsing.parseAndValidate(
                objectMapper, rawValue, topic, ApprovalEventEnvelope.class, "approval ",
                "eventId/aggregateId/payload", ApprovalEventEnvelope::isValid);
        ApprovalSubjectType subjectType =
                ApprovalSubjectType.fromOrNull(envelope.payloadString("subjectType"));
        if (subjectType == null) {
            throw new InvalidEnvelopeException("Unknown/absent subjectType '"
                    + envelope.payloadString("subjectType") + "' on topic " + topic);
        }

        Instant occurredAt = envelope.effectiveOccurredAt();
        Instant submittedAt = status == ApprovalStatus.SUBMITTED ? occurredAt : null;
        Instant finalizedAt = status.isTerminal() ? envelope.payloadInstant("finalizedAt") : null;
        String reason = status.isTerminal() ? envelope.payloadString("reason") : null;

        return new ApprovalFactCommand(
                envelope.eventId(),
                topic,
                envelope.aggregateId(),
                status,
                subjectType,
                envelope.payloadString("subjectId"),
                envelope.payloadString("approverId"),
                envelope.payloadString("submitterId"),
                occurredAt,
                submittedAt,
                finalizedAt,
                reason);
    }
}
