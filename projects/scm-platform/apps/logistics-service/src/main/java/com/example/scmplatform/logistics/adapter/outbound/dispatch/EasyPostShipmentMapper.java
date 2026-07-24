package com.example.scmplatform.logistics.adapter.outbound.dispatch;

import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Translates between the domain {@link Dispatch} and the package-private EasyPost DTOs (I7/I8),
 * and (de)serialises the response snapshot cached in {@code dispatch_request_dedupe}. Keeping
 * ser/deser here means the vendor DTOs never escape this package.
 */
@Component
class EasyPostShipmentMapper {

    private final ObjectMapper objectMapper;

    EasyPostShipmentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    EasyPostShipmentRequest toRequest(Dispatch dispatch) {
        return EasyPostShipmentRequest.of(dispatch.getShipmentNo());
    }

    DispatchAck toAck(EasyPostShipmentResponse response) {
        String carrier = response.carrier() != null ? response.carrier() : "UNKNOWN";
        return new DispatchAck(response.trackingCode(), carrier, Carrier.EASYPOST);
    }

    String serialize(EasyPostShipmentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize EasyPost response snapshot", e);
        }
    }

    DispatchAck ackFromSnapshot(String snapshot) {
        try {
            return toAck(objectMapper.readValue(snapshot, EasyPostShipmentResponse.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse cached EasyPost snapshot", e);
        }
    }
}
