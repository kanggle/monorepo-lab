package com.wms.outbound.adapter.in.web.controller;

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
import com.wms.outbound.application.result.SagaResult;
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
 * Slice tests for {@link OrderQueryController#getSaga} (outbound-service-api.md §5.1).
 *
 * <p>Three scenarios:
 * <ol>
 *   <li>Order with a saga → 200, all saga fields present including {@code state}.</li>
 *   <li>Unknown order id → 404 {@code ORDER_NOT_FOUND}.</li>
 *   <li>Unauthenticated → 401.</li>
 * </ol>
 */
@WebMvcTest(controllers = OrderQueryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class OrderQuerySagaControllerSliceTest {

    private static final UUID ORDER_ID     = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID SAGA_ID      = UUID.fromString("33333333-0000-7000-8000-000000000003");
    private static final UUID WAREHOUSE_ID = UUID.fromString("44444444-0000-7000-8000-000000000004");

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-04-29T10:05:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QueryOrderUseCase queryOrder;

    @MockitoBean
    QueryPickingRequestUseCase queryPickingRequest;

    @MockitoBean
    QuerySagaUseCase querySaga;

    // ------------------------------------------------------------------
    //  Scenario 1: order with a saga → 200, state populated
    // ------------------------------------------------------------------

    @Test
    @DisplayName("§5.1 order with saga → 200, state=RESERVED and all fields present")
    @WithMockUser(roles = "OUTBOUND_READ")
    void orderWithSaga_returns200WithState() throws Exception {
        stubOrderFound();
        SagaResult sagaResult = new SagaResult(
                SAGA_ID, ORDER_ID, "RESERVED", null, T0, T1, 1L);
        when(querySaga.findByOrderId(ORDER_ID)).thenReturn(Optional.of(sagaResult));

        mockMvc.perform(get("/api/v1/outbound/orders/{id}/saga", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sagaId").value(SAGA_ID.toString()))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.state").value("RESERVED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.startedAt").isString())
                .andExpect(jsonPath("$.lastTransitionAt").isString())
                .andExpect(jsonPath("$.failureReason").value((Object) null));
    }

    @Test
    @DisplayName("§5.1 saga with failureReason → failureReason present in response")
    @WithMockUser(roles = "OUTBOUND_READ")
    void sagaWithFailureReason_failureReasonPresent() throws Exception {
        stubOrderFound();
        SagaResult sagaResult = new SagaResult(
                SAGA_ID, ORDER_ID, "RESERVE_FAILED", "INSUFFICIENT_STOCK", T0, T1, 2L);
        when(querySaga.findByOrderId(ORDER_ID)).thenReturn(Optional.of(sagaResult));

        mockMvc.perform(get("/api/v1/outbound/orders/{id}/saga", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RESERVE_FAILED"))
                .andExpect(jsonPath("$.failureReason").value("INSUFFICIENT_STOCK"));
    }

    // ------------------------------------------------------------------
    //  Scenario 2: unknown order id → 404 ORDER_NOT_FOUND
    // ------------------------------------------------------------------

    @Test
    @DisplayName("§5.1 unknown order id → 404 ORDER_NOT_FOUND")
    @WithMockUser(roles = "OUTBOUND_READ")
    void unknownOrderId_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(queryOrder.findById(unknownId)).thenThrow(new OrderNotFoundException(unknownId));

        mockMvc.perform(get("/api/v1/outbound/orders/{id}/saga", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    // ------------------------------------------------------------------
    //  Auth gate: unauthenticated → 401
    // ------------------------------------------------------------------

    @Test
    @DisplayName("§5.1 unauthenticated request → 401")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/outbound/orders/{id}/saga", ORDER_ID))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private void stubOrderFound() {
        OrderResult orderResult = new OrderResult(
                ORDER_ID, "ORD-001", "MANUAL",
                UUID.randomUUID(), WAREHOUSE_ID,
                null, null, "PICKING",
                0L, T0, "creator", T0, "creator",
                List.of(new OrderLineResult(UUID.randomUUID(), 1, UUID.randomUUID(), null, 10)),
                SAGA_ID, "RESERVED");
        when(queryOrder.findById(ORDER_ID)).thenReturn(orderResult);
    }
}
