package com.example.settlement.presentation;

import com.example.settlement.TestSettlementServiceApplication;
import com.example.settlement.application.service.CloseSettlementPeriodUseCase;
import com.example.settlement.application.service.ExecuteSellerPayoutsUseCase;
import com.example.settlement.application.service.OpenSettlementPeriodUseCase;
import com.example.settlement.application.service.QuerySettlementPeriodUseCase;
import com.example.settlement.application.view.PayoutView;
import com.example.settlement.application.view.PeriodView;
import com.example.settlement.domain.period.PeriodAlreadyClosedException;
import com.example.settlement.domain.period.PeriodNotClosedException;
import com.example.settlement.domain.period.PeriodNotFoundException;
import com.example.settlement.domain.period.PeriodWindowInvalidException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementPeriodController.class)
@ContextConfiguration(classes = TestSettlementServiceApplication.class)
@Import(GlobalExceptionHandler.class)
class SettlementPeriodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenSettlementPeriodUseCase openPeriod;
    @MockitoBean
    private CloseSettlementPeriodUseCase closePeriod;
    @MockitoBean
    private QuerySettlementPeriodUseCase queryPeriod;
    @MockitoBean
    private ExecuteSellerPayoutsUseCase executePayouts;

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");

    private static final String OPEN_BODY = """
            {"from":"2026-06-01T00:00:00Z","to":"2026-07-01T00:00:00Z"}
            """;

    // ── POST /periods ──────────────────────────────────────────────────────

    @Test
    void open_admin_returns201_open() throws Exception {
        given(openPeriod.open(anyString(), any(), any())).willReturn(
                new PeriodView("p-1", FROM, TO, "OPEN", null, null, List.of()));

        mockMvc.perform(post("/api/admin/settlements/periods")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodId").value("p-1"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.closedAt").doesNotExist());
    }

    @Test
    void open_invalidWindow_returns422() throws Exception {
        given(openPeriod.open(anyString(), any(), any()))
                .willThrow(new PeriodWindowInvalidException("from >= to"));

        mockMvc.perform(post("/api/admin/settlements/periods")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIOD_WINDOW_INVALID"));
    }

    @Test
    void open_missingRole_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/settlements/periods")
                        .contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void open_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/settlements/periods")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ── POST /periods/{id}/close ───────────────────────────────────────────

    @Test
    void close_admin_returns200_closedWithPendingPayouts() throws Exception {
        PayoutView payout = new PayoutView("po-1", "seller-1", 27_000L, 3_000L, 1,
                "PENDING", null, null);
        given(closePeriod.close(eq("p-1"), anyString(), anyString())).willReturn(
                new PeriodView("p-1", FROM, TO, "CLOSED",
                        Instant.parse("2026-07-01T09:00:00Z"), 1, List.of(payout)));

        mockMvc.perform(post("/api/admin/settlements/periods/p-1/close")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.sellerCount").value(1))
                .andExpect(jsonPath("$.payouts[0].payoutId").value("po-1"))
                .andExpect(jsonPath("$.payouts[0].status").value("PENDING"))
                .andExpect(jsonPath("$.payouts[0].payableNetMinor").value(27000))
                // W-4: payoutReference is PRESENT with value null while PENDING (contract).
                .andExpect(jsonPath("$.payouts[0].payoutReference").value(nullValue()));
    }

    @Test
    void close_alreadyClosed_returns409() throws Exception {
        given(closePeriod.close(eq("p-1"), anyString(), anyString()))
                .willThrow(new PeriodAlreadyClosedException("already closed"));

        mockMvc.perform(post("/api/admin/settlements/periods/p-1/close")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERIOD_ALREADY_CLOSED"));
    }

    @Test
    void close_crossTenantOrAbsent_returns404() throws Exception {
        given(closePeriod.close(eq("p-x"), anyString(), anyString()))
                .willThrow(new PeriodNotFoundException("not found"));

        mockMvc.perform(post("/api/admin/settlements/periods/p-x/close")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    @Test
    void close_missingRole_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/settlements/periods/p-1/close"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ── GET /periods ───────────────────────────────────────────────────────

    @Test
    void list_admin_returns200_tenantScoped() throws Exception {
        given(queryPeriod.listPeriods(anyString())).willReturn(List.of(
                new PeriodView("p-1", FROM, TO, "CLOSED",
                        Instant.parse("2026-07-01T09:00:00Z"), 1, List.of())));

        mockMvc.perform(get("/api/admin/settlements/periods").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].periodId").value("p-1"))
                .andExpect(jsonPath("$.items[0].status").value("CLOSED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_missingRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/settlements/periods"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ── GET /periods/{id}/payouts ──────────────────────────────────────────────

    @Test
    void listPayouts_admin_returns200_with_payout_rows() throws Exception {
        PayoutView paid = new PayoutView("po-1", "seller-1", 27_000L, 3_000L, 1,
                "PAID", "SIM-p-1-seller-1-uuid1", Instant.parse("2026-07-02T12:00:00Z"));
        PayoutView pending = new PayoutView("po-2", "seller-2", 18_000L, 2_000L, 2,
                "PENDING", null, null);
        given(executePayouts.list(eq("p-1"), anyString()))
                .willReturn(List.of(paid, pending));

        mockMvc.perform(get("/api/admin/settlements/periods/p-1/payouts")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].payoutId").value("po-1"))
                .andExpect(jsonPath("$.items[0].status").value("PAID"))
                .andExpect(jsonPath("$.items[0].payoutReference").value("SIM-p-1-seller-1-uuid1"))
                .andExpect(jsonPath("$.items[1].status").value("PENDING"))
                // W-4: payoutReference is PRESENT with value null while PENDING (contract).
                .andExpect(jsonPath("$.items[1].payoutReference").value(nullValue()))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listPayouts_missingRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/settlements/periods/p-1/payouts"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listPayouts_crossTenantOrAbsent_returns404() throws Exception {
        given(executePayouts.list(eq("p-x"), anyString()))
                .willThrow(new PeriodNotFoundException("not found"));

        mockMvc.perform(get("/api/admin/settlements/periods/p-x/payouts")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    // ── POST /periods/{id}/payouts/execute ────────────────────────────────────

    @Test
    void executePayouts_admin_returns200_post_execution_statuses() throws Exception {
        PayoutView paid = new PayoutView("po-1", "seller-1", 27_000L, 3_000L, 1,
                "PAID", "SIM-p-1-seller-1-uuid1", Instant.parse("2026-07-02T12:00:00Z"));
        given(executePayouts.execute(eq("p-1"), anyString()))
                .willReturn(List.of(paid));

        mockMvc.perform(post("/api/admin/settlements/periods/p-1/payouts/execute")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("PAID"))
                .andExpect(jsonPath("$.items[0].payoutReference").value("SIM-p-1-seller-1-uuid1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void executePayouts_openPeriod_returns409_PERIOD_NOT_CLOSED() throws Exception {
        given(executePayouts.execute(eq("p-open"), anyString()))
                .willThrow(new PeriodNotClosedException("period is not CLOSED: p-open"));

        mockMvc.perform(post("/api/admin/settlements/periods/p-open/payouts/execute")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERIOD_NOT_CLOSED"));
    }

    @Test
    void executePayouts_crossTenantOrAbsent_returns404() throws Exception {
        given(executePayouts.execute(eq("p-x"), anyString()))
                .willThrow(new PeriodNotFoundException("not found"));

        mockMvc.perform(post("/api/admin/settlements/periods/p-x/payouts/execute")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    @Test
    void executePayouts_missingRole_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/settlements/periods/p-1/payouts/execute"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
