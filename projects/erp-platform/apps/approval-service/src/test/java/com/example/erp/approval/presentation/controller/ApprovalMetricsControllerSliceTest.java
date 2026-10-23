package com.example.erp.approval.presentation.controller;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.metrics.PerformanceMetrics.Metric;
import com.example.common.time.KstPeriodBounds;
import com.example.common.time.MetricsPeriod;
import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.application.ApprovalMetricsService;
import com.example.erp.approval.domain.error.ApprovalErrors.PermissionDeniedException;
import com.example.erp.approval.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link ApprovalMetricsController} (TASK-MONO-809): the shared
 * {@code PerformanceMetrics} shape (bare, no envelope), and {@code ?period=} missing / unknown →
 * {@code 400 VALIDATION_ERROR} through the real {@link GlobalExceptionHandler}.
 */
@WebMvcTest(ApprovalMetricsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ApprovalMetricsControllerSliceTest {

    private static final ActorContext ACTOR = new ActorContext("acct-1", "erp",
            Set.of("erp.read"), Set.of("*"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ApprovalMetricsService service;

    @BeforeEach
    void setUp() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(ACTOR, "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static PerformanceMetrics sample(MetricsPeriod period) {
        KstPeriodBounds bounds = KstPeriodBounds.from(
                Clock.fixed(Instant.parse("2026-10-23T05:05:00Z"), ZoneOffset.UTC));
        return PerformanceMetrics.of(bounds, period, List.of(
                Metric.count("submitted", 12, 9),
                Metric.count("approved", 7, 8),
                Metric.count("rejected", 1, 2)),
                Map.of(java.time.LocalDate.parse("2026-10-20"), Map.of("submitted", 3L)));
    }

    @Test
    @DisplayName("GET /metrics?period=week → 200, the shared shape with the three count metrics")
    void metrics200() throws Exception {
        when(service.getMetrics(any(ActorContext.class), eq(MetricsPeriod.WEEK)))
                .thenReturn(sample(MetricsPeriod.WEEK));

        mockMvc.perform(get("/api/erp/approval/metrics").param("period", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("week"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.current.from").value("2026-10-19T00:00:00+09:00"))
                .andExpect(jsonPath("$.metrics[0].key").value("submitted"))
                .andExpect(jsonPath("$.metrics[0].unit").value("count"))
                .andExpect(jsonPath("$.metrics[0].currency").doesNotExist())
                .andExpect(jsonPath("$.metrics[0].current").value("12"))
                .andExpect(jsonPath("$.metrics[0].previous").value("9"))
                .andExpect(jsonPath("$.metrics[1].key").value("approved"))
                .andExpect(jsonPath("$.metrics[2].key").value("rejected"))
                .andExpect(jsonPath("$.series[1].date").value("2026-10-20"))
                .andExpect(jsonPath("$.series[1].values.submitted").value("3"))
                .andExpect(jsonPath("$.series[1].values.approved").value("0"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(service).getMetrics(any(ActorContext.class), eq(MetricsPeriod.WEEK));
    }

    @Test
    @DisplayName("period is case-insensitive: MONTH → 200")
    void periodCaseInsensitive() throws Exception {
        when(service.getMetrics(any(ActorContext.class), eq(MetricsPeriod.MONTH)))
                .thenReturn(sample(MetricsPeriod.MONTH));

        mockMvc.perform(get("/api/erp/approval/metrics").param("period", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("month"));
    }

    @Test
    @DisplayName("period missing → 400 VALIDATION_ERROR, service never called")
    void periodMissing400() throws Exception {
        mockMvc.perform(get("/api/erp/approval/metrics"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("period unknown → 400 VALIDATION_ERROR")
    void periodUnknown400() throws Exception {
        mockMvc.perform(get("/api/erp/approval/metrics").param("period", "today"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("read gate refused → 403 PERMISSION_DENIED")
    void permissionDenied403() throws Exception {
        when(service.getMetrics(any(ActorContext.class), eq(MetricsPeriod.WEEK)))
                .thenThrow(new PermissionDeniedException("no read role"));

        mockMvc.perform(get("/api/erp/approval/metrics").param("period", "week"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
