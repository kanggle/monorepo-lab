package com.wms.admin.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.admin.api.advice.GlobalExceptionHandler;
import com.wms.admin.config.SecurityConfig;
import com.wms.admin.readmodel.inventory.AdjustmentAuditEntity;
import com.wms.admin.readmodel.inventory.AdjustmentAuditRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdjustmentAuditController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdjustmentAuditControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AdjustmentAuditRepository repository;

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    private AdjustmentAuditEntity sample() {
        return new AdjustmentAuditEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, UUID.randomUUID(), "AVAILABLE", -5, "LOSS", "lost", "actor", NOW, NOW);
    }

    @Test
    void list_viewer_returns200() throws Exception {
        Page<AdjustmentAuditEntity> page = new PageImpl<>(List.of(sample()),
                PageRequest.of(0, 20), 1);
        when(repository.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/dashboard/adjustments")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reasonCode").value("LOSS"));
    }

    @Test
    void list_dateRangeFilter_passedToRepository() throws Exception {
        Page<AdjustmentAuditEntity> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(repository.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        Instant from = Instant.parse("2026-05-09T00:00:00Z");
        Instant to = Instant.parse("2026-05-09T23:59:59Z");

        mockMvc.perform(get("/api/v1/admin/dashboard/adjustments")
                        .param("occurredAtFrom", from.toString())
                        .param("occurredAtTo", to.toString())
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk());

        verify(repository).search(any(), any(), any(), any(), any(), eq(from), eq(to), any());
    }

    @Test
    void list_dateRangeInverted_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/adjustments")
                        .param("occurredAtFrom", "2026-05-09T23:59:59Z")
                        .param("occurredAtTo", "2026-05-09T00:00:00Z")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
