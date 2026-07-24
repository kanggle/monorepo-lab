package com.example.scmplatform.logistics.application.routing;

import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.domain.model.Carrier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the framework-free {@link CarrierRouter} (ADR-053 §D3). No Spring — the router is
 * constructed directly with a {@code carrierCode → vendor} registry + a {@link SimpleMeterRegistry}.
 *
 * <ul>
 *   <li>domestic carrierCode → 굿스플로;</li>
 *   <li>international carrierCode → EasyPost;</li>
 *   <li><b>null</b> carrierCode → default vendor + {@code CARRIER_UNROUTABLE} degrade (no drop);</li>
 *   <li><b>unmapped</b> carrierCode → default vendor + degrade.</li>
 * </ul>
 * Exactly one vendor is selected; a mapped code never emits a degrade.
 */
class CarrierRouterTest {

    private final ShipmentDispatchPort easyPostPort = mock(ShipmentDispatchPort.class);
    private final ShipmentDispatchPort goodsflowPort = mock(ShipmentDispatchPort.class);
    private final ShipmentDispatchPort standalonePort = mock(ShipmentDispatchPort.class);

    private SimpleMeterRegistry meters;
    private CarrierRouter router;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        router = new CarrierRouter(
                Map.of(Carrier.EASYPOST, easyPostPort, Carrier.GOODSFLOW, goodsflowPort),
                Map.of("CJ-LOGISTICS", Carrier.GOODSFLOW, "UPS", Carrier.EASYPOST),
                Carrier.EASYPOST,
                meters);
    }

    private double unroutableCount() {
        return meters.find(CarrierRouter.UNROUTABLE_METRIC).counter() == null
                ? 0.0
                : meters.find(CarrierRouter.UNROUTABLE_METRIC).counter().count();
    }

    @Test
    void domesticCarrierCode_routesToGoodsflow_noDegrade() {
        assertThat(router.select("CJ-LOGISTICS")).isSameAs(goodsflowPort);
        assertThat(unroutableCount()).isZero();
    }

    @Test
    void internationalCarrierCode_routesToEasyPost_noDegrade() {
        assertThat(router.select("UPS")).isSameAs(easyPostPort);
        assertThat(unroutableCount()).isZero();
    }

    @Test
    void carrierCode_matchedCaseInsensitively() {
        assertThat(router.select("cj-logistics")).isSameAs(goodsflowPort);
        assertThat(router.select("  Ups ")).isSameAs(easyPostPort);
        assertThat(unroutableCount()).isZero();
    }

    @Test
    void nullCarrierCode_routesToDefaultVendor_withDegrade() {
        assertThat(router.select(null)).isSameAs(easyPostPort); // default = EASYPOST
        assertThat(unroutableCount()).isEqualTo(1.0);
    }

    @Test
    void unmappedCarrierCode_routesToDefaultVendor_withDegrade() {
        assertThat(router.select("MYSTERY-CARRIER")).isSameAs(easyPostPort);
        assertThat(unroutableCount()).isEqualTo(1.0);
    }

    @Test
    void defaultVendorGoodsflow_isHonoured() {
        CarrierRouter goodsflowDefault = new CarrierRouter(
                Map.of(Carrier.EASYPOST, easyPostPort, Carrier.GOODSFLOW, goodsflowPort),
                Map.of("UPS", Carrier.EASYPOST),
                Carrier.GOODSFLOW,
                meters);
        assertThat(goodsflowDefault.select(null)).isSameAs(goodsflowPort);
    }

    @Test
    void defaultVendorWithoutPort_isRejectedAtConstruction() {
        try {
            new CarrierRouter(
                    Map.of(Carrier.GOODSFLOW, goodsflowPort),
                    Map.of(),
                    Carrier.EASYPOST, // no EASYPOST port registered
                    meters);
            org.junit.jupiter.api.Assertions.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageContaining("default vendor");
        }
    }

    @Test
    void singleVendorPassthrough_returnsStubForEveryRoute_noDegrade() {
        CarrierRouter standalone = CarrierRouter.singleVendor(Carrier.STANDALONE, standalonePort);

        assertThat(standalone.select("CJ-LOGISTICS")).isSameAs(standalonePort);
        assertThat(standalone.select("UPS")).isSameAs(standalonePort);
        assertThat(standalone.select(null)).isSameAs(standalonePort);
        assertThat(standalone.select("ANYTHING")).isSameAs(standalonePort);
    }
}
