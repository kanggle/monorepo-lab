package com.wms.outbound.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.outbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.outbound.application.port.in.RetryTmsNotificationUseCase;
import com.wms.outbound.application.result.RetryTmsNotificationResult;
import com.wms.outbound.config.SecurityConfig;
import java.time.Instant;
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
 * {@code @WebMvcTest} slice for {@link ShipmentController#retryTmsNotify}
 * (TASK-BE-526): {@code POST /api/v1/outbound/shipments/{id}:retry-tms-notify}.
 *
 * <p>This endpoint has no {@code @RequestBody} / {@code @Valid} DTO — the
 * only request-level constraint the controller enforces is the mandatory
 * {@code Idempotency-Key} header (via {@code RequestContext.requireIdempotencyKey},
 * thrown as {@link IllegalArgumentException} and mapped by
 * {@link GlobalExceptionHandler#handleBadInput} to the same 400
 * {@code VALIDATION_ERROR} envelope a {@code @Valid} failure would produce).
 * That header check stands in for the AC-2 "validation negative" scenario
 * here, since there is no DTO to violate (per the task's out-of-scope note:
 * the service-level ADMIN-only narrowing is mocked and not slice-testable).
 */
@WebMvcTest(controllers = ShipmentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class ShipmentControllerTest {

    private static final UUID SHIPMENT_ID = UUID.fromString("11111111-0000-7000-8000-000000000001");

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RetryTmsNotificationUseCase retryTmsNotification;

    // ------------------------------------------------------------------
    //  POST /api/v1/outbound/shipments/{id}:retry-tms-notify (retryTmsNotify)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("retryTmsNotify: OUTBOUND_WRITE + Idempotency-Key -> 200, body carries shipmentId/tmsStatus")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void retryTmsNotify_happyPath_returns200() throws Exception {
        RetryTmsNotificationResult result = new RetryTmsNotificationResult(
                SHIPMENT_ID, "NOTIFIED", T0, "1Z999", "COMPLETED", T0, "retrier-1");
        when(retryTmsNotification.retry(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/outbound/shipments/{id}:retry-tms-notify", SHIPMENT_ID)
                        .header(IDEMPOTENCY_KEY, "idem-retry-tms-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(SHIPMENT_ID.toString()))
                .andExpect(jsonPath("$.tmsStatus").value("NOTIFIED"))
                .andExpect(jsonPath("$.sagaState").value("COMPLETED"));

        verify(retryTmsNotification).retry(any());
    }

    @Test
    @DisplayName("retryTmsNotify: missing Idempotency-Key -> 400 VALIDATION_ERROR envelope")
    @WithMockUser(roles = "OUTBOUND_WRITE")
    void retryTmsNotify_missingIdempotencyKey_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/shipments/{id}:retry-tms-notify", SHIPMENT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("retryTmsNotify: OUTBOUND_READ-only caller -> 403 (SecurityConfig POST gate)")
    @WithMockUser(roles = "OUTBOUND_READ")
    void retryTmsNotify_readOnlyCaller_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/shipments/{id}:retry-tms-notify", SHIPMENT_ID)
                        .header(IDEMPOTENCY_KEY, "idem-retry-tms-2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("retryTmsNotify: unauthenticated -> 401")
    void retryTmsNotify_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outbound/shipments/{id}:retry-tms-notify", SHIPMENT_ID))
                .andExpect(status().isUnauthorized());
    }
}
