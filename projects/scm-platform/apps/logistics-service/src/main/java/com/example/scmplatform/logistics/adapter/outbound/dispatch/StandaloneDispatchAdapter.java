package com.example.scmplatform.logistics.adapter.outbound.dispatch;

import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Credential-free {@link ShipmentDispatchPort} for the {@code standalone} profile — local / CI
 * bring-up with no EasyPost key (external-integrations.md § Test Suite; ADR-053 standalone
 * parity). Returns a <b>deterministic</b> in-memory ack keyed on the shipment id so slice/IT
 * assertions hold without a vendor.
 */
@Component
@Profile("standalone")
public class StandaloneDispatchAdapter implements ShipmentDispatchPort {

    @Override
    public DispatchAck dispatch(Dispatch dispatch) {
        String trackingNo = "STANDALONE-" + dispatch.getShipmentId().value();
        return new DispatchAck(trackingNo, "STANDALONE-CARRIER", Carrier.STANDALONE);
    }
}
