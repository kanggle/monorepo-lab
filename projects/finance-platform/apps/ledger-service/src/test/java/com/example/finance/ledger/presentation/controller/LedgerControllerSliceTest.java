package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.QueryLedgerUseCase;
import com.example.finance.ledger.application.view.AccountLinePageView;
import com.example.finance.ledger.application.view.AccountLineView;
import com.example.finance.ledger.application.view.JournalEntryView;
import com.example.finance.ledger.application.view.JournalLineView;
import com.example.finance.ledger.application.view.LedgerAccountBalanceView;
import com.example.finance.ledger.application.view.TrialBalanceView;
import com.example.finance.ledger.domain.account.LedgerAccountType;
import com.example.finance.ledger.domain.account.NormalSide;
import com.example.finance.ledger.domain.error.LedgerErrors.JournalEntryNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerAccountNotFoundException;
import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link LedgerController} + the
 * {@link GlobalExceptionHandler} error envelope. Security filters are bypassed
 * ({@code addFilters = false}); the {@link ActorContext} is placed directly in
 * the {@link SecurityContextHolder}.
 */
@WebMvcTest(LedgerController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class LedgerControllerSliceTest {

    private static final ActorContext ACTOR =
            new ActorContext("user-1", "finance", java.util.Set.of("finance.read"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QueryLedgerUseCase queryLedger;

    @BeforeEach
    void setUp() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(ACTOR, "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static Money krw(long m) {
        return Money.of(m, Currency.KRW);
    }

    @Test
    @DisplayName("GET /entries/{id} → 200 with lines + balanced")
    void getEntry() throws Exception {
        JournalEntryView view = new JournalEntryView(
                "e-1", Instant.now(), "TRANSACTION", "txn-1", "evt-1", null,
                List.of(
                        new JournalLineView("CASH_CLEARING", EntryDirection.DEBIT, krw(150_000),
                                java.math.BigDecimal.ONE, krw(150_000)),
                        new JournalLineView("CUSTOMER_WALLET:acc-1", EntryDirection.CREDIT, krw(150_000),
                                java.math.BigDecimal.ONE, krw(150_000))),
                true);
        when(queryLedger.getEntry("e-1", "finance")).thenReturn(view);

        mockMvc.perform(get("/api/finance/ledger/entries/e-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryId").value("e-1"))
                .andExpect(jsonPath("$.data.balanced").value(true))
                .andExpect(jsonPath("$.data.lines[0].ledgerAccountCode").value("CASH_CLEARING"))
                .andExpect(jsonPath("$.data.lines[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.data.lines[0].money.amount").value("150000"))
                .andExpect(jsonPath("$.data.lines[0].money.currency").value("KRW"))
                .andExpect(jsonPath("$.data.lines[0].exchangeRate").value("1"))
                .andExpect(jsonPath("$.data.lines[0].baseAmount.amount").value("150000"))
                .andExpect(jsonPath("$.data.lines[0].baseAmount.currency").value("KRW"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("GET /entries/{id} unknown → 404 JOURNAL_ENTRY_NOT_FOUND")
    void getEntryNotFound() throws Exception {
        when(queryLedger.getEntry(Mockito.eq("missing"), Mockito.anyString()))
                .thenThrow(new JournalEntryNotFoundException("journal entry not found: missing"));
        mockMvc.perform(get("/api/finance/ledger/entries/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOURNAL_ENTRY_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /accounts/{code}/entries → 200 paginated")
    void getAccountEntries() throws Exception {
        AccountLinePageView page = new AccountLinePageView(List.of(
                new AccountLineView("e-1", Instant.now(), EntryDirection.CREDIT, krw(150_000))),
                0, 20, 1, 1);
        when(queryLedger.getAccountLines("CASH_CLEARING", "finance", 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/finance/ledger/accounts/CASH_CLEARING/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].entryId").value("e-1"))
                .andExpect(jsonPath("$.data[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /accounts/{code}/balance → 200 with balance + side")
    void getBalance() throws Exception {
        LedgerAccountBalanceView view = new LedgerAccountBalanceView(
                "CUSTOMER_WALLET:acc-1", LedgerAccountType.LIABILITY, NormalSide.CREDIT,
                krw(0), krw(150_000), krw(150_000), NormalSide.CREDIT);
        when(queryLedger.getBalance("CUSTOMER_WALLET:acc-1", "finance")).thenReturn(view);

        mockMvc.perform(get("/api/finance/ledger/accounts/CUSTOMER_WALLET:acc-1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerAccountCode").value("CUSTOMER_WALLET:acc-1"))
                .andExpect(jsonPath("$.data.type").value("LIABILITY"))
                .andExpect(jsonPath("$.data.balance.amount").value("150000"))
                .andExpect(jsonPath("$.data.balanceSide").value("CREDIT"));
    }

    @Test
    @DisplayName("GET /accounts/{code}/balance unknown → 404 LEDGER_ACCOUNT_NOT_FOUND")
    void getBalanceNotFound() throws Exception {
        when(queryLedger.getBalance(Mockito.eq("NOPE"), Mockito.anyString()))
                .thenThrow(new LedgerAccountNotFoundException("ledger account not found: NOPE"));
        mockMvc.perform(get("/api/finance/ledger/accounts/NOPE/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEDGER_ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /trial-balance → 200 with inBalance true")
    void getTrialBalance() throws Exception {
        TrialBalanceView view = new TrialBalanceView(List.of(
                new TrialBalanceView.AccountTotalsView("CASH_CLEARING", krw(150_000), krw(0),
                        krw(150_000), krw(0)),
                new TrialBalanceView.AccountTotalsView("CUSTOMER_WALLET:acc-1", krw(0), krw(150_000),
                        krw(0), krw(150_000))),
                krw(150_000), krw(150_000), krw(150_000), krw(150_000), true);
        when(queryLedger.getTrialBalance("finance")).thenReturn(view);

        mockMvc.perform(get("/api/finance/ledger/trial-balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inBalance").value(true))
                .andExpect(jsonPath("$.data.grandDebitTotal.amount").value("150000"))
                .andExpect(jsonPath("$.data.grandCreditTotal.amount").value("150000"))
                .andExpect(jsonPath("$.data.grandBaseDebitTotal.amount").value("150000"))
                .andExpect(jsonPath("$.data.grandBaseCreditTotal.amount").value("150000"))
                .andExpect(jsonPath("$.data.accounts[0].ledgerAccountCode").value("CASH_CLEARING"))
                .andExpect(jsonPath("$.data.accounts[0].baseDebitTotal.amount").value("150000"));
    }
}
