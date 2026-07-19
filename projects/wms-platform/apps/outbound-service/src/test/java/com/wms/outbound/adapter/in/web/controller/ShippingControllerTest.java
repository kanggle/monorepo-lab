package com.wms.outbound.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.outbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.outbound.application.port.in.ConfirmShippingUseCase;
import com.wms.outbound.application.result.ShipmentResult;
import com.wms.outbound.config.SecurityConfig;
import java.time.Instant;
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
 * {@code @WebMvcTest} slice for {@link ShippingController#confirmShipping}
 * (TASK-BE-526): {@code POST /api/v1/outbound/orders/{orderId}/shipments}.
 */
@WebMvcTest(controllers = ShippingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class ShippingControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID SHIPMENT_ID = UUID.fromString("22222222-0000-7000-8000-000000000002");

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConfirmShippingUseCase confirmShipping;

    // ------------------------------------------------------------------
    //  POST /api/v1/outbound/orders/{orderId}/shipments (confirmShipping)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("confirmShipping: valid body + OUTBOUND_WRITE -> 201, body carries shipmentId/orderId")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void confirmShipping_happyPath_returns201() throws Exception {
        ShipmentResult result = new ShipmentResult(
                SHIPMENT_ID, "SHP-001", ORDER_ID, "ORD-100", "UPS", null,
                T0, "PENDING", null, "SHIPPED", "SHIPPED", 1L, T0, "shipper-1");
        when(confirmShipping.confirm(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/outbound/orders/{orderId}/shipments", ORDER_ID)
                        .header(IDEMPOTENCY_KEY, "idem-confirm-shipping-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "carrierCode": "UPS", "version": 0 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipmentId").value(SHIPMENT_ID.toString()))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.orderStatus").value("SHIPPED"));

        verify(confirmShipping).confirm(any());
    }

    @Test
    @DisplayName("confirmShipping: carrierCode over @Size(max=40) -> 400 VALIDATION_ERROR envelope")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void confirmShipping_carrierCodeTooLong_returns400ValidationError() throws Exception {
        String tooLong = "X".repeat(41);
        mockMvc.perform(post("/api/v1/outbound/orders/{orderId}/shipments", ORDER_ID)
                        .header(IDEMPOTENCY_KEY, "idem-confirm-shipping-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "carrierCode": "%s", "version": 0 }
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("confirmShipping: OUTBOUND_READ-only caller -> 403 (SecurityConfig POST gate)")
    @WithMockUser(roles = "OUTBOUND_READ")
    void confirmShipping_readOnlyCaller_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/orders/{orderId}/shipments", ORDER_ID)
                        .header(IDEMPOTENCY_KEY, "idem-confirm-shipping-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "carrierCode": "UPS", "version": 0 }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("confirmShipping: unauthenticated -> 401")
    void confirmShipping_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/orders/{orderId}/shipments", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "carrierCode": "UPS", "version": 0 }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
