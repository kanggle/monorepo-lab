package com.example.scmplatform.logistics.adapter.inbound.web;

import com.example.scmplatform.logistics.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.logistics.adapter.inbound.web.controller.DispatchController;
import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.application.usecase.RetryDispatchUseCase;
import com.example.scmplatform.logistics.config.SecurityConfig;
import com.example.scmplatform.logistics.domain.error.DispatchNotFoundException;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.CarrierCode;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.DispatchStatus;
import com.example.scmplatform.logistics.domain.model.ShipmentId;
import com.example.scmplatform.logistics.domain.model.TrackingNo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DispatchController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class DispatchControllerSliceTest {

    @Autowired MockMvc mockMvc;
    @MockBean DispatchPersistencePort persistencePort;
    @MockBean RetryDispatchUseCase retryDispatchUseCase;

    static final UUID DISPATCH_ID = UUID.fromString("0192dddd-0000-0000-0000-000000000001");
    static final UUID SHIPMENT_ID = UUID.fromString("0192dddd-0000-0000-0000-000000000002");
    static final UUID ORDER_ID = UUID.fromString("0192dddd-0000-0000-0000-000000000003");

    private static Dispatch pending() {
        return Dispatch.create(DISPATCH_ID, ShipmentId.of(SHIPMENT_ID), "SHP-001",
                ORDER_ID, "ORD-001", "scm", Instant.now());
    }

    private static Dispatch dispatched() {
        return Dispatch.reconstitute(DISPATCH_ID, ShipmentId.of(SHIPMENT_ID), "SHP-001",
                ORDER_ID, "ORD-001", "scm", null, CarrierCode.of("USPS"), TrackingNo.of("TRACK-1"),
                DispatchStatus.DISPATCHED, null, Carrier.EASYPOST, 1, Instant.now(), Instant.now());
    }

    @Test
    @WithMockUser
    void getDispatch_returns200() throws Exception {
        when(persistencePort.findById(DISPATCH_ID)).thenReturn(Optional.of(pending()));

        mockMvc.perform(get("/api/logistics/dispatches/" + DISPATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(DISPATCH_ID.toString()))
                .andExpect(jsonPath("$.data.shipmentId").value(SHIPMENT_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @WithMockUser
    void getDispatch_unknownId_returns404() throws Exception {
        UUID missing = UUID.randomUUID();
        when(persistencePort.findById(missing)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/logistics/dispatches/" + missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISPATCH_NOT_FOUND"));
    }

    @Test
    @WithMockUser
    void retry_failedDispatch_recoversToDispatched() throws Exception {
        when(retryDispatchUseCase.retry(eq(DISPATCH_ID))).thenReturn(dispatched());

        mockMvc.perform(post("/api/logistics/dispatches/" + DISPATCH_ID + ":retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(DISPATCH_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.data.trackingNo").value("TRACK-1"))
                .andExpect(jsonPath("$.data.carrierCode").value("USPS"));
    }

    @Test
    @WithMockUser
    void retry_alreadyDispatched_returnsCachedAck() throws Exception {
        // The use case short-circuits (no vendor call) and returns the cached DISPATCHED
        // aggregate; the controller surfaces it 200 with the original tracking number.
        when(retryDispatchUseCase.retry(eq(DISPATCH_ID))).thenReturn(dispatched());

        mockMvc.perform(post("/api/logistics/dispatches/" + DISPATCH_ID + ":retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.data.trackingNo").value("TRACK-1"));
    }

    @Test
    @WithMockUser
    void retry_unknownId_returns404() throws Exception {
        UUID missing = UUID.randomUUID();
        when(retryDispatchUseCase.retry(eq(missing)))
                .thenThrow(new DispatchNotFoundException(missing));

        mockMvc.perform(post("/api/logistics/dispatches/" + missing + ":retry"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISPATCH_NOT_FOUND"));
    }

    @Test
    void getDispatch_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/logistics/dispatches/" + DISPATCH_ID))
                .andExpect(status().isUnauthorized());
    }
}
