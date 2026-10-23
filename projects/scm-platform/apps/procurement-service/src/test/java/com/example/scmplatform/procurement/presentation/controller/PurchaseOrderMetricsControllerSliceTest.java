package com.example.scmplatform.procurement.presentation.controller;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.metrics.PerformanceMetrics.Metric;
import com.example.common.page.PageResult;
import com.example.common.time.KstPeriodBounds;
import com.example.common.time.MetricsPeriod;
import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.IdempotencyExecutor;
import com.example.scmplatform.procurement.application.IdempotencyHasher;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderMetricsService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.presentation.advice.GlobalExceptionHandler;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link PurchaseOrderMetricsController} and the TASK-MONO-810
 * {@code GET /po} submit-date filter. Loads {@link PurchaseOrderController} too, so the
 * literal {@code /po/metrics} is proven to win over {@code /po/{poId}}; the real
 * {@link GlobalExceptionHandler} answers the 400s. Auth/role gate: see
 * {@code ProcurementBusinessRoleGateSliceTest}.
 */
@WebMvcTest({PurchaseOrderMetricsController.class, PurchaseOrderController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PurchaseOrderMetricsControllerSliceTest {

    private static final ActorContext ACTOR =
            new ActorContext("acct-1", "scm", Set.of("SCM_PROCUREMENT_READ"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PurchaseOrderMetricsService metricsService;

    @MockitoBean
    PurchaseOrderApplicationService service;

    @MockitoBean
    IdempotencyExecutor idempotency;

    @MockitoBean
    IdempotencyHasher hasher;

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
                Clock.fixed(Instant.parse("2026-10-23T05:05:00Z"), java.time.ZoneOffset.UTC));
        return PerformanceMetrics.of(bounds, period, List.of(
                        Metric.count("poCount", 12, 9),
                        Metric.money("poAmount:KRW", "KRW", 1200000, 900000),
                        Metric.money("poAmount:USD", "USD", 12345, 0),
                        Metric.count("receivedCount", 4, 6)),
                Map.of(LocalDate.parse("2026-10-20"), Map.of("poCount", 3L, "poAmount:KRW", 300000L)));
    }

    // ---- GET /po/metrics ------------------------------------------------------

    @Test
    @DisplayName("GET /po/metrics?period=week → 200, the shared shape bare (no envelope), integer strings")
    void metrics200() throws Exception {
        when(metricsService.getMetrics(any(ActorContext.class), eq(MetricsPeriod.WEEK)))
                .thenReturn(sample(MetricsPeriod.WEEK));

        mockMvc.perform(get("/api/procurement/po/metrics").param("period", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("week"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.current.from").value("2026-10-19T00:00:00+09:00"))
                .andExpect(jsonPath("$.current.to").value("2026-10-23T14:05:00+09:00"))
                .andExpect(jsonPath("$.previous.from").value("2026-10-12T00:00:00+09:00"))
                .andExpect(jsonPath("$.metrics[0].key").value("poCount"))
                .andExpect(jsonPath("$.metrics[0].unit").value("count"))
                .andExpect(jsonPath("$.metrics[0].current").value("12"))
                .andExpect(jsonPath("$.metrics[0].previous").value("9"))
                .andExpect(jsonPath("$.metrics[1].key").value("poAmount:KRW"))
                .andExpect(jsonPath("$.metrics[1].unit").value("money"))
                .andExpect(jsonPath("$.metrics[1].currency").value("KRW"))
                .andExpect(jsonPath("$.metrics[1].current").value("1200000"))
                .andExpect(jsonPath("$.metrics[2].key").value("poAmount:USD"))
                .andExpect(jsonPath("$.metrics[2].current").value("12345"))
                .andExpect(jsonPath("$.metrics[3].key").value("receivedCount"))
                .andExpect(jsonPath("$.series[1].date").value("2026-10-20"))
                .andExpect(jsonPath("$.series[1].values.poCount").value("3"))
                .andExpect(jsonPath("$.series[1].values['poAmount:KRW']").value("300000"))
                .andExpect(jsonPath("$.series[1].values['poAmount:USD']").value("0"))
                .andExpect(jsonPath("$.series[1].values.receivedCount").value("0"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(metricsService).getMetrics(any(ActorContext.class), eq(MetricsPeriod.WEEK));
        // /po/metrics was NOT routed to GET /po/{poId}
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("period is case-insensitive: MONTH → 200")
    void periodCaseInsensitive() throws Exception {
        when(metricsService.getMetrics(any(ActorContext.class), eq(MetricsPeriod.MONTH)))
                .thenReturn(sample(MetricsPeriod.MONTH));

        mockMvc.perform(get("/api/procurement/po/metrics").param("period", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("month"));
    }

    @Test
    @DisplayName("period missing → 400 VALIDATION_ERROR (not this service's usual 422), nothing called")
    void periodMissing400() throws Exception {
        mockMvc.perform(get("/api/procurement/po/metrics"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(metricsService, service);
    }

    @Test
    @DisplayName("period unknown (today) → 400 VALIDATION_ERROR")
    void periodUnknown400() throws Exception {
        mockMvc.perform(get("/api/procurement/po/metrics").param("period", "today"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(metricsService, service);
    }

    @Test
    @DisplayName("GET /po/{poId} still routes to the PO detail for any other id")
    void poIdStillRoutes() throws Exception {
        when(service.get(eq("metrics-not"), any(ActorContext.class)))
                .thenThrow(new com.example.scmplatform.procurement.domain.error.PoNotFoundException("PO not found"));

        mockMvc.perform(get("/api/procurement/po/metrics-not"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PO_NOT_FOUND"));
        verifyNoInteractions(metricsService);
    }

    // ---- GET /po submit-date filter -------------------------------------------

    private static PageResult<PurchaseOrderView> emptyPage() {
        return new PageResult<>(List.of(), 0, 20, 0L, 0);
    }

    @Test
    @DisplayName("GET /po?submittedFrom&submittedTo&status → the instants (offset or Z) reach the service with the status")
    void searchPassesSubmitDateBounds() throws Exception {
        Instant from = Instant.parse("2026-10-18T15:00:00Z");
        Instant to = Instant.parse("2026-10-23T05:05:00Z");
        when(service.search(any(ActorContext.class), eq(PoStatus.SUBMITTED), isNull(), isNull(),
                eq(from), eq(to), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/procurement/po")
                        .param("submittedFrom", "2026-10-19T00:00:00+09:00")
                        .param("submittedTo", "2026-10-23T05:05:00Z")
                        .param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        verify(service).search(any(ActorContext.class), eq(PoStatus.SUBMITTED), isNull(), isNull(),
                eq(from), eq(to), any());
    }

    @Test
    @DisplayName("GET /po with only submittedFrom → submittedTo is null")
    void searchOneBound() throws Exception {
        Instant from = Instant.parse("2026-10-18T15:00:00Z");
        when(service.search(any(ActorContext.class), isNull(), isNull(), isNull(), eq(from), isNull(), any()))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/procurement/po").param("submittedFrom", "2026-10-18T15:00:00Z"))
                .andExpect(status().isOk());
        verify(service).search(any(ActorContext.class), isNull(), isNull(), isNull(), eq(from), isNull(), any());
    }

    @Test
    @DisplayName("GET /po with a submittedFrom that is not an ISO-8601 instant → 400 VALIDATION_ERROR")
    void searchBadInstant400() throws Exception {
        mockMvc.perform(get("/api/procurement/po").param("submittedFrom", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/procurement/po").param("submittedTo", "2026-13-45T99:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("GET /po — the metrics card link (submittedFrom · submittedTo · excludeStatus=CANCELED) reaches the service as sent")
    void searchPassesExcludeStatus() throws Exception {
        Instant from = Instant.parse("2026-10-18T15:00:00Z");
        Instant to = Instant.parse("2026-10-23T05:05:00Z");
        when(service.search(any(ActorContext.class), isNull(), eq(PoStatus.CANCELED), isNull(),
                eq(from), eq(to), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/procurement/po")
                        .param("submittedFrom", "2026-10-18T15:00:00Z")
                        .param("submittedTo", "2026-10-23T05:05:00Z")
                        .param("excludeStatus", "CANCELED"))
                .andExpect(status().isOk());
        verify(service).search(any(ActorContext.class), isNull(), eq(PoStatus.CANCELED), isNull(),
                eq(from), eq(to), any());
    }

    @Test
    @DisplayName("GET /po — excludeStatus that is not a PoStatus → 400 VALIDATION_ERROR, service never called")
    void searchBadExcludeStatus400() throws Exception {
        mockMvc.perform(get("/api/procurement/po").param("excludeStatus", "GONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
}
