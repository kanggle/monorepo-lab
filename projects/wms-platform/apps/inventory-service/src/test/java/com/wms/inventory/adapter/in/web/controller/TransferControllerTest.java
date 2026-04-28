package com.wms.inventory.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.inventory.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inventory.application.port.in.QueryTransferUseCase;
import com.wms.inventory.application.port.in.TransferStockUseCase;
import com.wms.inventory.application.result.TransferResult;
import com.wms.inventory.application.result.TransferView;
import com.wms.inventory.config.SecurityConfig;
import com.wms.inventory.domain.exception.InventoryValidationException;
import com.wms.inventory.domain.exception.TransferSameLocationException;
import com.wms.inventory.domain.model.TransferReasonCode;
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

@WebMvcTest(controllers = TransferController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TransferControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private TransferStockUseCase transferStock;
    @MockBean private QueryTransferUseCase queryTransfer;

    @Test
    void createTransferSucceeds() throws Exception {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID sku = UUID.randomUUID();

        when(transferStock.transfer(any())).thenReturn(sample(source, target, sku));

        String body = "{\"sourceLocationId\":\"" + source + "\","
                + "\"targetLocationId\":\"" + target + "\","
                + "\"skuId\":\"" + sku + "\","
                + "\"quantity\":10,"
                + "\"reasonCode\":\"TRANSFER_INTERNAL\","
                + "\"reasonNote\":\"rebalance\"}";

        mockMvc.perform(post("/api/v1/inventory/transfers")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.sourceInventory.availableQty").value(70))
                .andExpect(jsonPath("$.targetInventory.availableQty").value(10));
    }

    @Test
    void sameLocationReturns422() throws Exception {
        when(transferStock.transfer(any())).thenThrow(
                new TransferSameLocationException("source and target are equal"));

        UUID location = UUID.randomUUID();
        String body = "{\"sourceLocationId\":\"" + location + "\","
                + "\"targetLocationId\":\"" + location + "\","
                + "\"skuId\":\"" + UUID.randomUUID() + "\","
                + "\"quantity\":10,"
                + "\"reasonCode\":\"TRANSFER_INTERNAL\"}";

        mockMvc.perform(post("/api/v1/inventory/transfers")
                        .header("Idempotency-Key", "idem-2")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSFER_SAME_LOCATION"));
    }

    @Test
    void crossWarehouseReturns400() throws Exception {
        when(transferStock.transfer(any())).thenThrow(
                new InventoryValidationException("Cross-warehouse transfers are not supported in v1"));
        String body = "{\"sourceLocationId\":\"" + UUID.randomUUID() + "\","
                + "\"targetLocationId\":\"" + UUID.randomUUID() + "\","
                + "\"skuId\":\"" + UUID.randomUUID() + "\","
                + "\"quantity\":10,"
                + "\"reasonCode\":\"TRANSFER_INTERNAL\"}";

        mockMvc.perform(post("/api/v1/inventory/transfers")
                        .header("Idempotency-Key", "idem-3")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void missingIdempotencyKeyRejected() throws Exception {
        String body = "{\"sourceLocationId\":\"" + UUID.randomUUID() + "\","
                + "\"targetLocationId\":\"" + UUID.randomUUID() + "\","
                + "\"skuId\":\"" + UUID.randomUUID() + "\","
                + "\"quantity\":10,"
                + "\"reasonCode\":\"TRANSFER_INTERNAL\"}";

        mockMvc.perform(post("/api/v1/inventory/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static TransferResult sample(UUID source, UUID target, UUID sku) {
        UUID transferId = UUID.randomUUID();
        UUID sourceInvId = UUID.randomUUID();
        UUID targetInvId = UUID.randomUUID();
        TransferView view = new TransferView(
                transferId, UUID.randomUUID(),
                source, target, sku, null, 10,
                TransferReasonCode.TRANSFER_INTERNAL, "rebalance",
                "actor", Instant.parse("2026-04-25T10:00:00Z"));
        return new TransferResult(view,
                new TransferResult.Endpoint(sourceInvId, 70, 0, 0, 1L, false),
                new TransferResult.Endpoint(targetInvId, 10, 0, 0, 1L, true));
    }
}
