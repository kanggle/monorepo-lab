package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for the {@code POST /api/finance/ledger/fx-rates/refresh} endpoint
 * on {@link FxRateController} (TASK-MONO-300 — ADR-002 manual-refresh realized).
 *
 * <p>Security filters are bypassed ({@code addFilters = false}); the {@link ActorContext} is
 * placed directly in the {@link SecurityContextHolder}. Proves:
 * <ul>
 *   <li>POST refresh invokes {@link RefreshFxRateQuotesUseCase#refresh()} and returns
 *       {@code {feedEnabled, refreshed}} (AC-1).</li>
 *   <li>Feed enabled → count of upserted pairs returned (AC-1).</li>
 *   <li>Feed disabled → 200 no-op {@code {feedEnabled:false, refreshed:0}} (AC-2).</li>
 *   <li>No authenticated actor in context → {@link ActorContextResolver#currentOrThrow()}
 *       throws {@link IllegalStateException} → 422 (verifies auth enforcement at the
 *       controller level — the real 401/403 is enforced by the security filter chain
 *       tested in integration tests with full Spring context) (AC-1).</li>
 *   <li>{@code meta.timestamp} present (envelope shape invariant).</li>
 * </ul>
 */
@WebMvcTest(FxRateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FxRatesRefreshControllerSliceTest extends AbstractFxRateControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("POST /fx-rates/refresh → 200 feedEnabled:true refreshed:3 — use case invoked, count returned")
    void refreshInvokesUseCaseAndReturnsCount() throws Exception {
        when(fxRateFeedSettings.feedEnabled()).thenReturn(true);
        when(refreshFxRateQuotes.refresh()).thenReturn(3);

        mockMvc.perform(post("/api/finance/ledger/fx-rates/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedEnabled").value(true))
                .andExpect(jsonPath("$.data.refreshed").value(3))
                .andExpect(jsonPath("$.meta.timestamp").exists());

        verify(refreshFxRateQuotes).refresh();
    }

    @Test
    @DisplayName("POST /fx-rates/refresh feed disabled → 200 feedEnabled:false refreshed:0 (no-op, not an error)")
    void feedDisabledReturns200NoOp() throws Exception {
        when(fxRateFeedSettings.feedEnabled()).thenReturn(false);
        when(refreshFxRateQuotes.refresh()).thenReturn(0);

        mockMvc.perform(post("/api/finance/ledger/fx-rates/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedEnabled").value(false))
                .andExpect(jsonPath("$.data.refreshed").value(0))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("POST /fx-rates/refresh partial provider failure → 200 with partial count (best-effort, no 500)")
    void partialProviderFailureReturnsBestEffortCount() throws Exception {
        // Simulate 2 of 3 pairs succeeding (one pair failed inside use case, logged + continued)
        when(fxRateFeedSettings.feedEnabled()).thenReturn(true);
        when(refreshFxRateQuotes.refresh()).thenReturn(2);

        mockMvc.perform(post("/api/finance/ledger/fx-rates/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedEnabled").value(true))
                .andExpect(jsonPath("$.data.refreshed").value(2));
    }

    @Test
    @DisplayName("POST /fx-rates/refresh → 200 refreshed:0 when zero pairs configured (empty pairs list)")
    void zeroPairsConfiguredReturns200WithZeroCount() throws Exception {
        when(fxRateFeedSettings.feedEnabled()).thenReturn(true);
        when(refreshFxRateQuotes.refresh()).thenReturn(0);

        mockMvc.perform(post("/api/finance/ledger/fx-rates/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedEnabled").value(true))
                .andExpect(jsonPath("$.data.refreshed").value(0));
    }

    @Test
    @DisplayName("POST /fx-rates/refresh no actor in SecurityContext → currentOrThrow() throws → 422 (auth enforcement at controller level)")
    void noActorInContextYields422() throws Exception {
        // Clear the SecurityContext set up in @BeforeEach — simulates missing auth principal.
        // The real 401/403 is enforced by the security filter chain (integration tests);
        // in this slice test with addFilters=false, currentOrThrow() throws IllegalStateException → 422.
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/api/finance/ledger/fx-rates/refresh"))
                .andExpect(status().isUnprocessableEntity());
    }
}
