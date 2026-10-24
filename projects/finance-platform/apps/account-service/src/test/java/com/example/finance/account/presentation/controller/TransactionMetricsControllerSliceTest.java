package com.example.finance.account.presentation.controller;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.KstPeriodBounds;
import com.example.common.time.MetricsPeriod;
import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.ActorContext;
import com.example.finance.account.application.TransactionMetricsService;
import com.example.finance.account.presentation.advice.GlobalExceptionHandler;
import com.example.finance.account.presentation.support.IdempotentExecution;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link TransactionMetricsController} (TASK-MONO-812) — the
 * routing against {@code AccountController}'s {@code /accounts/{id}} (both loaded), the bare
 * shared shape on the wire (no {@code {data, meta}} envelope), the 400 for a missing / unknown
 * period, and the AC-3 field census of the response. Authorization is pinned through the real
 * chain in {@code AccountBusinessRoleGateSliceTest}.
 */
@WebMvcTest({TransactionMetricsController.class, AccountController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TransactionMetricsControllerSliceTest {

    private static final ActorContext READER =
            new ActorContext("op-1", "finance", Set.of("FINANCE_OPERATOR", "FINANCE_ACCOUNT_READ"));

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    TransactionMetricsService metricsService;
    @MockitoBean
    AccountApplicationService accountService;
    @MockitoBean
    IdempotentExecution idempotency;

    @BeforeEach
    void setUp() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(READER, "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static PerformanceMetrics sample(MetricsPeriod period) {
        KstPeriodBounds bounds = KstPeriodBounds.from(
                Clock.fixed(Instant.parse("2026-10-23T05:05:00Z"), ZoneOffset.UTC));
        List<PerformanceMetrics.Metric> metrics = List.of(
                PerformanceMetrics.Metric.count("topupCount", 2, 1),
                PerformanceMetrics.Metric.money("topupAmount:KRW", "KRW", 150_000, 70_000),
                PerformanceMetrics.Metric.money("topupAmount:USD", "USD", 2_550, 0),
                PerformanceMetrics.Metric.count("transferCount", 0, 0),
                PerformanceMetrics.Metric.money("transferAmount:KRW", "KRW", 0, 0),
                PerformanceMetrics.Metric.money("transferAmount:USD", "USD", 0, 0),
                PerformanceMetrics.Metric.count("captureCount", 1, 0),
                PerformanceMetrics.Metric.money("captureAmount:KRW", "KRW", 30_000, 0),
                PerformanceMetrics.Metric.money("captureAmount:USD", "USD", 0, 0));
        Map<LocalDate, Map<String, Long>> daily = Map.of(
                LocalDate.parse("2026-10-19"), Map.of("topupCount", 2L, "topupAmount:KRW", 150_000L,
                        "topupAmount:USD", 2_550L));
        return PerformanceMetrics.of(bounds, period, metrics, daily);
    }

    @Test
    @DisplayName("GET /accounts/metrics?period=week → 200, routed here (not /accounts/{id}), bare shared shape")
    void weekBareShape() throws Exception {
        when(metricsService.getMetrics(any(), eq(MetricsPeriod.WEEK))).thenReturn(sample(MetricsPeriod.WEEK));

        mockMvc.perform(get("/api/finance/accounts/metrics").param("period", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.period").value("week"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.current.from").value("2026-10-19T00:00:00+09:00"))
                .andExpect(jsonPath("$.metrics[0].key").value("topupCount"))
                .andExpect(jsonPath("$.metrics[0].current").value("2"))
                .andExpect(jsonPath("$.metrics[1].key").value("topupAmount:KRW"))
                .andExpect(jsonPath("$.metrics[1].unit").value("money"))
                .andExpect(jsonPath("$.metrics[1].currency").value("KRW"))
                .andExpect(jsonPath("$.metrics[1].current").value("150000"))
                .andExpect(jsonPath("$.metrics[2].currency").value("USD"))
                .andExpect(jsonPath("$.metrics[2].current").value("2550"))
                .andExpect(jsonPath("$.series[0].date").value("2026-10-19"))
                .andExpect(jsonPath("$.series[0].values['topupAmount:USD']").value("2550"));
        verify(metricsService).getMetrics(READER, MetricsPeriod.WEEK);
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("period is case-insensitive (MONTH → month)")
    void monthCaseInsensitive() throws Exception {
        when(metricsService.getMetrics(any(), eq(MetricsPeriod.MONTH))).thenReturn(sample(MetricsPeriod.MONTH));

        mockMvc.perform(get("/api/finance/accounts/metrics").param("period", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("month"));
    }

    @Test
    @DisplayName("missing period → 400 VALIDATION_ERROR (not this service's 422 for a bare IAE); no query")
    void missingPeriod400() throws Exception {
        mockMvc.perform(get("/api/finance/accounts/metrics"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("unknown period (today / year) → 400 VALIDATION_ERROR")
    void unknownPeriod400() throws Exception {
        for (String p : List.of("today", "year", "")) {
            mockMvc.perform(get("/api/finance/accounts/metrics").param("period", p))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("🔴 TASK-MONO-817: wildcard tenant (tenant_id='*') → 403 PERMISSION_DENIED, never zeros; no query")
    void wildcardTenant403() throws Exception {
        ActorContext wildcard = new ActorContext("super-1", "*", Set.of("FINANCE_SUPERADMIN_READ"));
        TestingAuthenticationToken auth = new TestingAuthenticationToken(wildcard, "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/api/finance/accounts/metrics").param("period", "week"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("🔴 AC-3 census: every field name in the body is one of the shared shape's — no account / holder / owner field")
    void fieldCensus() throws Exception {
        when(metricsService.getMetrics(any(), eq(MetricsPeriod.WEEK))).thenReturn(sample(MetricsPeriod.WEEK));

        String body = mockMvc.perform(get("/api/finance/accounts/metrics").param("period", "week"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);

        assertThat(fieldNames(root)).containsExactlyInAnyOrder(
                "period", "timezone", "current", "previous", "metrics", "series");
        assertThat(fieldNames(root.get("current"))).containsExactlyInAnyOrder("from", "to");
        for (JsonNode m : root.get("metrics")) {
            assertThat(fieldNames(m)).isSubsetOf("key", "unit", "currency", "current", "previous");
        }
        for (JsonNode p : root.get("series")) {
            assertThat(fieldNames(p)).containsExactlyInAnyOrder("date", "values");
        }
        assertThat(body).doesNotContainIgnoringCase("accountId")
                .doesNotContainIgnoringCase("ownerRef")
                .doesNotContainIgnoringCase("counterparty")
                .doesNotContainIgnoringCase("holdId");
    }

    @Test
    @DisplayName("/accounts/{id} still routes to AccountController (the literal does not swallow ids)")
    void accountByIdStillRoutes() throws Exception {
        mockMvc.perform(get("/api/finance/accounts/acc-1"));
        verify(accountService).getAccount(eq("acc-1"), any());
        verifyNoInteractions(metricsService);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }
}
