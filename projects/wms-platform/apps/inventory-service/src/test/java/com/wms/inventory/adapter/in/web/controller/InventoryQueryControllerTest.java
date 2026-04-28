package com.wms.inventory.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.inventory.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inventory.application.port.in.QueryInventoryUseCase;
import com.wms.inventory.application.result.InventoryView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InventoryQueryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class InventoryQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueryInventoryUseCase queryInventory;

    private static final UUID INV_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();
    private static final UUID SKU = UUID.randomUUID();

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/{id}", INV_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void readRoleCanListInventory() throws Exception {
        when(queryInventory.list(any())).thenReturn(PageView.of(
                List.of(sampleView(INV_ID, 80)), 0, 20, 1L, "updatedAt,desc"));

        mockMvc.perform(get("/api/v1/inventory")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(INV_ID.toString()))
                .andExpect(jsonPath("$.content[0].onHandQty").value(80))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.sort").value("updatedAt,desc"));
    }

    @Test
    void getByIdReturnsETag() throws Exception {
        when(queryInventory.findById(INV_ID))
                .thenReturn(Optional.of(sampleView(INV_ID, 50)));

        mockMvc.perform(get("/api/v1/inventory/{id}", INV_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_READ"))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.availableQty").value(50));
    }

    @Test
    void getByIdUnknownReturns404WithErrorEnvelope() throws Exception {
        when(queryInventory.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/inventory/{id}", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVENTORY_NOT_FOUND"));
    }

    @Test
    void getByKeyHitReturnsRow() throws Exception {
        when(queryInventory.findByKey(LOCATION, SKU, null))
                .thenReturn(Optional.of(sampleView(INV_ID, 30)));

        mockMvc.perform(get("/api/v1/inventory/by-key")
                        .param("locationId", LOCATION.toString())
                        .param("skuId", SKU.toString())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(INV_ID.toString()))
                .andExpect(jsonPath("$.availableQty").value(30));
    }

    @Test
    void getByKeyMissReturns404() throws Exception {
        when(queryInventory.findByKey(any(), any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/inventory/by-key")
                        .param("locationId", LOCATION.toString())
                        .param("skuId", SKU.toString())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVENTORY_NOT_FOUND"));
    }

    @Test
    void writeRoleAloneIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/inventory")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static InventoryView sampleView(UUID id, int available) {
        return new InventoryView(
                id, WAREHOUSE, LOCATION, "WH01-A-01-01-01",
                SKU, "SKU-X", null, null,
                available, 0, 0, available,
                Instant.parse("2026-04-25T10:00:00Z"), 3L,
                Instant.parse("2026-04-18T10:00:00Z"),
                Instant.parse("2026-04-25T10:00:00Z"));
    }
}
