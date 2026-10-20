package com.wms.master.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.common.page.PageResult;
import com.example.security.servlet.FirewallRejections;
import com.wms.master.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.master.application.port.in.WarehouseCrudUseCase;
import com.wms.master.application.port.in.WarehouseQueryUseCase;
import com.wms.master.application.query.ListWarehousesQuery;
import com.wms.master.config.SecurityConfig;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * TASK-MONO-801 — a path the servlet firewall rejects up front, through the real
 * {@code FilterChainProxy} built from master-service's {@link SecurityConfig} (shape B:
 * {@code anyRequest().authenticated()}, nested {@code {"error":{…}}} envelope).
 *
 * <p>Before the {@code RequestRejectedHandler} bean the default handler did {@code sendError(400)};
 * on Tomcat the {@code /error} dispatch met {@code authenticated()} anonymous → 401 (AC-0, measured).
 * MockMvc performs no error dispatch, so the red here is a 400 with an empty body.
 */
@WebMvcTest(controllers = WarehouseController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("wms master — TASK-MONO-801 up-front firewall rejection (real chain, nested envelope)")
class FirewallRejectionSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarehouseCrudUseCase crudUseCase;

    @MockitoBean
    private WarehouseQueryUseCase queryUseCase;

    @MockitoBean
    @SuppressWarnings("unused")
    private JwtDecoder jwtDecoder;

    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        root.detachAppender(logs);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/x;y",
            "/api/v1/master/warehouses;jsessionid=abc",
            "/api/v1/master//warehouses",
            "/api/v1/master/%25"})
    @DisplayName("🔴 AC-1 rejected path, no token → 400 BAD_REQUEST in the nested envelope, no echo, one WARN, no ERROR")
    void rejectedPath_anonymous_answers400(String rawPath) throws Exception {
        MvcResult result = mockMvc.perform(get(URI.create(rawPath)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value(FirewallRejections.MESSAGE))
                .andExpect(jsonPath("$.error.timestamp").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(rawPath);
        assertOneFirewallWarnAndNoError();
    }

    @Test
    @DisplayName("🔴 AC-1 rejected path with an authorised token → still 400")
    void rejectedPath_withToken_answers400() throws Exception {
        mockMvc.perform(get(URI.create("/api/v1/master/warehouses;x"))
                        .with(jwt().jwt(b -> b.claim("role", "MASTER_READ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        assertOneFirewallWarnAndNoError();
    }

    @Test
    @DisplayName("control — a normal path without a token is still 401 UNAUTHORIZED")
    void normalPath_anonymous_still401() throws Exception {
        mockMvc.perform(get("/api/v1/master/warehouses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("control — an authorised normal request is unchanged (200)")
    void normalPath_authorised_unchanged() throws Exception {
        when(queryUseCase.list(any(ListWarehousesQuery.class)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));
        mockMvc.perform(get("/api/v1/master/warehouses")
                        .with(jwt().jwt(b -> b.claim("role", "MASTER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("control — a real unexpected exception is still 500 INTERNAL_ERROR")
    void unexpectedException_still500() throws Exception {
        when(queryUseCase.list(any(ListWarehousesQuery.class)))
                .thenThrow(new IllegalStateException("boom"));
        mockMvc.perform(get("/api/v1/master/warehouses")
                        .with(jwt().jwt(b -> b.claim("role", "MASTER_READ"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    private void assertOneFirewallWarnAndNoError() {
        List<ILoggingEvent> firewall = logs.list.stream()
                .filter(e -> e.getLoggerName().equals(FirewallRejections.class.getName()))
                .toList();
        assertThat(firewall).hasSize(1);
        assertThat(firewall.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }
}
