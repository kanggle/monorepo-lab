package com.example.scmplatform.logistics.integration;

import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.CarrierCode;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.ShipmentId;
import com.example.scmplatform.logistics.domain.model.TrackingNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (Postgres + WireMock, full security chain) for the operator surface:
 * {@code :retry} recovery, cached-ack replay with no vendor call, and tenant fail-closed
 * (non-scm/non-entitled → 403 TENANT_FORBIDDEN; entitled → pass). Dispatch rows are seeded
 * directly (no create endpoint — the trigger is the BE-044 event).
 */
@AutoConfigureMockMvc
class DispatchRetryIntegrationTest extends AbstractLogisticsIntegrationTest {

    private static final String SHIPMENTS = "/shipments";
    private static final String SUCCESS_BODY =
            "{\"id\":\"shp_1\",\"tracking_code\":\"TRACK-NEW\","
                    + "\"selected_rate\":{\"carrier\":\"USPS\"},\"status\":\"purchased\"}";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void reset() {
        cleanDatabase();
        EASYPOST.resetAll();
    }

    @Test
    void retry_failedDispatch_scmToken_recoversToDispatched() throws Exception {
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        Dispatch failed = seedFailed(UUID.randomUUID(), "SHP-RETRY");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/logistics/dispatches/" + failed.getId() + ":retry")
                        .with(jwt().jwt(j -> j.claim("tenant_id", TENANT_SCM))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.data.trackingNo").value("TRACK-NEW"));

        EASYPOST.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void retry_alreadyDispatched_returnsCachedAck_noVendorCall() throws Exception {
        Dispatch dispatched = seedDispatched(UUID.randomUUID(), "SHP-DONE");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/logistics/dispatches/" + dispatched.getId() + ":retry")
                        .with(jwt().jwt(j -> j.claim("tenant_id", TENANT_SCM))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.data.trackingNo").value("TRACK-PRE"));

        // Already DISPATCHED → idempotent short-circuit, no vendor call.
        EASYPOST.verify(exactly(0), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void inspect_scmToken_returns200() throws Exception {
        Dispatch failed = seedFailed(UUID.randomUUID(), "SHP-INSPECT");

        mockMvc.perform(get("/api/logistics/dispatches/" + failed.getId())
                        .with(jwt().jwt(j -> j.claim("tenant_id", TENANT_SCM))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPATCH_FAILED"));
    }

    @Test
    void inspect_crossTenantToken_forbidden() throws Exception {
        Dispatch failed = seedFailed(UUID.randomUUID(), "SHP-WMS");

        mockMvc.perform(get("/api/logistics/dispatches/" + failed.getId())
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void inspect_entitledCrossTenantToken_passes() throws Exception {
        Dispatch failed = seedFailed(UUID.randomUUID(), "SHP-ENT");

        mockMvc.perform(get("/api/logistics/dispatches/" + failed.getId())
                        .with(jwt().jwt(j -> j.claim("tenant_id", "acme")
                                .claim("entitled_domains", List.of("scm")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPATCH_FAILED"));
    }

    private Dispatch seedDispatched(UUID shipmentId, String shipmentNo) {
        Dispatch dispatch = Dispatch.create(UUID.randomUUID(), ShipmentId.of(shipmentId),
                shipmentNo, UUID.randomUUID(), "ORD-" + shipmentNo, TENANT_SCM, Instant.now());
        dispatch.recordAck(TrackingNo.of("TRACK-PRE"), CarrierCode.of("USPS"),
                Carrier.EASYPOST, Instant.now());
        return persistencePort.save(dispatch);
    }
}
