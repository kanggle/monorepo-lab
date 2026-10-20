package com.example.product.presentation.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.product.TestProductServiceApplication;
import com.example.product.application.dto.SellerSummary;
import com.example.product.application.service.RegisterSellerService;
import com.example.product.application.service.SellerQueryService;
import com.example.product.domain.model.SellerStatus;
import com.example.product.infrastructure.config.ProductSecurityConfig;
import com.example.product.presentation.advice.GlobalExceptionHandler;
import com.example.security.servlet.FirewallRejections;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-801 — a path the servlet firewall rejects up front, through the real
 * {@code FilterChainProxy} built from {@link ProductSecurityConfig} (shape C: a permit-all default
 * chain + the {@code /internal/**} resource-server chain).
 *
 * <p>Before the {@code RequestRejectedHandler} bean the default handler did {@code sendError(400)}
 * and, on Tomcat, the permitted {@code /error} dispatch answered Boot's default body
 * {@code {timestamp,status,error,path}} — 400, but echoing the path and not the common envelope
 * (AC-0, measured). MockMvc performs no error dispatch, so the red here is a 400 with an empty body.
 */
@WebMvcTest(controllers = {InternalSellerController.class, AdminSellerController.class})
@ContextConfiguration(classes = TestProductServiceApplication.class)
@Import({GlobalExceptionHandler.class, ProductSecurityConfig.class})
@DisplayName("product-service — TASK-MONO-801 up-front firewall rejection (real chains)")
class FirewallRejectionSliceTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SellerQueryService sellerQueryService;

    @MockitoBean
    RegisterSellerService registerSellerService;

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
            "/api/admin/sellers/s-1;jsessionid=abc",
            "/api/admin//sellers/s-1",
            "/api/admin/sellers/%25",
            "/internal/sellers/s-1;x"})
    @DisplayName("🔴 AC-1 rejected path → 400 BAD_REQUEST common envelope (not Boot's body), no echo, one WARN, no ERROR")
    void rejectedPath_answers400(String rawPath) throws Exception {
        MvcResult result = mvc.perform(get(URI.create(rawPath)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(FirewallRejections.MESSAGE))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(rawPath);

        List<ILoggingEvent> firewall = logs.list.stream()
                .filter(e -> e.getLoggerName().equals(FirewallRejections.class.getName()))
                .toList();
        assertThat(firewall).hasSize(1);
        assertThat(firewall.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }

    @Test
    @DisplayName("control — /internal/** without a token is still 401 UNAUTHORIZED")
    void internalWithoutToken_still401() throws Exception {
        mvc.perform(get("/internal/sellers/s-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("control — an authorised header-trust request is unchanged (200)")
    void headerTrustRequest_unchanged() throws Exception {
        Instant t = Instant.parse("2026-10-20T00:00:00Z");
        given(sellerQueryService.getSeller("s-1"))
                .willReturn(new SellerSummary("s-1", "Seller s-1", SellerStatus.ACTIVE, t, t));
        mvc.perform(get("/api/admin/sellers/s-1")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_PRODUCT_READ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value("s-1"));
    }

    @Test
    @DisplayName("control — a real unexpected exception is still 500 INTERNAL_ERROR")
    void unexpectedException_still500() throws Exception {
        given(sellerQueryService.getSeller("s-1")).willThrow(new IllegalStateException("boom"));
        mvc.perform(get("/api/admin/sellers/s-1")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_PRODUCT_READ"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
