package com.wms.master.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.page.PageResult;
import com.wms.master.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.master.application.port.in.WarehouseCrudUseCase;
import com.wms.master.application.port.in.WarehouseQueryUseCase;
import com.wms.master.application.query.ListWarehousesQuery;
import com.wms.master.application.result.WarehouseResult;
import com.wms.master.config.SecurityConfig;
import com.wms.master.domain.model.WarehouseStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WarehouseController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class WarehouseSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarehouseCrudUseCase crudUseCase;

    @MockitoBean
    private WarehouseQueryUseCase queryUseCase;

    @MockitoBean
    @SuppressWarnings("unused")
    private JwtDecoder jwtDecoder;

    @Test
    void noAuthHeader_returns401_withPlatformEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/master/warehouses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void noAuth_onMutatingEndpoint_returns401_BeforeIdempotencyCheck() throws Exception {
        mockMvc.perform(post("/api/v1/master/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"WH01\",\"name\":\"x\",\"timezone\":\"Asia/Seoul\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void authenticatedJwt_reachesController() throws Exception {
        when(queryUseCase.list(any(ListWarehousesQuery.class)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/v1/master/warehouses")
                        .with(jwt().jwt(b -> b.claim("role", "MASTER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void publicActuatorHealth_isAccessibleWithoutJwt() throws Exception {
        // /actuator/health is not served by @WebMvcTest, but the security rule
        // is still verified by the permitAll() matcher; a 404 (not 401) confirms
        // the request passed the auth filter and reached dispatch.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status != 401 : "actuator/health must bypass auth, got " + status;
                });
    }

    @Test
    void roleClaimArray_mapsAllEntriesToAuthorities() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryUseCase.findById(id)).thenReturn(new WarehouseResult(
                id, "WH01", "X", null, "Asia/Seoul", WarehouseStatus.ACTIVE,
                0L, Instant.now(), "a", Instant.now(), "a"));

        mockMvc.perform(get("/api/v1/master/warehouses/" + id)
                        .with(jwt().jwt(b -> b.claim("role",
                                List.of("MASTER_READ", "MASTER_WRITE")))))
                .andExpect(status().isOk());
    }
}
