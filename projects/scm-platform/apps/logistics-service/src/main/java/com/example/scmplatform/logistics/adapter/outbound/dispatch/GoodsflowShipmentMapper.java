package com.example.scmplatform.logistics.adapter.outbound.dispatch;

import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Translates between the domain {@link Dispatch} and the package-private 굿스플로 DTOs (I7/I8),
 * and (de)serialises the response snapshot cached in {@code dispatch_request_dedupe}. Keeping
 * ser/deser here means the vendor DTOs never escape this package. Mirrors
 * {@code EasyPostShipmentMapper} with the {@link Carrier#GOODSFLOW} vendor stamp.
 */
@Component
class GoodsflowShipmentMapper {

    private final ObjectMapper objectMapper;

    GoodsflowShipmentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    GoodsflowShipmentRequest toRequest(Dispatch dispatch) {
        return GoodsflowShipmentRequest.of(dispatch.getShipmentNo());
    }

    DispatchAck toAck(GoodsflowShipmentResponse response) {
        String carrier = response.carrier() != null ? response.carrier() : "UNKNOWN";
        return new DispatchAck(response.trackingCode(), carrier, Carrier.GOODSFLOW);
    }

    String serialize(GoodsflowShipmentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize 굿스플로 response snapshot", e);
        }
    }

    DispatchAck ackFromSnapshot(String snapshot) {
        try {
            return toAck(objectMapper.readValue(snapshot, GoodsflowShipmentResponse.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse cached 굿스플로 snapshot", e);
        }
    }
}
