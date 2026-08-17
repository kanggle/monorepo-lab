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
class OperationsControllerSliceTest {

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

    /**
     * TASK-MONO-547. The demo operator's token is neither of the two cases above: the
     * role hierarchy is {@code SUPERADMIN > ADMIN > OPERATOR > VIEWER}, so an operator
     * sits BETWEEN them — it inherits VIEWER (which is why every other wms dashboard
     * opens for the demo account) and still lacks ADMIN. Nothing asserted that, and it
     * is the exact fact the interview walkthrough's § 6 row describes.
     *
     * <p>The gate is not an oversight: {@code admin-service-api.md § 6.2} specifies
     * {@code Auth: WMS_ADMIN or higher}, and {@code OperatorRoleDerivation} withholds
     * the ADMIN tier by decision (TASK-BE-433, reaffirmed by TASK-MONO-514). This test
     * exists so that decision cannot be reversed silently by a hierarchy or derivation
     * edit while the documentation still says the screen is closed.
     *
     * <p>🔴 The authority list below is a COPY of {@code OperatorRoleDerivation
     * .WMS_OPERATOR_ROLES}, which lives in another project's package-private class and
     * cannot be imported here. So this test and {@code OperatorRoleDerivationTest}
     * each verify their own half — "the derivation produces this set" and "this set is
     * refused" — and nothing measures the join. A live call with a real assume-tenant
     * token is what would; see the task's AC-1 note on what was and was not run.
     */
    @Test
    void projectionStatus_demoOperatorTier_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/projection-status")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_WMS_OPERATOR"),
                                        new SimpleGrantedAuthority("ROLE_OUTBOUND_READ"),
                                        new SimpleGrantedAuthority("ROLE_OUTBOUND_WRITE"),
                                        new SimpleGrantedAuthority("ROLE_INBOUND_READ"),
                                        new SimpleGrantedAuthority("ROLE_INBOUND_WRITE"),
                                        new SimpleGrantedAuthority("ROLE_INVENTORY_READ"),
                                        new SimpleGrantedAuthority("ROLE_INVENTORY_WRITE"),
                                        new SimpleGrantedAuthority("ROLE_MASTER_READ"))))
                .andExpect(status().isForbidden());
    }
}
