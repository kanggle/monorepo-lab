package com.example.security.service.query.internal;

import com.example.security.service.infrastructure.config.InternalAuthFilter;
import com.example.security.service.query.SecurityMetricsQueryService;
import com.example.security.service.query.dto.SecurityMetricsDailyView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /internal/security/metrics/daily} slice (TASK-MONO-811). The {@code test} profile
 * bypasses {@link InternalAuthFilter} like {@link LoginHistoryQueryControllerSliceTest}; the auth
 * behaviour on this path is {@code SecurityMetricsInternalAuthSliceTest}.
 */
@WebMvcTest(controllers = SecurityMetricsQueryController.class)
@Import({QueryExceptionHandler.class, InternalAuthFilter.class})
@ActiveProfiles("test")
class SecurityMetricsQueryControllerSliceTest {

    private static final Instant FROM = Instant.parse("2026-10-18T15:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-23T05:05:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean SecurityMetricsQueryService metricsQueryService;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    @DisplayName("200 — counts per KST date; 🔴 the body holds exactly tenantId/from/to/days[date,loginSuccess,loginFailure,accountLocked]")
    void ok_fieldCensus() throws Exception {
        when(metricsQueryService.daily(eq("fan-platform"), eq(FROM), eq(TO))).thenReturn(
                new SecurityMetricsDailyView("fan-platform", FROM.toString(), TO.toString(), List.of(
                        new SecurityMetricsDailyView.Day("2026-10-19", 3, 1, 0))));

        String body = mockMvc.perform(get("/internal/security/metrics/daily")
                        .param("tenantId", "fan-platform")
                        .param("from", "2026-10-18T15:00:00Z")
                        .param("to", "2026-10-23T05:05:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].date").value("2026-10-19"))
                .andExpect(jsonPath("$.days[0].loginSuccess").value(3))
                .andExpect(jsonPath("$.days[0].loginFailure").value(1))
                .andExpect(jsonPath("$.days[0].accountLocked").value(0))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        assertThat(fieldNames(root)).containsExactly("tenantId", "from", "to", "days");
        assertThat(fieldNames(root.get("days").get(0)))
                .containsExactly("date", "loginSuccess", "loginFailure", "accountLocked");
        assertThat(body).doesNotContain("accountId", "ip", "email", "fingerprint", "eventId");
    }

    @Test
    @DisplayName("an offset instant (+09:00) is accepted and reaches the service as the same instant")
    void offsetInstantAccepted() throws Exception {
        when(metricsQueryService.daily(eq("*"), eq(FROM), eq(TO))).thenReturn(
                new SecurityMetricsDailyView("*", FROM.toString(), TO.toString(), List.of()));

        mockMvc.perform(get("/internal/security/metrics/daily")
                        .param("tenantId", "*")
                        .param("from", "2026-10-19T00:00:00+09:00")
                        .param("to", "2026-10-23T14:05:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").isEmpty());
    }

    @Test
    @DisplayName("missing tenantId → 400 VALIDATION_ERROR, service not called")
    void missingTenant400() throws Exception {
        mockMvc.perform(get("/internal/security/metrics/daily")
                        .param("from", "2026-10-18T15:00:00Z")
                        .param("to", "2026-10-23T05:05:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(metricsQueryService);
    }

    @Test
    @DisplayName("from not an instant → 400 VALIDATION_ERROR, service not called")
    void badInstant400() throws Exception {
        mockMvc.perform(get("/internal/security/metrics/daily")
                        .param("tenantId", "fan-platform")
                        .param("from", "yesterday")
                        .param("to", "2026-10-23T05:05:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(metricsQueryService);
    }

    @Test
    @DisplayName("the service's range guard (IllegalArgumentException) → 400 VALIDATION_ERROR")
    void rangeGuard400() throws Exception {
        when(metricsQueryService.daily(eq("fan-platform"), eq(TO), eq(FROM)))
                .thenThrow(new IllegalArgumentException("to must not be before from"));

        mockMvc.perform(get("/internal/security/metrics/daily")
                        .param("tenantId", "fan-platform")
                        .param("from", "2026-10-23T05:05:00Z")
                        .param("to", "2026-10-18T15:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> it = node.fieldNames();
        it.forEachRemaining(names::add);
        return names;
    }
}
