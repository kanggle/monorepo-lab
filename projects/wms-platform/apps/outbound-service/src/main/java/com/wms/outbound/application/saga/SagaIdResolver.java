package com.wms.outbound.application.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.domain.model.OutboundSaga;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@code sagaId} from an inventory-event payload, falling back
 * to a {@code pickingRequestId} / {@code reservationId} lookup when the
 * envelope does not carry the saga id directly.
 *
 * <p>Behaviour mirrors the per-consumer {@code resolveSagaId} duplicates that
 * previously lived in {@code InventoryReservedConsumer},
 * {@code InventoryReleasedConsumer}, and {@code InventoryConfirmedConsumer}.
 *
 * <p>Per outbound-events.md §C1: the inventory-service publishes its replies
 * with {@code sagaId} on the payload. Older messages may only carry
 * {@code pickingRequestId} / {@code reservationId}; this resolver tolerates
 * both shapes during rolling upgrades.
 */
@Component
public class SagaIdResolver {

    private final SagaPersistencePort sagaPersistence;

    public SagaIdResolver(SagaPersistencePort sagaPersistence) {
        this.sagaPersistence = sagaPersistence;
    }

    /**
     * Try to extract {@code sagaId} from the envelope payload; if not
     * present, fall back to looking up the saga by
     * {@code pickingRequestId} / {@code reservationId}.
     *
     * @return the resolved {@code sagaId}, or {@code null} if no correlation
     *         keys are present.
     */
    public UUID resolve(JsonNode payload) {
        JsonNode sagaIdNode = payload.get("sagaId");
        if (sagaIdNode != null && !sagaIdNode.isNull() && sagaIdNode.isTextual()) {
            return UUID.fromString(sagaIdNode.asText());
        }
        JsonNode pickingRequestIdNode = payload.get("pickingRequestId");
        if (pickingRequestIdNode == null || pickingRequestIdNode.isNull()) {
            pickingRequestIdNode = payload.get("reservationId");
        }
        if (pickingRequestIdNode != null && !pickingRequestIdNode.isNull()
                && pickingRequestIdNode.isTextual()) {
            UUID pickingRequestId = UUID.fromString(pickingRequestIdNode.asText());
            Optional<OutboundSaga> saga = sagaPersistence.findByPickingRequestId(pickingRequestId);
            return saga.map(OutboundSaga::sagaId).orElse(null);
        }
        return null;
    }
}
