package com.wms.inventory.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.inventory.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inventory.application.port.in.MovementQueryUseCase;
import com.wms.inventory.application.result.MovementView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.config.SecurityConfig;
import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.MovementType;
import com.wms.inventory.domain.model.ReasonCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MovementQueryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MovementQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MovementQueryUseCase movementQuery;

    private static final UUID INV_ID = UUID.randomUUID();

    @Test
    void perInventoryListReturnsResult() throws Exception {
        MovementView v = new MovementView(
                UUID.randomUUID(), INV_ID, MovementType.RECEIVE, Bucket.AVAILABLE,
                50, 0, 50, ReasonCode.PUTAWAY, null,
                null, null, null, UUID.randomUUID(),
                "system:putaway-consumer",
                Instant.parse("2026-04-25T10:00:00Z"));
        when(movementQuery.list(any())).thenReturn(
                PageView.of(List.of(v), 0, 20, 1L, "occurredAt,desc"));

        mockMvc.perform(get("/api/v1/inventory/{id}/movements", INV_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].movementType").value("RECEIVE"))
                .andExpect(jsonPath("$.content[0].delta").value(50));
    }

    @Test
    void crossRowQueryRequiresOccurredAfterWhenInventoryIdAbsent() throws Exception {
        // Without inventoryId or occurredAfter, MovementListCriteria's
        // compact constructor throws InventoryValidationException, which the
        // global handler maps to 400 VALIDATION_ERROR.
        mockMvc.perform(get("/api/v1/inventory/movements")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_READ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void crossRowQueryAcceptsOccurredAfterAlone() throws Exception {
        when(movementQuery.list(any())).thenReturn(
                PageView.of(List.of(), 0, 20, 0L, "occurredAt,desc"));

        mockMvc.perform(get("/api/v1/inventory/movements")
                        .param("occurredAfter", "2026-04-20T00:00:00Z")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/{id}/movements", INV_ID))
                .andExpect(status().isUnauthorized());
    }
}
