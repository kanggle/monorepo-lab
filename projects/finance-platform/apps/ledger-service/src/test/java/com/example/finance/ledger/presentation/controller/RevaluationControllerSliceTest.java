package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.RevalueForeignBalanceUseCase;
import com.example.finance.ledger.application.RevalueForeignBalanceUseCase.NoOpReason;
import com.example.finance.ledger.application.RevalueForeignBalanceUseCase.Result;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerPeriodClosedException;
import com.example.finance.ledger.domain.error.LedgerErrors.RevaluationRateInvalidException;
import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.journal.FxRevaluationPolicy.Outcome;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.SourceRef;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link RevaluationController} + the
 * {@link GlobalExceptionHandler} error envelope (9th increment, TASK-FIN-BE-015).
 * Security filters bypassed; the {@link ActorContext} is placed directly in the
 * {@link SecurityContextHolder}. Proves 201 revalued / 200 no-op / 200 replay, the
 * missing-header 400, and the domain error envelopes (422).
 */
@WebMvcTest(RevaluationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RevaluationControllerSliceTest {

    private static final ActorContext ACTOR =
            new ActorContext("operator-7", "finance", java.util.Set.of("finance.read"));
    private static final Instant POSTED_AT = Instant.parse("2026-06-30T23:59:59Z");
    private static final String CASH = LedgerAccountCodes.CASH_CLEARING;

    private static final String BODY = """
            { "ledgerAccountCode": "CASH_CLEARING",
              "currency": "USD",
              "closingRate": "13.5",
              "postedAt": "2026-06-30T23:59:59Z",
              "reference": "FX-REVAL-2026-06-USD",
              "memo": "month-end USD revaluation" }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean RevalueForeignBalanceUseCase revalueForeignBalance;

    @BeforeEach
    void setUp() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(ACTOR, "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static JournalEntry revaluationEntry() {
        Money baseDelta = Money.of(5_000L, Currency.KRW);
        return JournalEntry.post("e-1", "finance", POSTED_AT,
                SourceRef.ofRevaluation("FX-REVAL-2026-06-USD", "reval:FX-REVAL-2026-06-USD"),
                List.of(JournalLine.baseAdjustment("finance", CASH, Currency.USD,
                                EntryDirection.DEBIT, baseDelta, new BigDecimal("13.5")),
                        JournalLine.credit("finance", LedgerAccountCodes.FX_GAIN, baseDelta)));
    }

    @Test
    @DisplayName("POST /revaluations booked → 201 revalued:true with deltaBaseMinor + outcome + entry")
    void revaluedCreated() throws Exception {
        when(revalueForeignBalance.revalue(any()))
                .thenReturn(new Result(true, 5_000L, Outcome.FX_GAIN, null, revaluationEntry()));

        mockMvc.perform(post("/api/finance/ledger/revaluations")
                        .header("Idempotency-Key", "FX-REVAL-2026-06-USD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.revalued").value(true))
                .andExpect(jsonPath("$.data.deltaBaseMinor").value("5000"))
                .andExpect(jsonPath("$.data.outcome").value("FX_GAIN"))
                .andExpect(jsonPath("$.data.entry.entryId").value("e-1"))
                .andExpect(jsonPath("$.data.entry.source.sourceType").value("REVALUATION"))
                .andExpect(jsonPath("$.data.entry.lines.length()").value(2))
                .andExpect(jsonPath("$.data.entry.lines[0].money.amount").value("0"))
                .andExpect(jsonPath("$.data.entry.lines[0].money.currency").value("USD"))
                .andExpect(jsonPath("$.data.entry.lines[0].baseAmount.amount").value("5000"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("POST /revaluations no position → 200 revalued:false NO_POSITION, no entry")
    void noPositionNoOp() throws Exception {
        when(revalueForeignBalance.revalue(any()))
                .thenReturn(new Result(false, 0L, null, NoOpReason.NO_POSITION, null));

        mockMvc.perform(post("/api/finance/ledger/revaluations")
                        .header("Idempotency-Key", "FX-REVAL-2026-06-USD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revalued").value(false))
                .andExpect(jsonPath("$.data.reason").value("NO_POSITION"))
                .andExpect(jsonPath("$.data.entry").doesNotExist());
    }

    @Test
    @DisplayName("POST /revaluations replay → 200 revalued:false REPLAY with the original entry")
    void replayOk() throws Exception {
        when(revalueForeignBalance.revalue(any()))
                .thenReturn(new Result(false, 0L, null, NoOpReason.REPLAY, revaluationEntry()));

        mockMvc.perform(post("/api/finance/ledger/revaluations")
                        .header("Idempotency-Key", "FX-REVAL-2026-06-USD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revalued").value(false))
                .andExpect(jsonPath("$.data.reason").value("REPLAY"))
                .andExpect(jsonPath("$.data.entry.entryId").value("e-1"));
    }

    @Test
    @DisplayName("POST /revaluations without Idempotency-Key → 400 IDEMPOTENCY_KEY_REQUIRED")
    void missingKey() throws Exception {
        mockMvc.perform(post("/api/finance/ledger/revaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("POST /revaluations closingRate ≤ 0 → 422 REVALUATION_RATE_INVALID")
    void invalidRate() throws Exception {
        when(revalueForeignBalance.revalue(any()))
                .thenThrow(new RevaluationRateInvalidException("closingRate must be strictly positive"));

        mockMvc.perform(post("/api/finance/ledger/revaluations")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REVALUATION_RATE_INVALID"));
    }

    @Test
    @DisplayName("POST /revaluations base currency → 422 CURRENCY_MISMATCH")
    void currencyMismatch() throws Exception {
        when(revalueForeignBalance.revalue(any()))
                .thenThrow(new CurrencyMismatchException("the base currency cannot be revalued"));

        mockMvc.perform(post("/api/finance/ledger/revaluations")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    @DisplayName("POST /revaluations into a CLOSED period → 422 LEDGER_PERIOD_CLOSED")
    void closedPeriod() throws Exception {
        when(revalueForeignBalance.revalue(any()))
                .thenThrow(new LedgerPeriodClosedException("posting into a CLOSED period"));

        mockMvc.perform(post("/api/finance/ledger/revaluations")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LEDGER_PERIOD_CLOSED"));
    }
}
