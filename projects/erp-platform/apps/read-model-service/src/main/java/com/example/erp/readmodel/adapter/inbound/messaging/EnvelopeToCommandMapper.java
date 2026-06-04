package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.command.MasterChangeCommand;
import com.example.erp.readmodel.domain.common.ChangeKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Maps a raw Kafka record value (masterdata envelope JSON) to a
 * {@link MasterChangeCommand}. A malformed JSON, an invalid envelope (null
 * {@code eventId}/{@code aggregateId}/{@code payload}), or an unknown
 * {@code changeKind} is rejected with {@link InvalidEnvelopeException} so the
 * consumer routes it straight to the DLT without retry (Failure Mode 2). All
 * Kafka / Jackson types stay in this adapter — the application layer receives a
 * pure command.
 */
@Component
public class EnvelopeToCommandMapper {

    private final ObjectMapper objectMapper;

    public EnvelopeToCommandMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MasterChangeCommand map(String rawValue, String topic) {
        MasterEventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(rawValue, MasterEventEnvelope.class);
        } catch (Exception e) {
            throw new InvalidEnvelopeException("Unparseable envelope on topic " + topic
                    + ": " + e.getMessage());
        }
        if (envelope == null || !envelope.isValid()) {
            throw new InvalidEnvelopeException("Invalid envelope (missing eventId/aggregateId/"
                    + "payload) on topic " + topic);
        }
        ChangeKind changeKind = ChangeKind.fromOrNull(envelope.changeKindRaw());
        if (changeKind == null) {
            throw new InvalidEnvelopeException("Unknown/absent changeKind '"
                    + envelope.changeKindRaw() + "' on topic " + topic);
        }
        return new MasterChangeCommand(
                envelope.eventId(),
                topic,
                envelope.aggregateId(),
                changeKind,
                envelope.effectiveOccurredAt(),
                envelope.after());
    }
}
