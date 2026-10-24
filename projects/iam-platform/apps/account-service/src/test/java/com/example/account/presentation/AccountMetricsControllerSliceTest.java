package com.example.account.presentation;

import com.example.account.application.result.AccountMetricsDailyResult;
import com.example.account.application.service.AccountMetricsQueryService;
import com.example.account.application.service.AccountSearchQueryService;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.AccountMetricsController;
import com.example.account.presentation.internal.AccountSearchController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /internal/account-metrics/daily} slice (TASK-MONO-811), under the dev/test bypass like
 * {@link AccountSearchControllerSliceTest}. The auth gate on this path is
 * {@code AccountMetricsInternalAuthSliceTest}. {@link AccountSearchController} is loaded too, so the
 * metrics path is proven not to collide with {@code /internal/accounts/{accountId}}.
 */
@WebMvcTest({AccountMetricsController.class, AccountSearchController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("AccountMetricsController slice tests (TASK-MONO-811)")
class AccountMetricsControllerSliceTest {

    private static final Instant FROM = Instant.parse("2026-09-30T15:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-23T05:05:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AccountMetricsQueryService metricsQueryService;
    @MockitoBean AccountSearchQueryService accountSearchQueryService;

    @Test
    @DisplayName("200 — per-KST-date counts; 🔴 field census: tenantId/from/to/days[date,newAccount] and nothing else")
    void ok_fieldCensus() throws Exception {
        given(metricsQueryService.daily(eq("fan-platform"), eq(FROM), eq(TO))).willReturn(
                new AccountMetricsDailyResult(List.of(new AccountMetricsDailyResult.Day(LocalDate.parse("2026-10-02"), 4))));

        String body = mockMvc.perform(get("/internal/account-metrics/daily")
                        .param("tenantId", "fan-platform")
                        .param("from", "2026-09-30T15:00:00Z")
                        .param("to", "2026-10-23T05:05:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("fan-platform"))
                .andExpect(jsonPath("$.from").value("2026-09-30T15:00:00Z"))
                .andExpect(jsonPath("$.days[0].date").value("2026-10-02"))
                .andExpect(jsonPath("$.days[0].newAccount").value(4))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        assertThat(names(root)).containsExactly("tenantId", "from", "to", "days");
        assertThat(names(root.get("days").get(0))).containsExactly("date", "newAccount");
        assertThat(body).doesNotContain("email", "accountId", "\"id\"", "status");
        verifyNoInteractions(accountSearchQueryService);
    }

    @Test
    @DisplayName("an offset instant (+09:00) is accepted as the same instant")
    void offsetInstant() throws Exception {
        given(metricsQueryService.daily(eq("*"), eq(FROM), eq(TO)))
                .willReturn(new AccountMetricsDailyResult(List.of()));

        mockMvc.perform(get("/internal/account-metrics/daily")
                        .param("tenantId", "*")
                        .param("from", "2026-10-01T00:00:00+09:00")
                        .param("to", "2026-10-23T14:05:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").isEmpty());
    }

    @Test
    @DisplayName("from not an instant → 400 VALIDATION_ERROR (not 500), service not called")
    void badInstant400() throws Exception {
        mockMvc.perform(get("/internal/account-metrics/daily")
                        .param("tenantId", "fan-platform")
                        .param("from", "last-week")
                        .param("to", "2026-10-23T05:05:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(metricsQueryService);
    }

    @Test
    @DisplayName("tenantId missing → 400 VALIDATION_ERROR")
    void missingTenant400() throws Exception {
        mockMvc.perform(get("/internal/account-metrics/daily")
                        .param("from", "2026-09-30T15:00:00Z")
                        .param("to", "2026-10-23T05:05:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(metricsQueryService);
    }

    @Test
    @DisplayName("the service's range guard → 400 VALIDATION_ERROR")
    void rangeGuard400() throws Exception {
        given(metricsQueryService.daily(eq("fan-platform"), eq(TO), eq(FROM)))
                .willThrow(new IllegalArgumentException("to must not be before from"));

        mockMvc.perform(get("/internal/account-metrics/daily")
                        .param("tenantId", "fan-platform")
                        .param("from", "2026-10-23T05:05:00Z")
                        .param("to", "2026-09-30T15:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
