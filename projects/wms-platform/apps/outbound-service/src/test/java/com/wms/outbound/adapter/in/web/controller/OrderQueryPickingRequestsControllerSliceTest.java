package com.wms.outbound.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.outbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.outbound.application.port.in.QueryOrderUseCase;
import com.wms.outbound.application.port.in.QueryPickingRequestUseCase;
import com.wms.outbound.application.port.in.QuerySagaUseCase;
import com.wms.outbound.application.result.OrderLineResult;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.application.result.PickingRequestLineResult;
import com.wms.outbound.application.result.PickingRequestResult;
import com.wms.outbound.config.SecurityConfig;
import com.wms.outbound.domain.exception.OrderNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice tests for {@link OrderQueryController#listPickingRequests} (TASK-BE-343,
 * {@code outbound-service-api.md} §2.4).
 *
 * <p>Three scenarios per AC:
 * <ol>
 *   <li>Order with a picking request → 200, {@code content[0].lines} non-empty,
 *       {@code locationId} + {@code qtyToPick} present.</li>
 *   <li>Order exists but no picking request yet → 200 {@code { content:[] }}.</li>
 *   <li>Unknown order id → 404 {@code ORDER_NOT_FOUND}.</li>
 * </ol>
 */
@WebMvcTest(controllers = OrderQueryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class OrderQueryPickingRequestsControllerSliceTest {

    private static final UUID ORDER_ID        = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID PICKING_ID      = UUID.fromString("22222222-0000-7000-8000-000000000002");
    private static final UUID SAGA_ID         = UUID.fromString("33333333-0000-7000-8000-000000000003");
    private static final UUID WAREHOUSE_ID    = UUID.fromString("44444444-0000-7000-8000-000000000004");
    private static final UUID ORDER_LINE_ID   = UUID.fromString("55555555-0000-7000-8000-000000000005");
    private static final UUID SKU_ID          = UUID.fromString("66666666-0000-7000-8000-000000000006");
    private static final UUID LOCATION_ID     = UUID.fromString("77777777-0000-7000-8000-000000000007");
    private static final UUID PICKING_LINE_ID = UUID.fromString("88888888-0000-7000-8000-000000000008");

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QueryOrderUseCase queryOrder;

    @MockitoBean
    QueryPickingRequestUseCase queryPickingRequest;

    @MockitoBean
    QuerySagaUseCase querySaga;

    // ------------------------------------------------------------------
    //  Scenario 1: order WITH a picking request → 200 with lines
    // ------------------------------------------------------------------

    @Test
    @DisplayName("§2.4 order with picking request → 200, content[0].lines carries locationId + qtyToPick")
    @WithMockUser(roles = "OUTBOUND_READ")
    void orderWithPickingRequest_returns200WithLines() throws Exception {
        stubOrderFound();
        PickingRequestLineResult line = new PickingRequestLineResult(
                PICKING_LINE_ID, ORDER_LINE_ID, SKU_ID, null, LOCATION_ID, 50);
        PickingRequestResult pr = new PickingRequestResult(
                PICKING_ID, ORDER_ID, SAGA_ID, WAREHOUSE_ID,
                "SUBMITTED", List.of(line), 0L, T0, T0);
        when(queryPickingRequest.findByOrderId(ORDER_ID)).thenReturn(Optional.of(pr));

        mockMvc.perform(get("/api/v1/outbound/orders/{id}/picking-requests", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].pickingRequestId").value(PICKING_ID.toString()))
                .andExpect(jsonPath("$.content[0].orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.content[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.content[0].lines.length()").value(1))
                .andExpect(jsonPath("$.content[0].lines[0].locationId").value(LOCATION_ID.toString()))
                .andExpect(jsonPath("$.content[0].lines[0].qtyToPick").value(50))
                .andExpect(jsonPath("$.content[0].lines[0].orderLineId").value(ORDER_LINE_ID.toString()))
                .andExpect(jsonPath("$.content[0].lines[0].skuId").value(SKU_ID.toString()))
                .andExpect(jsonPath("$.content[0].lines[0].lotId").doesNotExist());
    }

    @Test
    @DisplayName("§2.4 lot-tracked line → lotId present in response")
    @WithMockUser(roles = "OUTBOUND_READ")
    void lotTrackedLine_lotIdPresent() throws Exception {
        UUID lotId = UUID.randomUUID();
        stubOrderFound();
        PickingRequestLineResult line = new PickingRequestLineResult(
                PICKING_LINE_ID, ORDER_LINE_ID, SKU_ID, lotId, LOCATION_ID, 10);
        PickingRequestResult pr = new PickingRequestResult(
                PICKING_ID, ORDER_ID, SAGA_ID, WAREHOUSE_ID,
                "SUBMITTED", List.of(line), 0L, T0, T0);
        when(queryPickingRequest.findByOrderId(ORDER_ID)).thenReturn(Optional.of(pr));

        mockMvc.perform(get("/api/v1/outbound/orders/{id}/picking-requests", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lines[0].lotId").value(lotId.toString()));
    }

    // ------------------------------------------------------------------
    //  Scenario 2: order exists, no picking request yet → 200 empty
    // ------------------------------------------------------------------

    @Test
    @DisplayName("§2.4 order exists but no picking request yet → 200 { content:[] }")
    @WithMockUser(roles = "OUTBOUND_READ")
    void orderExistsNoPickingRequest_returns200EmptyContent() throws Exception {
        stubOrderFound();
        when(queryPickingRequest.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/outbound/orders/{id}/picking-requests", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ------------------------------------------------------------------
    //  Scenario 3: unknown order id → 404 ORDER_NOT_FOUND
    // ------------------------------------------------------------------

    @Test
    @DisplayName("§2.4 unknown order id → 404 ORDER_NOT_FOUND")
    @WithMockUser(roles = "OUTBOUND_READ")
    void unknownOrderId_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(queryOrder.findById(unknownId)).thenThrow(new OrderNotFoundException(unknownId));

        mockMvc.perform(get("/api/v1/outbound/orders/{id}/picking-requests", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    // ------------------------------------------------------------------
    //  Auth gate: unauthenticated → 401
    // ------------------------------------------------------------------

    @Test
    @DisplayName("§2.4 unauthenticated request → 401")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/outbound/orders/{id}/picking-requests", ORDER_ID))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private void stubOrderFound() {
        // Uses the 16-arg convenience constructor (cancel fields default to null).
        OrderResult orderResult = new OrderResult(
                ORDER_ID, "ORD-001", "MANUAL",
                UUID.randomUUID(), WAREHOUSE_ID,
                null, null, "PICKING",
                0L, T0, "creator", T0, "creator",
                List.of(new OrderLineResult(ORDER_LINE_ID, 1, SKU_ID, null, 50)),
                SAGA_ID, "REQUESTED");
        when(queryOrder.findById(ORDER_ID)).thenReturn(orderResult);
    }
}
