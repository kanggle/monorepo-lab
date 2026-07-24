package com.example.scmplatform.logistics.config;

import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.application.routing.CarrierRouter;
import com.example.scmplatform.logistics.application.routing.FulfillmentRouter;
import com.example.scmplatform.logistics.domain.model.Carrier;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Map;

/**
 * Wires the {@link CarrierRouter} bean per profile — the framework-free router itself stays
 * annotation-free; this config supplies its collaborators.
 *
 * <ul>
 *   <li><b>{@code !standalone}</b> — multi-vendor: EasyPost + 굿스플로 ports keyed by vendor, the
 *       {@code carrierCode → vendor} registry from {@link CarrierRouterProperties}, and the
 *       {@code MeterRegistry} for the {@code CARRIER_UNROUTABLE} degrade metric. The two adapters
 *       are injected by bean name (they are AOP-proxied to the {@link ShipmentDispatchPort}
 *       interface, so a concrete-type injection would not resolve).</li>
 *   <li><b>{@code standalone}</b> — single-vendor passthrough: every route resolves to the one
 *       {@code StandaloneDispatchAdapter} stub (no vendor split, no degrade), so local/CI boots
 *       credential-free.</li>
 * </ul>
 */
@Configuration
public class CarrierRouterConfig {

    @Bean
    @Profile("!standalone")
    CarrierRouter carrierRouter(
            @Qualifier("easyPostDispatchAdapter") ShipmentDispatchPort easyPostPort,
            @Qualifier("goodsflowDispatchAdapter") ShipmentDispatchPort goodsflowPort,
            CarrierRouterProperties props,
            MeterRegistry meterRegistry) {
        Map<Carrier, ShipmentDispatchPort> portsByVendor = Map.of(
                Carrier.EASYPOST, easyPostPort,
                Carrier.GOODSFLOW, goodsflowPort);
        return new CarrierRouter(portsByVendor, props.getCarrierVendors(),
                props.getDefaultVendor(), meterRegistry);
    }

    @Bean
    @Profile("standalone")
    CarrierRouter standaloneCarrierRouter(ShipmentDispatchPort standalonePort) {
        return CarrierRouter.singleVendor(Carrier.STANDALONE, standalonePort);
    }

    /**
     * The framework-free {@link FulfillmentRouter} multimodal seam (ADR-053 §D4). Profile-agnostic —
     * Phase 1 always resolves self-fulfillment (carrier dispatch); the Phase-2 3PL arm is a
     * documented extension point, not active. No collaborators in Phase 1.
     */
    @Bean
    FulfillmentRouter fulfillmentRouter() {
        return new FulfillmentRouter();
    }
}
