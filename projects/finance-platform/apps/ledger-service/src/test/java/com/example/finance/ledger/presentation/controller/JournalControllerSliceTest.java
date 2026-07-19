package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.PostManualJournalEntryUseCase;
import com.example.finance.ledger.application.PostManualJournalEntryUseCase.Result;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerAccountNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerEntryUnbalancedException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerPeriodClosedException;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.SourceRef;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
 * {@link WebMvcTest} slice for {@link JournalController} + the
 * {@link GlobalExceptionHandler} error envelope (5th increment, TASK-FIN-BE-011).
 * Security filters bypassed; the {@link ActorContext} is placed directly in the
 * {@link SecurityContextHolder}. Proves 201 fresh / 200 replay, the missing-header
 * 400, and the domain error envelopes (422/404).
 */
@WebMvcTest(JournalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class JournalControllerSliceTest extends AbstractLedgerControllerSliceTest {

    private static final Instant POSTED_AT = Instant.parse("2026-06-12T00:00:00Z");
    private static final String CASH = LedgerAccountCodes.CASH_CLEARING;
    private static final String WALLET = LedgerAccountCodes.customerWallet("acc-1");

    private static final String BALANCED_BODY = """
            { "postedAt": "2026-06-12T00:00:00Z",
              "reference": "ADJ-2026-06-CORR-014",
              "memo": "correct mis-posted settlement clearing",
              "lines": [
                { "ledgerAccountCode": "CASH_CLEARING",         "direction": "DEBIT",  "money": { "amount": "50000", "currency": "KRW" } },
                { "ledgerAccountCode": "CUSTOMER_WALLET:acc-1", "direction": "CREDIT", "money": { "amount": "50000", "currency": "KRW" } }
              ] }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean PostManualJournalEntryUseCase postManualEntry;

    @Override
    protected ActorContext actor() {
        return new ActorContext("operator-7", "finance", java.util.Set.of("finance.read"));
    }

    private static JournalEntry manualEntry() {
        Money krw = Money.of(50_000L, Currency.KRW);
        return JournalEntry.post("e-1", "finance", POSTED_AT,
                SourceRef.ofManual("ADJ-2026-06-CORR-014", "manual:ADJ-2026-06-CORR-014"),
                List.of(JournalLine.debit("finance", CASH, krw),
                        JournalLine.credit("finance", WALLET, krw)));
    }

    @Test
    @DisplayName("POST /entries fresh → 201 with the MANUAL source entry shape")
    void freshPostCreated() throws Exception {
        when(postManualEntry.post(any())).thenReturn(new Result(manualEntry(), false));

        mockMvc.perform(post("/api/finance/ledger/entries")
                        .header("Idempotency-Key", "ADJ-2026-06-CORR-014")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BALANCED_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.entryId").value("e-1"))
                .andExpect(jsonPath("$.data.source.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.data.source.sourceTransactionId").value("ADJ-2026-06-CORR-014"))
                .andExpect(jsonPath("$.data.source.sourceEventId").value("manual:ADJ-2026-06-CORR-014"))
                .andExpect(jsonPath("$.data.balanced").value(true))
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].money.amount").value("50000"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("POST /entries replay (same key) → 200 with the original entry")
    void replayOk() throws Exception {
        when(postManualEntry.post(any())).thenReturn(new Result(manualEntry(), true));

        mockMvc.perform(post("/api/finance/ledger/entries")
                        .header("Idempotency-Key", "ADJ-2026-06-CORR-014")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BALANCED_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryId").value("e-1"))
                .andExpect(jsonPath("$.data.source.sourceType").value("MANUAL"));
    }

    @Test
    @DisplayName("POST /entries without Idempotency-Key → 400 IDEMPOTENCY_KEY_REQUIRED")
    void missingKey() throws Exception {
        mockMvc.perform(post("/api/finance/ledger/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BALANCED_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("POST /entries unbalanced → 422 LEDGER_ENTRY_UNBALANCED")
    void unbalanced() throws Exception {
        when(postManualEntry.post(any()))
                .thenThrow(new LedgerEntryUnbalancedException("Σ debit != Σ credit"));

        mockMvc.perform(post("/api/finance/ledger/entries")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BALANCED_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LEDGER_ENTRY_UNBALANCED"));
    }

    @Test
    @DisplayName("POST /entries unknown account → 404 LEDGER_ACCOUNT_NOT_FOUND")
    void unknownAccount() throws Exception {
        when(postManualEntry.post(any()))
                .thenThrow(new LedgerAccountNotFoundException("ledger account does not exist"));

        mockMvc.perform(post("/api/finance/ledger/entries")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BALANCED_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEDGER_ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /entries into a CLOSED period → 422 LEDGER_PERIOD_CLOSED (synchronous)")
    void closedPeriod() throws Exception {
        when(postManualEntry.post(any()))
                .thenThrow(new LedgerPeriodClosedException("posting into a CLOSED period"));

        mockMvc.perform(post("/api/finance/ledger/entries")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BALANCED_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LEDGER_PERIOD_CLOSED"));
    }
}
