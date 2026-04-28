package com.wms.inventory.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.inventory.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inventory.application.port.in.AdjustStockUseCase;
import com.wms.inventory.application.port.in.QueryAdjustmentUseCase;
import com.wms.inventory.application.result.AdjustmentResult;
import com.wms.inventory.application.result.AdjustmentView;
import com.wms.inventory.config.SecurityConfig;
import com.wms.inventory.domain.exception.InsufficientStockException;
import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.ReasonCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdjustmentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdjustmentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AdjustStockUseCase adjustStock;
    @MockBean private QueryAdjustmentUseCase queryAdjustment;

    private static final UUID INV = UUID.randomUUID();
    private static final UUID ADJ = UUID.randomUUID();

    @Test
    void createAdjustmentSucceeds() throws Exception {
        when(adjustStock.adjust(any())).thenReturn(sample(Bucket.AVAILABLE, -5));

        String body = "{\"inventoryId\":\"" + INV + "\","
                + "\"bucket\":\"AVAILABLE\","
                + "\"delta\":-5,"
                + "\"reasonCode\":\"ADJUSTMENT_LOSS\","
                + "\"reasonNote\":\"lost in transit\"}";

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adjustmentId").value(ADJ.toString()))
                .andExpect(jsonPath("$.bucket").value("AVAILABLE"))
                .andExpect(jsonPath("$.delta").value(-5));
    }

    @Test
    void createAdjustmentMissingIdempotencyKeyRejected() throws Exception {
        String body = "{\"inventoryId\":\"" + INV + "\","
                + "\"bucket\":\"AVAILABLE\","
                + "\"delta\":-5,"
                + "\"reasonCode\":\"ADJUSTMENT_LOSS\","
                + "\"reasonNote\":\"lost in transit\"}";

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createAdjustmentMissingReasonNoteReturnsAdjustmentReasonRequired() throws Exception {
        String body = "{\"inventoryId\":\"" + INV + "\","
                + "\"bucket\":\"AVAILABLE\","
                + "\"delta\":-5,"
                + "\"reasonCode\":\"ADJUSTMENT_LOSS\","
                + "\"reasonNote\":\"  \"}";

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADJUSTMENT_REASON_REQUIRED"));
    }

    @Test
    void createAdjustmentReservedBucketWriteRoleForbidden() throws Exception {
        String body = "{\"inventoryId\":\"" + INV + "\","
                + "\"bucket\":\"RESERVED\","
                + "\"delta\":-5,"
                + "\"reasonCode\":\"ADJUSTMENT_LOSS\","
                + "\"reasonNote\":\"lost\"}";

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Idempotency-Key", "idem-2")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createAdjustmentReservedBucketAdminAllowed() throws Exception {
        when(adjustStock.adjust(any())).thenReturn(sample(Bucket.RESERVED, -5));
        String body = "{\"inventoryId\":\"" + INV + "\","
                + "\"bucket\":\"RESERVED\","
                + "\"delta\":-5,"
                + "\"reasonCode\":\"ADJUSTMENT_LOSS\","
                + "\"reasonNote\":\"manual reservation correction\"}";

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Idempotency-Key", "idem-3")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().jwt(j -> j.claim("role", "INVENTORY_ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_ADMIN"))))
                .andExpect(status().isCreated());
    }

    @Test
    void markDamagedSucceeds() throws Exception {
        when(adjustStock.adjust(any())).thenReturn(sample(Bucket.AVAILABLE, -3));
        String body = "{\"quantity\":3,\"reasonNote\":\"package torn\"}";

        mockMvc.perform(post("/api/v1/inventory/{id}/mark-damaged", INV)
                        .header("Idempotency-Key", "idem-4")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"))))
                .andExpect(status().isOk());
    }

    @Test
    void markDamagedInsufficientReturns422() throws Exception {
        when(adjustStock.adjust(any())).thenThrow(new InsufficientStockException(
                INV, Bucket.AVAILABLE, 1, 10));
        String body = "{\"quantity\":10,\"reasonNote\":\"too much\"}";

        mockMvc.perform(post("/api/v1/inventory/{id}/mark-damaged", INV)
                        .header("Idempotency-Key", "idem-5")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void writeOffDamagedRequiresAdmin() throws Exception {
        String body = "{\"quantity\":3,\"reasonNote\":\"complete write-off\"}";

        mockMvc.perform(post("/api/v1/inventory/{id}/write-off-damaged", INV)
                        .header("Idempotency-Key", "idem-6")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void writeOffDamagedAdminSucceeds() throws Exception {
        when(adjustStock.adjust(any())).thenReturn(sample(Bucket.DAMAGED, -3));
        String body = "{\"quantity\":3,\"reasonNote\":\"complete write-off\"}";

        mockMvc.perform(post("/api/v1/inventory/{id}/write-off-damaged", INV)
                        .header("Idempotency-Key", "idem-7")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_ADMIN"))))
                .andExpect(status().isOk());
    }

    private static AdjustmentResult sample(Bucket bucket, int delta) {
        AdjustmentView view = new AdjustmentView(
                ADJ, INV, bucket, delta,
                ReasonCode.ADJUSTMENT_LOSS, "test reason",
                "actor", Instant.parse("2026-04-25T10:00:00Z"));
        AdjustmentResult.InventorySnapshot snap = new AdjustmentResult.InventorySnapshot(
                INV, 95, 0, 0, 95, 1L);
        return new AdjustmentResult(view, snap);
    }
}
