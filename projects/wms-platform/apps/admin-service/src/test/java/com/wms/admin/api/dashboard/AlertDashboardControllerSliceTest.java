package com.wms.admin.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.admin.api.advice.GlobalExceptionHandler;
import com.wms.admin.application.alert.AlertAcknowledgeService;
import com.wms.admin.config.SecurityConfig;
import com.wms.admin.domain.error.AlertNotFoundException;
import com.wms.admin.domain.error.StateTransitionInvalidException;
import com.wms.admin.readmodel.alert.AlertLogEntity;
import com.wms.admin.readmodel.alert.AlertLogRepository;
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

@WebMvcTest(controllers = AlertDashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AlertDashboardControllerSliceTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AlertLogRepository alertRepo;
    @MockitoBean AlertAcknowledgeService acknowledgeService;

    private static final UUID ALERT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant DETECTED_AT = Instant.parse("2026-05-09T10:00:00Z");

    private AlertLogEntity sampleAlert() {
        return new AlertLogEntity(ALERT_ID, "LOW_STOCK", null, null, null, null,
                10, 5, DETECTED_AT, DETECTED_AT);
    }

    @Test
    void list_viewer_returns200() throws Exception {
        Page<AlertLogEntity> page = new PageImpl<>(List.of(sampleAlert()),
                PageRequest.of(0, 20), 1);
        when(alertRepo.search(any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/dashboard/alerts")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ALERT_ID.toString()));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_dateRangeFilter_passedToRepository() throws Exception {
        Page<AlertLogEntity> page = new PageImpl<>(java.util.List.of(),
                PageRequest.of(0, 20), 0);
        when(alertRepo.search(any(), any(), any(), any(), any(), any())).thenReturn(page);

        Instant from = Instant.parse("2026-05-09T00:00:00Z");
        Instant to = Instant.parse("2026-05-09T23:59:59Z");

        mockMvc.perform(get("/api/v1/admin/dashboard/alerts")
                        .param("detectedAtFrom", from.toString())
                        .param("detectedAtTo", to.toString())
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(alertRepo).search(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(from),
                org.mockito.ArgumentMatchers.eq(to), any());
    }

    @Test
    void list_dateRangeInverted_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/alerts")
                        .param("detectedAtFrom", "2026-05-09T23:59:59Z")
                        .param("detectedAtTo", "2026-05-09T00:00:00Z")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void acknowledge_operator_returns200() throws Exception {
        when(acknowledgeService.acknowledge(any(UUID.class), any())).thenReturn(sampleAlert());

        mockMvc.perform(post("/api/v1/admin/dashboard/alerts/" + ALERT_ID + "/acknowledge")
                        .header("X-Actor-Id", "ops-1")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ALERT_ID.toString()));
    }

    @Test
    void acknowledge_viewer_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/dashboard/alerts/" + ALERT_ID + "/acknowledge")
                        .header("X-Actor-Id", "ops-1")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void acknowledge_alreadyAcknowledged_returns422() throws Exception {
        doThrow(new StateTransitionInvalidException("alert already acknowledged"))
                .when(acknowledgeService).acknowledge(any(UUID.class), any());

        mockMvc.perform(post("/api/v1/admin/dashboard/alerts/" + ALERT_ID + "/acknowledge")
                        .header("X-Actor-Id", "ops-1")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"));
    }

    @Test
    void acknowledge_notFound_returns404() throws Exception {
        doThrow(new AlertNotFoundException(ALERT_ID))
                .when(acknowledgeService).acknowledge(any(UUID.class), any());

        mockMvc.perform(post("/api/v1/admin/dashboard/alerts/" + ALERT_ID + "/acknowledge")
                        .header("X-Actor-Id", "ops-1")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ALERT_NOT_FOUND"));
    }
}
