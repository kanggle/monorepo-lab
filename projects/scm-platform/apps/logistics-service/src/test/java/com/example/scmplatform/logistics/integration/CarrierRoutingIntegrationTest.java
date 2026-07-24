package com.example.scmplatform.logistics.integration;

import com.example.scmplatform.logistics.application.usecase.RetryDispatchUseCase;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.DispatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end routing: a seeded {@code DISPATCH_FAILED} dispatch (whose {@code vendor} is null)
 * re-routes deterministically from the stored {@code requested_carrier_code} on {@code :retry}
 * (BE-043 AC). Both vendor stubs return success; the test asserts which vendor the router picked.
 *
 * <ul>
 *   <li>domestic {@code CJ-LOGISTICS} → 굿스플로 (EasyPost untouched);</li>
 *   <li>international {@code UPS} → EasyPost (굿스플로 untouched);</li>
 *   <li>{@code null} → the configured default vendor (EasyPost).</li>
 * </ul>
 */
class CarrierRoutingIntegrationTest extends AbstractLogisticsIntegrationTest {

    private static final String SHIPMENTS = "/shipments";
    private static final String EASYPOST_BODY =
            "{\"id\":\"shp_1\",\"tracking_code\":\"TRACK-EP\","
                    + "\"selected_rate\":{\"carrier\":\"USPS\"},\"status\":\"purchased\"}";
    private static final String GOODSFLOW_BODY =
            "{\"id\":\"gf_1\",\"invoiceNo\":\"INV-GF\","
                    + "\"deliveryCompanyCode\":\"CJ-GLS\",\"status\":\"BOOKED\"}";

    @Autowired
    private RetryDispatchUseCase retryDispatchUseCase;

    @BeforeEach
    void reset() {
        cleanDatabase();
        EASYPOST.resetAll();
        GOODSFLOW.resetAll();
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json").withBody(EASYPOST_BODY)));
        GOODSFLOW.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(GOODSFLOW_BODY)));
    }

    @Test
    void retry_domesticCarrierCode_reroutesToGoodsflow() {
        Dispatch failed = seedFailed(UUID.randomUUID(), "SHP-KR", "CJ-LOGISTICS");

        Dispatch result = retryDispatchUseCase.retry(failed.getId());

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCHED);
        assertThat(result.getVendor()).isEqualTo(Carrier.GOODSFLOW);
        assertThat(result.getTrackingNo().value()).isEqualTo("INV-GF");
        GOODSFLOW.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
        EASYPOST.verify(exactly(0), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void retry_internationalCarrierCode_reroutesToEasyPost() {
        Dispatch failed = seedFailed(UUID.randomUUID(), "SHP-INTL", "UPS");

        Dispatch result = retryDispatchUseCase.retry(failed.getId());

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCHED);
        assertThat(result.getVendor()).isEqualTo(Carrier.EASYPOST);
        assertThat(result.getTrackingNo().value()).isEqualTo("TRACK-EP");
        EASYPOST.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
        GOODSFLOW.verify(exactly(0), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void retry_nullCarrierCode_routesToDefaultVendor() {
        Dispatch failed = seedFailed(UUID.randomUUID(), "SHP-NULL", null);

        Dispatch result = retryDispatchUseCase.retry(failed.getId());

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCHED);
        // Default vendor = EASYPOST (logistics.carrier-router.default-vendor).
        assertThat(result.getVendor()).isEqualTo(Carrier.EASYPOST);
        EASYPOST.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
        GOODSFLOW.verify(exactly(0), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }
}
