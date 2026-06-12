package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.SettleForeignPositionUseCase;
import com.example.finance.ledger.application.SettleForeignPositionUseCase.NoOpReason;
import com.example.finance.ledger.application.SettleForeignPositionUseCase.Result;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerAccountNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerPeriodClosedException;
import com.example.finance.ledger.domain.error.LedgerErrors.SettlementRateInvalidException;
import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.journal.FxSettlementPolicy.Outcome;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link SettlementController} + the
 * {@link GlobalExceptionHandler} error envelope (10th increment, TASK-FIN-BE-016).
 * Security filters bypassed; the {@link ActorContext} is placed directly in the
 * {@link SecurityContextHolder}. Proves 201 settled / 200 no-op / 200 replay, the
 * missing-header 400, and the domain error envelopes (422 / 404).
 */
@WebMvcTest(SettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SettlementControllerSliceTest {

    private static final ActorContext ACTOR =
            new ActorContext("operator-7", "finance", java.util.Set.of("finance.read"));
    private static final Instant POSTED_AT = Instant.parse("2026-06-30T23:59:59Z");
    private static final String CASH = LedgerAccountCodes.CASH_CLEARING;
    private static final String PROCEEDS = LedgerAccountCodes.SETTLEMENT_SUSPENSE;

    private static final String BODY = """
            { "ledgerAccountCode": "CASH_CLEARING",
              "currency": "USD",
              "settlementRate": "13.7",
              "proceedsAccountCode": "SETTLEMENT_SUSPENSE",
              "postedAt": "2026-06-30T23:59:59Z",
              "reference": "FX-SETTLE-2026-06-USD",
              "memo": "liquidate USD holdings" }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean SettleForeignPositionUseCase settleForeignPosition;

    @BeforeEach
    void setUp() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(ACTOR, "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static JournalEntry settlementEntry() {
        Money base130 = Money.of(130_000L, Currency.KRW);
        Money base137 = Money.of(137_000L, Currency.KRW);
        Money base7 = Money.of(7_000L, Currency.KRW);
        return JournalEntry.post("e-1", "finance", POSTED_AT,
                SourceRef.ofSettlement("FX-SETTLE-2026-06-USD", "settle:FX-SETTLE-2026-06-USD"),
                List.of(JournalLine.of("finance", CASH, EntryDirection.CREDIT,
                                Money.of(10_000L, Currency.USD), base130),
                        JournalLine.of("finance", PROCEEDS, EntryDirection.DEBIT, base137),
                        JournalLine.credit("finance", LedgerAccountCodes.FX_GAIN, base7)));
    }

    @Test
    @DisplayName("POST /settlements booked → 201 settled:true with realized + proceeds + outcome + entry")
    void settledCreated() throws Exception {
        when(settleForeignPosition.settle(any()))
                .thenReturn(new Result(true, 7_000L, 137_000L, Outcome.FX_GAIN, 0L, 0L, null, settlementEntry()));

        mockMvc.perform(post("/api/finance/ledger/settlements")
                        .header("Idempotency-Key", "FX-SETTLE-2026-06-USD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.settled").value(true))
                .andExpect(jsonPath("$.data.realizedBaseMinor").value("7000"))
                .andExpect(jsonPath("$.data.proceedsBaseMinor").value("137000"))
                .andExpect(jsonPath("$.data.outcome").value("FX_GAIN"))
                .andExpect(jsonPath("$.data.entry.entryId").value("e-1"))
                .andExpect(jsonPath("$.data.entry.source.sourceType").value("SETTLEMENT"))
                .andExpect(jsonPath("$.data.entry.lines.length()").value(3))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("POST /settlements partial (settleForeignAmount) → 201 with residual OPEN exposed")
    void partialSettledWithResidual() throws Exception {
        // Settle $40 of $100: realized +2800, proceeds 54800, residual (6000, 78000).
        when(settleForeignPosition.settle(any()))
                .thenReturn(new Result(true, 2_800L, 54_800L, Outcome.FX_GAIN,
                        6_000L, 78_000L, null, settlementEntry()));

        String partialBody = """
                { "ledgerAccountCode": "CASH_CLEARING",
                  "currency": "USD",
                  "settlementRate": "13.7",
                  "proceedsAccountCode": "SETTLEMENT_SUSPENSE",
                  "settleForeignAmount": "4000" }
                """;

        mockMvc.perform(post("/api/finance/ledger/settlements")
                        .header("Idempotency-Key", "FX-SETTLE-2026-06-USD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partialBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.settled").value(true))
                .andExpect(jsonPath("$.data.realizedBaseMinor").value("2800"))
                .andExpect(jsonPath("$.data.residualForeignMinor").value("6000"))
                .andExpect(jsonPath("$.data.residualCarryingBaseMinor").value("78000"));
    }

    @Test
    @DisplayName("POST /settlements invalid settleForeignAmount → 422 SETTLEMENT_AMOUNT_INVALID")
    void invalidSettleAmount() throws Exception {
        when(settleForeignPosition.settle(any()))
                .thenThrow(new com.example.finance.ledger.domain.error.LedgerErrors
                        .SettlementAmountInvalidException("settleForeignAmount exceeds the position"));

        String overBody = """
                { "ledgerAccountCode": "CASH_CLEARING",
                  "currency": "USD",
                  "settlementRate": "13.7",
                  "proceedsAccountCode": "SETTLEMENT_SUSPENSE",
                  "settleForeignAmount": "999999" }
                """;

        mockMvc.perform(post("/api/finance/ledger/settlements")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_AMOUNT_INVALID"));
    }

    @Test
    @DisplayName("POST /settlements no position → 200 settled:false NO_POSITION, no entry")
    void noPositionNoOp() throws Exception {
        when(settleForeignPosition.settle(any()))
                .thenReturn(new Result(false, 0L, 0L, null, 0L, 0L, NoOpReason.NO_POSITION, null));

        mockMvc.perform(post("/api/finance/ledger/settlements")
                        .header("Idempotency-Key", "FX-SETTLE-2026-06-USD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settled").value(false))
                .andExpect(jsonPath("$.data.reason").value("NO_POSITION"))
                .andExpect(jsonPath("$.data.entry").doesNotExist());
    }

    @Test
    @DisplayName("POST /settlements replay → 200 settled:false REPLAY with the original entry")
    void replayOk() throws Exception {
        when(settleForeignPosition.settle(any()))
                .thenReturn(new Result(false, 0L, 0L, null, 0L, 0L, NoOpReason.REPLAY, settlementEntry()));

        mockMvc.perform(post("/api/finance/ledger/settlements")
                        .header("Idempotency-Key", "FX-SETTLE-2026-06-USD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settled").value(false))
                .andExpect(jsonPath("$.data.reason").value("REPLAY"))
                .andExpect(jsonPath("$.data.entry.entryId").value("e-1"));
    }

    @Test
    @DisplayName("POST /settlements without Idempotency-Key → 400 IDEMPOTENCY_KEY_REQUIRED")
    void missingKey() throws Exception {
        mockMvc.perform(post("/api/finance/ledger/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("POST /settlements settlementRate ≤ 0 → 422 SETTLEMENT_RATE_INVALID")
    void invalidRate() throws Exception {
        when(settleForeignPosition.settle(any()))
                .thenThrow(new SettlementRateInvalidException("settlementRate must be strictly positive"));

        mockMvc.perform(post("/api/finance/ledger/settlements")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_RATE_INVALID"));
    }

    @Test
    @DisplayName("POST /settlements base currency → 422 CURRENCY_MISMATCH")
    void currencyMismatch() throws Exception {
        when(settleForeignPosition.settle(any()))
                .thenThrow(new CurrencyMismatchException("the base currency has no FX position to settle"));

        mockMvc.perform(post("/api/finance/ledger/settlements")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    @DisplayName("POST /settlements unknown proceeds account → 404 LEDGER_ACCOUNT_NOT_FOUND")
    void unknownProceeds() throws Exception {
        when(settleForeignPosition.settle(any()))
                .thenThrow(new LedgerAccountNotFoundException("proceeds account does not exist"));

        mockMvc.perform(post("/api/finance/ledger/settlements")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEDGER_ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /settlements into a CLOSED period → 422 LEDGER_PERIOD_CLOSED")
    void closedPeriod() throws Exception {
        when(settleForeignPosition.settle(any()))
                .thenThrow(new LedgerPeriodClosedException("posting into a CLOSED period"));

        mockMvc.perform(post("/api/finance/ledger/settlements")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LEDGER_PERIOD_CLOSED"));
    }
}
