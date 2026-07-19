package com.wms.outbound.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.outbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.outbound.application.port.in.CancelOrderUseCase;
import com.wms.outbound.application.port.in.ReceiveOrderUseCase;
import com.wms.outbound.application.result.OrderLineResult;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
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
 * {@code @WebMvcTest} slice for {@link OrderController} write endpoints
 * (TASK-BE-526): {@code POST /api/v1/outbound/orders} (createOrder) and
 * {@code POST /api/v1/outbound/orders/{id}:cancel} (cancelOrder).
 *
 * <p>Mirrors the security-infra setup of the existing read-only slices
 * ({@link OrderQuerySagaControllerTest}): real {@link SecurityConfig}
 * imported so the coarse verb-&gt;role gate in {@code SecurityConfig} L91-96
 * is exercised, application services mocked.
 */
@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class OrderControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID CUSTOMER_PARTNER_ID = UUID.fromString("22222222-0000-7000-8000-000000000002");
    private static final UUID WAREHOUSE_ID = UUID.fromString("33333333-0000-7000-8000-000000000003");
    private static final UUID SKU_ID = UUID.fromString("44444444-0000-7000-8000-000000000004");
    private static final UUID ORDER_LINE_ID = UUID.fromString("55555555-0000-7000-8000-000000000005");
    private static final UUID SAGA_ID = UUID.fromString("66666666-0000-7000-8000-000000000006");

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ReceiveOrderUseCase receiveOrder;

    @MockitoBean
    CancelOrderUseCase cancelOrder;

    // ------------------------------------------------------------------
    //  POST /api/v1/outbound/orders (createOrder)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("createOrder: valid body + OUTBOUND_WRITE -> 201, body carries orderId/orderNo/status")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void createOrder_happyPath_returns201() throws Exception {
        OrderResult result = new OrderResult(
                ORDER_ID, "ORD-100", "MANUAL",
                CUSTOMER_PARTNER_ID, WAREHOUSE_ID,
                null, "note", "PICKING",
                0L, T0, "creator", T0, "creator",
                List.of(new OrderLineResult(ORDER_LINE_ID, 1, SKU_ID, null, 10)),
                SAGA_ID, "REQUESTED");
        when(receiveOrder.receive(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/outbound/orders")
                        .header(IDEMPOTENCY_KEY, "idem-create-order-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateOrderJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.orderNo").value("ORD-100"))
                .andExpect(jsonPath("$.status").value("PICKING"));

        verify(receiveOrder).receive(any());
    }

    @Test
    @DisplayName("createOrder: empty lines[] violates @NotEmpty -> 400 VALIDATION_ERROR envelope")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void createOrder_emptyLines_returns400ValidationError() throws Exception {
        String body = """
                {
                  "orderNo": "ORD-100",
                  "customerPartnerId": "%s",
                  "warehouseId": "%s",
                  "notes": "note",
                  "lines": []
                }
                """.formatted(CUSTOMER_PARTNER_ID, WAREHOUSE_ID);

        mockMvc.perform(post("/api/v1/outbound/orders")
                        .header(IDEMPOTENCY_KEY, "idem-create-order-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("createOrder: OUTBOUND_READ-only caller -> 403 (SecurityConfig POST gate)")
    @WithMockUser(roles = "OUTBOUND_READ")
    void createOrder_readOnlyCaller_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/orders")
                        .header(IDEMPOTENCY_KEY, "idem-create-order-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateOrderJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("createOrder: unauthenticated -> 401")
    void createOrder_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateOrderJson()))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  POST /api/v1/outbound/orders/{id}:cancel (cancelOrder)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cancelOrder: valid body + OUTBOUND_WRITE -> 200, cancel fields populated")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void cancelOrder_happyPath_returns200() throws Exception {
        OrderResult result = new OrderResult(
                ORDER_ID, "ORD-100", "MANUAL",
                CUSTOMER_PARTNER_ID, WAREHOUSE_ID,
                null, "note", "CANCELLED",
                1L, T0, "creator", T0, "creator",
                List.of(new OrderLineResult(ORDER_LINE_ID, 1, SKU_ID, null, 10)),
                SAGA_ID, "CANCELLED",
                "PICKING", "Customer requested cancellation", T0, "canceller");
        when(cancelOrder.cancel(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/outbound/orders/{id}:cancel", ORDER_ID)
                        .header(IDEMPOTENCY_KEY, "idem-cancel-order-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Customer requested cancellation", "version": 0 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.previousStatus").value("PICKING"))
                .andExpect(jsonPath("$.cancelledReason").value("Customer requested cancellation"));

        verify(cancelOrder).cancel(any());
    }

    @Test
    @DisplayName("cancelOrder: reason shorter than @Size(min=3) -> 400 VALIDATION_ERROR envelope")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void cancelOrder_shortReason_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/orders/{id}:cancel", ORDER_ID)
                        .header(IDEMPOTENCY_KEY, "idem-cancel-order-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "hi", "version": 0 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("cancelOrder: OUTBOUND_READ-only caller -> 403 (SecurityConfig POST gate)")
    @WithMockUser(roles = "OUTBOUND_READ")
    void cancelOrder_readOnlyCaller_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/orders/{id}:cancel", ORDER_ID)
                        .header(IDEMPOTENCY_KEY, "idem-cancel-order-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Customer requested cancellation", "version": 0 }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("cancelOrder: unauthenticated -> 401")
    void cancelOrder_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/orders/{id}:cancel", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Customer requested cancellation", "version": 0 }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private String validCreateOrderJson() {
        return """
                {
                  "orderNo": "ORD-100",
                  "customerPartnerId": "%s",
                  "warehouseId": "%s",
                  "notes": "note",
                  "lines": [
                    { "lineNo": 1, "skuId": "%s", "qtyOrdered": 10 }
                  ]
                }
                """.formatted(CUSTOMER_PARTNER_ID, WAREHOUSE_ID, SKU_ID);
    }
}
