package com.wms.outbound.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.outbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.outbound.application.port.in.ConfirmPickingUseCase;
import com.wms.outbound.application.port.in.QueryPickingRequestUseCase;
import com.wms.outbound.application.result.PickingConfirmationLineResult;
import com.wms.outbound.application.result.PickingConfirmationResult;
import com.wms.outbound.application.result.PickingRequestResult;
import com.wms.outbound.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice for {@link PickingController#confirmPicking}
 * (TASK-BE-526): {@code POST /api/v1/outbound/picking-requests/{id}/confirmations}.
 */
@WebMvcTest(controllers = PickingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class PickingControllerSliceTest {

    private static final UUID PICKING_REQUEST_ID = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("22222222-0000-7000-8000-000000000002");
    private static final UUID SAGA_ID = UUID.fromString("33333333-0000-7000-8000-000000000003");
    private static final UUID WAREHOUSE_ID = UUID.fromString("44444444-0000-7000-8000-000000000004");
    private static final UUID ORDER_LINE_ID = UUID.fromString("55555555-0000-7000-8000-000000000005");
    private static final UUID SKU_ID = UUID.fromString("66666666-0000-7000-8000-000000000006");
    private static final UUID LOCATION_ID = UUID.fromString("77777777-0000-7000-8000-000000000007");
    private static final UUID CONFIRMATION_ID = UUID.fromString("88888888-0000-7000-8000-000000000008");
    private static final UUID CONFIRMATION_LINE_ID = UUID.fromString("99999999-0000-7000-8000-000000000009");

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConfirmPickingUseCase confirmPicking;

    @MockitoBean
    QueryPickingRequestUseCase queryPickingRequest;

    // ------------------------------------------------------------------
    //  POST /api/v1/outbound/picking-requests/{id}/confirmations (confirmPicking)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("confirmPicking: valid body + OUTBOUND_WRITE -> 201, body carries confirmation fields")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void confirmPicking_happyPath_returns201() throws Exception {
        PickingRequestResult pickingRequest = new PickingRequestResult(
                PICKING_REQUEST_ID, ORDER_ID, SAGA_ID, WAREHOUSE_ID,
                "SUBMITTED", List.of(), 0L, T0, T0);
        when(queryPickingRequest.findById(PICKING_REQUEST_ID)).thenReturn(Optional.of(pickingRequest));

        PickingConfirmationResult result = new PickingConfirmationResult(
                CONFIRMATION_ID, PICKING_REQUEST_ID, ORDER_ID, "picker-1", T0, "notes",
                List.of(new PickingConfirmationLineResult(
                        CONFIRMATION_LINE_ID, ORDER_LINE_ID, SKU_ID, null, LOCATION_ID, 10)),
                "PICKED", "PICKING_CONFIRMED");
        when(confirmPicking.confirm(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/outbound/picking-requests/{id}/confirmations", PICKING_REQUEST_ID)
                        .header(IDEMPOTENCY_KEY, "idem-confirm-picking-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfirmPickingJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pickingConfirmationId").value(CONFIRMATION_ID.toString()))
                .andExpect(jsonPath("$.pickingRequestId").value(PICKING_REQUEST_ID.toString()))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.orderStatus").value("PICKED"));

        verify(confirmPicking).confirm(any());
    }

    @Test
    @DisplayName("confirmPicking: empty lines[] violates @NotEmpty -> 400 VALIDATION_ERROR envelope")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void confirmPicking_emptyLines_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/picking-requests/{id}/confirmations", PICKING_REQUEST_ID)
                        .header(IDEMPOTENCY_KEY, "idem-confirm-picking-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "notes": "notes", "lines": [] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("confirmPicking: OUTBOUND_READ-only caller -> 403 (SecurityConfig POST gate)")
    @WithMockUser(roles = "OUTBOUND_READ")
    void confirmPicking_readOnlyCaller_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/picking-requests/{id}/confirmations", PICKING_REQUEST_ID)
                        .header(IDEMPOTENCY_KEY, "idem-confirm-picking-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfirmPickingJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("confirmPicking: unauthenticated -> 401")
    void confirmPicking_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/picking-requests/{id}/confirmations", PICKING_REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfirmPickingJson()))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private String validConfirmPickingJson() {
        return """
                {
                  "notes": "notes",
                  "lines": [
                    { "orderLineId": "%s", "skuId": "%s", "actualLocationId": "%s", "qtyConfirmed": 10 }
                  ]
                }
                """.formatted(ORDER_LINE_ID, SKU_ID, LOCATION_ID);
    }
}
