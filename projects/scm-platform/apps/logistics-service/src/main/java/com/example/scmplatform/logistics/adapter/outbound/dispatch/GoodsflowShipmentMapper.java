package com.example.scmplatform.logistics.adapter.outbound.dispatch;

import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Translates between the domain {@link Dispatch} and the package-private 굿스플로 DTOs (I7/I8).
 * Snapshot (de)serialisation for {@code dispatch_request_dedupe} is inherited from
 * {@link VendorShipmentMapper}; the vendor-shaped mapping stays here, so the vendor DTOs never
 * escape this package. Mirrors {@code EasyPostShipmentMapper} with the {@link Carrier#GOODSFLOW}
 * vendor stamp — the two response shapes differ (§1.9 {@code tracking_code}/{@code selected_rate}
 * vs §2.9 {@code invoiceNo}/{@code deliveryCompanyCode}) and are deliberately not merged.
 */
@Component
class GoodsflowShipmentMapper extends VendorShipmentMapper<GoodsflowShipmentRequest, GoodsflowShipmentResponse> {

    GoodsflowShipmentMapper(ObjectMapper objectMapper) {
        super(objectMapper, GoodsflowShipmentResponse.class, "굿스플로");
    }

    @Override
    GoodsflowShipmentRequest toRequest(Dispatch dispatch) {
        return GoodsflowShipmentRequest.of(dispatch.getShipmentNo());
    }

    @Override
    DispatchAck toAck(GoodsflowShipmentResponse response) {
        String carrier = response.carrier() != null ? response.carrier() : "UNKNOWN";
        return new DispatchAck(response.trackingCode(), carrier, Carrier.GOODSFLOW);
    }

    @Override
    String trackingCode(GoodsflowShipmentResponse response) {
        return response.trackingCode();
    }
}
