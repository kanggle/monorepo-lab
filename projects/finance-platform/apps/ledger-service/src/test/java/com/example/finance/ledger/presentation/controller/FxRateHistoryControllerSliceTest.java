package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.GetFxRateHistoryUseCase;
import com.example.finance.ledger.application.GetFxRatesUseCase;
import com.example.finance.ledger.application.RefreshFxRateQuotesUseCase;
import com.example.finance.ledger.application.port.outbound.FxRateFeedSettings;
import com.example.finance.ledger.application.view.FxRateHistorySummaryView;
import com.example.finance.ledger.application.view.FxRateHistoryView;
import com.example.finance.ledger.presentation.advice.GlobalExceptionHandler;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for the history endpoint on {@link FxRateController}
 * (27th increment — TASK-FIN-BE-040). Security filters bypassed; the
 * {@link ActorContext} is placed directly in the {@link SecurityContextHolder}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>200 with newest-first quotes + rate as string (F5).</li>
 *   <li>Empty {@code quotes} array for an unknown pair (200, not 404).</li>
 *   <li>{@code meta.timestamp} present.</li>
 * </ul>
 */
@WebMvcTest(FxRateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FxRateHistoryControllerSliceTest {

    private static final ActorContext ACTOR =
            new ActorContext("operator-1", "finance", Set.of("finance.read"));

    private static final Instant T1 = Instant.parse("2026-06-15T06:00:00Z");
    private static final Instant T2 = Instant.parse("2026-06-15T07:00:00Z"); // newer

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetFxRatesUseCase getFxRates;

    @MockitoBean
    GetFxRateHistoryUseCase getFxRateHistory;

    @MockitoBean
    RefreshFxRateQuotesUseCase refreshFxRateQuotes;

    @MockitoBean
    FxRateFeedSettings fxRateFeedSettings;

    @BeforeEach
    void setUp() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(ACTOR, "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("GET /fx-rates/USD/history → 200 with two quotes newest-first, rate as string")
    void historyReturnsTwoQuotesNewestFirst() throws Exception {
        FxRateHistoryView newer = new FxRateHistoryView(
                new BigDecimal("13.60000000"), T2, T2, "stub");
        FxRateHistoryView older = new FxRateHistoryView(
                new BigDecimal("13.50000000"), T1, T1, "stub");

        when(getFxRateHistory.get("USD", null))
                .thenReturn(new FxRateHistorySummaryView("KRW", "USD", List.of(newer, older)));

        mockMvc.perform(get("/api/finance/ledger/fx-rates/USD/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.base").value("KRW"))
                .andExpect(jsonPath("$.data.foreign").value("USD"))
                .andExpect(jsonPath("$.data.quotes.length()").value(2))
                // newest first
                .andExpect(jsonPath("$.data.quotes[0].rate").value("13.60000000"))
                .andExpect(jsonPath("$.data.quotes[0].source").value("stub"))
                .andExpect(jsonPath("$.data.quotes[0].asOf").isNotEmpty())
                .andExpect(jsonPath("$.data.quotes[0].fetchedAt").isNotEmpty())
                // older second
                .andExpect(jsonPath("$.data.quotes[1].rate").value("13.50000000"))
                // meta envelope
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("GET /fx-rates/XXX/history → 200 quotes [] (unknown pair, empty-200 not 404)")
    void unknownPairReturnsEmpty200() throws Exception {
        when(getFxRateHistory.get("XXX", null))
                .thenReturn(new FxRateHistorySummaryView("KRW", "XXX", List.of()));

        mockMvc.perform(get("/api/finance/ledger/fx-rates/XXX/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.base").value("KRW"))
                .andExpect(jsonPath("$.data.foreign").value("XXX"))
                .andExpect(jsonPath("$.data.quotes").isArray())
                .andExpect(jsonPath("$.data.quotes").isEmpty())
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("GET /fx-rates/USD/history?limit=1 → limit forwarded to use-case")
    void limitParamIsForwarded() throws Exception {
        FxRateHistoryView row = new FxRateHistoryView(
                new BigDecimal("13.60000000"), T2, T2, "stub");

        when(getFxRateHistory.get("USD", 1))
                .thenReturn(new FxRateHistorySummaryView("KRW", "USD", List.of(row)));

        mockMvc.perform(get("/api/finance/ledger/fx-rates/USD/history").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotes.length()").value(1))
                .andExpect(jsonPath("$.data.quotes[0].rate").value("13.60000000"));
    }

    @Test
    @DisplayName("GET /fx-rates/USD/history → rate is a string not a float (F5)")
    void rateIsStringNotFloat() throws Exception {
        FxRateHistoryView row = new FxRateHistoryView(
                new BigDecimal("0.00930000"), T1, T1, "stub");

        when(getFxRateHistory.get("USD", null))
                .thenReturn(new FxRateHistorySummaryView("KRW", "USD", List.of(row)));

        mockMvc.perform(get("/api/finance/ledger/fx-rates/USD/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotes[0].rate").value("0.00930000"));
    }
}
