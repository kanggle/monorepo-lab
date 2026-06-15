package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.DeleteFxCostFlowAccountConfigUseCase;
import com.example.finance.ledger.application.GetFxCostFlowAccountConfigsUseCase;
import com.example.finance.ledger.application.GetFxCostFlowConfigUseCase;
import com.example.finance.ledger.application.GetFxPositionLotsUseCase;
import com.example.finance.ledger.application.SetFxCostFlowAccountConfigCommand;
import com.example.finance.ledger.application.SetFxCostFlowAccountConfigUseCase;
import com.example.finance.ledger.application.SetFxCostFlowConfigCommand;
import com.example.finance.ledger.application.SetFxCostFlowConfigUseCase;
import com.example.finance.ledger.application.SettleForeignPositionUseCase;
import com.example.finance.ledger.application.SettleForeignPositionUseCase.NoOpReason;
import com.example.finance.ledger.application.SettleForeignPositionUseCase.Result;
import com.example.finance.ledger.application.view.FxCostFlowAccountConfigView;
import com.example.finance.ledger.application.view.FxCostFlowConfigView;
import com.example.finance.ledger.application.view.FxPositionLotView;
import com.example.finance.ledger.application.view.FxPositionLotsView;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.CostFlowMethodInvalidException;
import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.error.LedgerErrors.FxRateUnavailableException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerAccountNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerPeriodClosedException;
import com.example.finance.ledger.domain.error.LedgerErrors.SettlementRateInvalidException;
import com.example.finance.ledger.domain.journal.CostFlowMethod;
import com.example.finance.ledger.domain.money.Currency;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @MockitoBean GetFxCostFlowConfigUseCase getFxCostFlowConfig;
    @MockitoBean SetFxCostFlowConfigUseCase setFxCostFlowConfig;
    @MockitoBean GetFxPositionLotsUseCase getFxPositionLots;
    @MockitoBean GetFxCostFlowAccountConfigsUseCase getFxCostFlowAccountConfigs;
    @MockitoBean SetFxCostFlowAccountConfigUseCase setFxCostFlowAccountConfig;
    @MockitoBean DeleteFxCostFlowAccountConfigUseCase deleteFxCostFlowAccountConfig;

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
    @DisplayName("POST /settlements omitted rate + no fresh quote → 422 FX_RATE_UNAVAILABLE")
    void rateUnavailable() throws Exception {
        when(settleForeignPosition.settle(any()))
                .thenThrow(new FxRateUnavailableException(
                        "no FX rate supplied and the FX rate feed is disabled — supply a manual rate"));

        // The body omits settlementRate entirely → the use case resolves from the feed (here it
        // fails closed). The DTO no longer rejects a blank/absent rate (24th increment).
        String omittedRateBody = """
                { "ledgerAccountCode": "CASH_CLEARING",
                  "currency": "USD",
                  "proceedsAccountCode": "SETTLEMENT_SUSPENSE",
                  "reference": "FX-SETTLE-2026-06-USD",
                  "memo": "liquidate USD holdings" }
                """;

        mockMvc.perform(post("/api/finance/ledger/settlements")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(omittedRateBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FX_RATE_UNAVAILABLE"));
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

    // ---- 15th increment: FX cost-flow method config (TASK-FIN-BE-023) ----

    @Test
    @DisplayName("GET /cost-flow-config unset → 200 WEIGHTED_AVERAGE default, no audit fields")
    void getCostFlowConfigDefault() throws Exception {
        when(getFxCostFlowConfig.get("finance"))
                .thenReturn(FxCostFlowConfigView.weightedAverageDefault());

        mockMvc.perform(get("/api/finance/ledger/settlements/cost-flow-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("WEIGHTED_AVERAGE"))
                .andExpect(jsonPath("$.data.updatedBy").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /cost-flow-config configured → 200 with method + audit fields")
    void getCostFlowConfigured() throws Exception {
        when(getFxCostFlowConfig.get("finance")).thenReturn(new FxCostFlowConfigView(
                CostFlowMethod.FIFO, "user-1", Instant.parse("2026-02-01T10:00:00Z")));

        mockMvc.perform(get("/api/finance/ledger/settlements/cost-flow-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("FIFO"))
                .andExpect(jsonPath("$.data.updatedBy").value("user-1"));
    }

    @Test
    @DisplayName("PUT /cost-flow-config FIFO → 200 persisted config, audited (updatedBy = actor)")
    void putCostFlowConfigFifoOk() throws Exception {
        when(setFxCostFlowConfig.set(any(SetFxCostFlowConfigCommand.class)))
                .thenReturn(new FxCostFlowConfigView(
                        CostFlowMethod.FIFO, "operator-7", Instant.parse("2026-02-01T10:00:00Z")));

        mockMvc.perform(put("/api/finance/ledger/settlements/cost-flow-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"FIFO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("FIFO"))
                .andExpect(jsonPath("$.data.updatedBy").value("operator-7"));
    }

    @Test
    @DisplayName("PUT /cost-flow-config unknown method (LIFO) → 400 VALIDATION_ERROR")
    void putCostFlowConfigInvalidMethod() throws Exception {
        when(setFxCostFlowConfig.set(any(SetFxCostFlowConfigCommand.class)))
                .thenThrow(new CostFlowMethodInvalidException(
                        "method must be one of WEIGHTED_AVERAGE, FIFO — got: LIFO"));

        mockMvc.perform(put("/api/finance/ledger/settlements/cost-flow-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"LIFO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- 20th increment: FX position lots read (TASK-FIN-BE-028) ----

    @Test
    @DisplayName("GET /{account}/USD/lots → 200 with lots and summary, money as strings")
    void getPositionLotsReturnsTwoLots() throws Exception {
        Instant acq1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant acq2 = Instant.parse("2026-01-02T00:00:00Z");
        FxPositionLotView lot1 = new FxPositionLotView(
                "lot-1", "USD", acq1, 1L,
                1_000L, 1_000L, 1_300_000L, 1_300_000L, "entry-1");
        FxPositionLotView lot2 = new FxPositionLotView(
                "lot-2", "USD", acq2, 2L,
                500L, 500L, 700_000L, 700_000L, "entry-2");
        FxPositionLotsView view = new FxPositionLotsView(
                java.util.List.of(lot1, lot2), 1_500L, 2_000_000L, 2);

        when(getFxPositionLots.get("finance", "FX_LOTS_WALLET", Currency.USD))
                .thenReturn(view);

        mockMvc.perform(get("/api/finance/ledger/settlements/FX_LOTS_WALLET/USD/lots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lotCount").value(2))
                .andExpect(jsonPath("$.data.totalRemainingForeignMinor").value("1500"))
                .andExpect(jsonPath("$.data.totalCarryingBaseMinor").value("2000000"))
                .andExpect(jsonPath("$.data.lots.length()").value(2))
                .andExpect(jsonPath("$.data.lots[0].lotId").value("lot-1"))
                .andExpect(jsonPath("$.data.lots[0].currency").value("USD"))
                .andExpect(jsonPath("$.data.lots[0].originalForeignMinor").value("1000"))
                .andExpect(jsonPath("$.data.lots[0].remainingForeignMinor").value("1000"))
                .andExpect(jsonPath("$.data.lots[0].originalBaseMinor").value("1300000"))
                .andExpect(jsonPath("$.data.lots[0].carryingBaseMinor").value("1300000"))
                .andExpect(jsonPath("$.data.lots[0].sourceJournalEntryId").value("entry-1"))
                .andExpect(jsonPath("$.data.lots[1].lotId").value("lot-2"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("GET /{account}/USD/lots → tenant from ActorContext (finance), 200")
    void getPositionLotsUsesTenantFromActorContext() throws Exception {
        FxPositionLotsView empty = new FxPositionLotsView(
                java.util.List.of(), 0L, 0L, 0);
        when(getFxPositionLots.get("finance", "ANY_ACCOUNT", Currency.USD))
                .thenReturn(empty);

        mockMvc.perform(get("/api/finance/ledger/settlements/ANY_ACCOUNT/USD/lots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lotCount").value(0))
                .andExpect(jsonPath("$.data.lots").isEmpty())
                .andExpect(jsonPath("$.data.totalRemainingForeignMinor").value("0"))
                .andExpect(jsonPath("$.data.totalCarryingBaseMinor").value("0"));
    }

    @Test
    @DisplayName("GET /{account}/XYZ/lots → 400 VALIDATION_ERROR (unknown currency)")
    void getPositionLotsUnknownCurrency400() throws Exception {
        mockMvc.perform(get("/api/finance/ledger/settlements/FX_LOTS_WALLET/XYZ/lots"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- 21st increment: per-account FX cost-flow override (TASK-FIN-BE-029) ----

    @Test
    @DisplayName("GET /cost-flow-config/accounts → 200 list of overrides for the tenant")
    void listCostFlowAccountOverrides() throws Exception {
        when(getFxCostFlowAccountConfigs.list("finance")).thenReturn(List.of(
                new FxCostFlowAccountConfigView("FX_USD_WALLET", CostFlowMethod.FIFO,
                        "operator-7", Instant.parse("2026-02-01T10:00:00Z")),
                new FxCostFlowAccountConfigView("FX_EUR_WALLET", CostFlowMethod.WEIGHTED_AVERAGE,
                        "operator-7", Instant.parse("2026-02-02T10:00:00Z"))));

        mockMvc.perform(get("/api/finance/ledger/settlements/cost-flow-config/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].ledgerAccountCode").value("FX_USD_WALLET"))
                .andExpect(jsonPath("$.data[0].method").value("FIFO"))
                .andExpect(jsonPath("$.data[1].method").value("WEIGHTED_AVERAGE"));
    }

    @Test
    @DisplayName("GET /cost-flow-config/accounts → 200 empty array when no overrides")
    void listCostFlowAccountOverridesEmpty() throws Exception {
        when(getFxCostFlowAccountConfigs.list("finance")).thenReturn(List.of());

        mockMvc.perform(get("/api/finance/ledger/settlements/cost-flow-config/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("PUT /cost-flow-config/accounts/{code} FIFO → 200 override, audited (updatedBy = actor)")
    void putCostFlowAccountOverrideFifoOk() throws Exception {
        when(setFxCostFlowAccountConfig.set(any(SetFxCostFlowAccountConfigCommand.class)))
                .thenReturn(new FxCostFlowAccountConfigView("FX_USD_WALLET", CostFlowMethod.FIFO,
                        "operator-7", Instant.parse("2026-02-01T10:00:00Z")));

        mockMvc.perform(put("/api/finance/ledger/settlements/cost-flow-config/accounts/FX_USD_WALLET")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"FIFO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerAccountCode").value("FX_USD_WALLET"))
                .andExpect(jsonPath("$.data.method").value("FIFO"))
                .andExpect(jsonPath("$.data.updatedBy").value("operator-7"));
    }

    @Test
    @DisplayName("PUT /cost-flow-config/accounts/{code} unknown method (LIFO) → 400 VALIDATION_ERROR")
    void putCostFlowAccountOverrideInvalidMethod() throws Exception {
        when(setFxCostFlowAccountConfig.set(any(SetFxCostFlowAccountConfigCommand.class)))
                .thenThrow(new CostFlowMethodInvalidException(
                        "method must be one of WEIGHTED_AVERAGE, FIFO — got: LIFO"));

        mockMvc.perform(put("/api/finance/ledger/settlements/cost-flow-config/accounts/FX_USD_WALLET")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"LIFO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("DELETE /cost-flow-config/accounts/{code} existing → 200 cleared:true")
    void deleteCostFlowAccountOverrideCleared() throws Exception {
        when(deleteFxCostFlowAccountConfig.clear("finance", "FX_USD_WALLET", "operator-7"))
                .thenReturn(true);

        mockMvc.perform(delete("/api/finance/ledger/settlements/cost-flow-config/accounts/FX_USD_WALLET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerAccountCode").value("FX_USD_WALLET"))
                .andExpect(jsonPath("$.data.cleared").value(true));
    }

    @Test
    @DisplayName("DELETE /cost-flow-config/accounts/{code} non-existent → 200 cleared:false (idempotent)")
    void deleteCostFlowAccountOverrideNoOp() throws Exception {
        when(deleteFxCostFlowAccountConfig.clear("finance", "FX_UNKNOWN", "operator-7"))
                .thenReturn(false);

        mockMvc.perform(delete("/api/finance/ledger/settlements/cost-flow-config/accounts/FX_UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(false));
    }
}
