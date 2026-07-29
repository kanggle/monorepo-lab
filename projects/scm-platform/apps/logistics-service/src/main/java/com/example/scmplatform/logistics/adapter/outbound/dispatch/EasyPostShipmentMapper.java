package com.example.scmplatform.logistics.adapter.outbound.dispatch;

import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Translates between the domain {@link Dispatch} and the package-private EasyPost DTOs (I7/I8).
 * Snapshot (de)serialisation for {@code dispatch_request_dedupe} is inherited from
 * {@link VendorShipmentMapper}; the vendor-shaped mapping stays here, so the vendor DTOs never
 * escape this package.
 */
@Component
class EasyPostShipmentMapper extends VendorShipmentMapper<EasyPostShipmentRequest, EasyPostShipmentResponse> {

    EasyPostShipmentMapper(ObjectMapper objectMapper) {
        super(objectMapper, EasyPostShipmentResponse.class, "EasyPost");
    }

    @Override
    EasyPostShipmentRequest toRequest(Dispatch dispatch) {
        return EasyPostShipmentRequest.of(dispatch.getShipmentNo());
    }

    @Override
    DispatchAck toAck(EasyPostShipmentResponse response) {
        String carrier = response.carrier() != null ? response.carrier() : "UNKNOWN";
        return new DispatchAck(response.trackingCode(), carrier, Carrier.EASYPOST);
    }

    @Override
    String trackingCode(EasyPostShipmentResponse response) {
        return response.trackingCode();
    }
}
