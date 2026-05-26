package com.wms.admin.api.dashboard;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.admin.api.advice.GlobalExceptionHandler;
import com.wms.admin.api.dashboard.dto.ProjectionStatusResponse;
import com.wms.admin.application.projection.ProjectionStatusService;
import com.wms.admin.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OperationsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class OperationsControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ProjectionStatusService projectionStatusService;

    @Test
    void projectionStatus_admin_returns200() throws Exception {
        when(projectionStatusService.computeStatus()).thenReturn(
                new ProjectionStatusResponse(List.of(), 0.0d, 120, 3, 1, 0));

        mockMvc.perform(get("/api/v1/admin/operations/projection-status")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifetimeApplied").value(120))
                .andExpect(jsonPath("$.lifetimeIgnoredDuplicate").value(3))
                .andExpect(jsonPath("$.lifetimeIgnoredDuplicateLate").value(1));
    }

    @Test
    void projectionStatus_viewer_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/projection-status")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void projectionStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/projection-status"))
                .andExpect(status().isUnauthorized());
    }
}
