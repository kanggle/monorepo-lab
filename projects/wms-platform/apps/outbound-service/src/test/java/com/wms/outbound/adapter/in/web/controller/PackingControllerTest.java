package com.wms.outbound.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.outbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.outbound.application.port.in.ConfirmPackingUseCase;
import com.wms.outbound.application.port.in.CreatePackingUnitUseCase;
import com.wms.outbound.application.port.in.QueryPackingUnitUseCase;
import com.wms.outbound.application.port.in.SealPackingUnitUseCase;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.application.result.PackingUnitLineResult;
import com.wms.outbound.application.result.PackingUnitResult;
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
 * {@code @WebMvcTest} slice for {@link PackingController} write endpoints
 * (TASK-BE-526): {@code POST .../packing-units} (createUnit),
 * {@code PATCH .../packing-units/{id}} (sealUnit), and
 * {@code POST .../packing/confirm} (confirmPacking).
 */
@WebMvcTest(controllers = PackingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class PackingControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID PACKING_UNIT_ID = UUID.fromString("22222222-0000-7000-8000-000000000002");
    private static final UUID ORDER_LINE_ID = UUID.fromString("33333333-0000-7000-8000-000000000003");
    private static final UUID SKU_ID = UUID.fromString("44444444-0000-7000-8000-000000000004");
    private static final UUID PACKING_UNIT_LINE_ID = UUID.fromString("55555555-0000-7000-8000-000000000005");

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CreatePackingUnitUseCase createPackingUnit;

    @MockitoBean
    SealPackingUnitUseCase sealPackingUnit;

    @MockitoBean
    ConfirmPackingUseCase confirmPacking;

    @MockitoBean
    QueryPackingUnitUseCase queryPackingUnit;

    // ------------------------------------------------------------------
    //  POST /api/v1/outbound/orders/{orderId}/packing-units (createUnit)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("createUnit: valid body + OUTBOUND_WRITE -> 201, body carries packingUnitId/status")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void createUnit_happyPath_returns201() throws Exception {
        PackingUnitResult result = new PackingUnitResult(
                PACKING_UNIT_ID, ORDER_ID, "CTN-001", "BOX",
                500, 30, 20, 10, "notes", "OPEN",
                List.of(new PackingUnitLineResult(PACKING_UNIT_LINE_ID, ORDER_LINE_ID, SKU_ID, null, 5)),
                "PACKING", 0L, T0, T0);
        when(createPackingUnit.create(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/outbound/orders/{orderId}/packing-units", ORDER_ID)
                        .header(IDEMPOTENCY_KEY, "idem-create-unit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateUnitJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.packingUnitId").value(PACKING_UNIT_ID.toString()))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(createPackingUnit).create(any());
    }

    @Test
    @DisplayName("createUnit: negative weightGrams violates @PositiveOrZero -> 400 VALIDATION_ERROR")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void createUnit_negativeWeight_returns400ValidationError() throws Exception {
        String body = """
                {
                  "cartonNo": "CTN-001",
                  "packingType": "BOX",
                  "weightGrams": -1,
                  "lines": [
                    { "orderLineId": "%s", "skuId": "%s", "qty": 5 }
                  ]
                }
                """.formatted(ORDER_LINE_ID, SKU_ID);

        mockMvc.perform(post("/api/v1/outbound/orders/{orderId}/packing-units", ORDER_ID)
                        .header(IDEMPOTENCY_KEY, "idem-create-unit-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("createUnit: OUTBOUND_READ-only caller -> 403 (SecurityConfig POST gate)")
    @WithMockUser(roles = "OUTBOUND_READ")
    void createUnit_readOnlyCaller_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/orders/{orderId}/packing-units", ORDER_ID)
                        .header(IDEMPOTENCY_KEY, "idem-create-unit-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateUnitJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("createUnit: unauthenticated -> 401")
    void createUnit_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/orders/{orderId}/packing-units", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateUnitJson()))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  PATCH /api/v1/outbound/packing-units/{id} (sealUnit)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sealUnit: valid body + OUTBOUND_WRITE -> 200, status=SEALED")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void sealUnit_happyPath_returns200() throws Exception {
        PackingUnitResult found = new PackingUnitResult(
                PACKING_UNIT_ID, ORDER_ID, "CTN-001", "BOX",
                500, 30, 20, 10, "notes", "OPEN",
                List.of(new PackingUnitLineResult(PACKING_UNIT_LINE_ID, ORDER_LINE_ID, SKU_ID, null, 5)),
                "PACKING", 0L, T0, T0);
        when(queryPackingUnit.findById(PACKING_UNIT_ID)).thenReturn(Optional.of(found));

        PackingUnitResult sealed = new PackingUnitResult(
                PACKING_UNIT_ID, ORDER_ID, "CTN-001", "BOX",
                500, 30, 20, 10, "notes", "SEALED",
                List.of(new PackingUnitLineResult(PACKING_UNIT_LINE_ID, ORDER_LINE_ID, SKU_ID, null, 5)),
                "PACKING", 1L, T0, T0);
        when(sealPackingUnit.seal(any())).thenReturn(sealed);

        mockMvc.perform(patch("/api/v1/outbound/packing-units/{id}", PACKING_UNIT_ID)
                        .header(IDEMPOTENCY_KEY, "idem-seal-unit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": 0 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packingUnitId").value(PACKING_UNIT_ID.toString()))
                .andExpect(jsonPath("$.status").value("SEALED"))
                .andExpect(jsonPath("$.version").value(1));

        verify(sealPackingUnit).seal(any());
    }

    @Test
    @DisplayName("sealUnit: negative version violates @Min(0) -> 400 VALIDATION_ERROR")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void sealUnit_negativeVersion_returns400ValidationError() throws Exception {
        mockMvc.perform(patch("/api/v1/outbound/packing-units/{id}", PACKING_UNIT_ID)
                        .header(IDEMPOTENCY_KEY, "idem-seal-unit-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": -1 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("sealUnit: OUTBOUND_READ-only caller -> 403 (SecurityConfig PATCH gate)")
    @WithMockUser(roles = "OUTBOUND_READ")
    void sealUnit_readOnlyCaller_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/outbound/packing-units/{id}", PACKING_UNIT_ID)
                        .header(IDEMPOTENCY_KEY, "idem-seal-unit-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": 0 }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("sealUnit: unauthenticated -> 401")
    void sealUnit_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/outbound/packing-units/{id}", PACKING_UNIT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": 0 }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  POST /api/v1/outbound/orders/{orderId}/packing/confirm (confirmPacking)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("confirmPacking: valid body + OUTBOUND_WRITE -> 200, status=PACKED")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void confirmPacking_happyPath_returns200() throws Exception {
        OrderResult result = new OrderResult(
                ORDER_ID, "ORD-100", "MANUAL",
                UUID.randomUUID(), UUID.randomUUID(),
                null, null, "PACKED",
                1L, T0, "creator", T0, "creator",
                List.of(), UUID.randomUUID(), "PACKING_CONFIRMED");
        when(confirmPacking.confirm(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/outbound/orders/{orderId}/packing/confirm", ORDER_ID)
                        .header(IDEMPOTENCY_KEY, "idem-confirm-packing-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": 0 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("PACKED"));

        verify(confirmPacking).confirm(any());
    }

    @Test
    @DisplayName("confirmPacking: negative version violates @Min(0) -> 400 VALIDATION_ERROR")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void confirmPacking_negativeVersion_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/orders/{orderId}/packing/confirm", ORDER_ID)
                        .header(IDEMPOTENCY_KEY, "idem-confirm-packing-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": -1 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("confirmPacking: OUTBOUND_READ-only caller -> 403 (SecurityConfig POST gate)")
    @WithMockUser(roles = "OUTBOUND_READ")
    void confirmPacking_readOnlyCaller_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/orders/{orderId}/packing/confirm", ORDER_ID)
                        .header(IDEMPOTENCY_KEY, "idem-confirm-packing-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": 0 }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("confirmPacking: unauthenticated -> 401")
    void confirmPacking_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/orders/{orderId}/packing/confirm", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": 0 }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private String validCreateUnitJson() {
        return """
                {
                  "cartonNo": "CTN-001",
                  "packingType": "BOX",
                  "weightGrams": 500,
                  "lengthMm": 30,
                  "widthMm": 20,
                  "heightMm": 10,
                  "notes": "notes",
                  "lines": [
                    { "orderLineId": "%s", "skuId": "%s", "qty": 5 }
                  ]
                }
                """.formatted(ORDER_LINE_ID, SKU_ID);
    }
}
