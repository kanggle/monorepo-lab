package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.view.FxRateView;
import com.example.finance.ledger.application.view.FxRatesView;
import com.example.finance.ledger.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link FxRateController} + the
 * {@link GlobalExceptionHandler} error envelope (25th increment — TASK-FIN-BE-033).
 * Security filters bypassed; the {@link ActorContext} is placed directly in the
 * {@link SecurityContextHolder}. Proves 200 with feedEnabled + rates array
 * (rate as string, stale/ageSeconds present), and empty cache → rates [].
 */
@WebMvcTest(FxRateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FxRateControllerSliceTest extends AbstractFxRateControllerSliceTest {

    private static final Instant AS_OF = Instant.parse("2026-06-15T06:00:00Z");
    private static final Instant FETCHED_AT = Instant.parse("2026-06-15T06:00:05Z");

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("GET /fx-rates → 200 with feedEnabled=true and two quotes, rate as string, stale+ageSeconds present")
    void listReturnsTwoQuotes() throws Exception {
        FxRateView usd = new FxRateView(
                "KRW", "USD", new BigDecimal("13.50000000"),
                AS_OF, "stub", FETCHED_AT, 21600L, false);
        FxRateView eur = new FxRateView(
                "KRW", "EUR", new BigDecimal("14.20000000"),
                AS_OF, "stub", FETCHED_AT, 21600L, false);

        when(getFxRates.get()).thenReturn(new FxRatesView(true, List.of(usd, eur)));

        mockMvc.perform(get("/api/finance/ledger/fx-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedEnabled").value(true))
                .andExpect(jsonPath("$.data.rates.length()").value(2))
                // First quote: KRW/EUR (sorted)
                .andExpect(jsonPath("$.data.rates[0].baseCurrency").value("KRW"))
                .andExpect(jsonPath("$.data.rates[0].foreignCurrency").value("USD"))
                // rate MUST be a string, not a float
                .andExpect(jsonPath("$.data.rates[0].rate").value("13.50000000"))
                .andExpect(jsonPath("$.data.rates[0].source").value("stub"))
                .andExpect(jsonPath("$.data.rates[0].ageSeconds").value(21600))
                .andExpect(jsonPath("$.data.rates[0].stale").value(false))
                .andExpect(jsonPath("$.data.rates[1].foreignCurrency").value("EUR"))
                .andExpect(jsonPath("$.data.rates[1].rate").value("14.20000000"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("GET /fx-rates → 200 feedEnabled=true rates [] when cache empty")
    void emptyCacheReturns200WithEmptyRates() throws Exception {
        when(getFxRates.get()).thenReturn(new FxRatesView(true, List.of()));

        mockMvc.perform(get("/api/finance/ledger/fx-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedEnabled").value(true))
                .andExpect(jsonPath("$.data.rates").isArray())
                .andExpect(jsonPath("$.data.rates").isEmpty())
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("GET /fx-rates → feedEnabled=false when feed is disabled")
    void feedDisabledExposedInResponse() throws Exception {
        FxRateView staleUsd = new FxRateView(
                "KRW", "USD", new BigDecimal("13.00"),
                AS_OF.minusSeconds(200000), "stub", FETCHED_AT.minusSeconds(200000),
                200000L, true);

        when(getFxRates.get()).thenReturn(new FxRatesView(false, List.of(staleUsd)));

        mockMvc.perform(get("/api/finance/ledger/fx-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedEnabled").value(false))
                .andExpect(jsonPath("$.data.rates.length()").value(1))
                .andExpect(jsonPath("$.data.rates[0].stale").value(true))
                .andExpect(jsonPath("$.data.rates[0].ageSeconds").value(200000));
    }

    @Test
    @DisplayName("GET /fx-rates → rate is serialised as a string (F5), not a JSON number")
    void rateIsStringNotFloat() throws Exception {
        // Use a rate that would lose precision as a float (many decimal places)
        FxRateView q = new FxRateView(
                "KRW", "JPY", new BigDecimal("0.00930000"),
                AS_OF, "http:provider", FETCHED_AT, 3600L, false);

        when(getFxRates.get()).thenReturn(new FxRatesView(true, List.of(q)));

        mockMvc.perform(get("/api/finance/ledger/fx-rates"))
                .andExpect(status().isOk())
                // Must be the string "0.00930000", not the number 0.0093
                .andExpect(jsonPath("$.data.rates[0].rate").value("0.00930000"))
                .andExpect(jsonPath("$.data.rates[0].foreignCurrency").value("JPY"));
    }
}
