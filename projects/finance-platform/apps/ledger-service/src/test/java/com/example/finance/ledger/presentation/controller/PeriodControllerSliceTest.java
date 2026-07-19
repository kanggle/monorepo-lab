package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.CloseAccountingPeriodUseCase;
import com.example.finance.ledger.application.OpenAccountingPeriodUseCase;
import com.example.finance.ledger.application.QueryAccountingPeriodUseCase;
import com.example.finance.ledger.application.view.AccountingPeriodView;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodAlreadyClosedException;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodInvalidWindowException;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodOverlapException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.PeriodAccountTotal;
import com.example.finance.ledger.domain.period.PeriodBalanceSnapshot;
import com.example.finance.ledger.domain.period.PeriodStatus;
import com.example.finance.ledger.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link PeriodController} + the
 * {@link GlobalExceptionHandler} error envelope (mirrors
 * {@code LedgerControllerSliceTest}). Security filters bypassed; the
 * {@link ActorContext} is placed directly in the {@link SecurityContextHolder}.
 */
@WebMvcTest(PeriodController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PeriodControllerSliceTest extends AbstractLedgerControllerSliceTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-02-01T00:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean OpenAccountingPeriodUseCase openPeriod;
    @MockitoBean CloseAccountingPeriodUseCase closePeriod;
    @MockitoBean QueryAccountingPeriodUseCase queryPeriod;

    @Override
    protected ActorContext actor() {
        return new ActorContext("user-1", "finance", java.util.Set.of("finance.read"));
    }

    private static Money krw(long m) {
        return Money.of(m, Currency.KRW);
    }

    @Test
    @DisplayName("POST /periods → 201 OPEN")
    void openCreated() throws Exception {
        when(openPeriod.open(eq("finance"), eq(FROM), eq(TO), anyString()))
                .thenReturn(AccountingPeriod.open("p-1", "finance", FROM, TO));

        mockMvc.perform(post("/api/finance/ledger/periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2026-01-01T00:00:00Z\",\"to\":\"2026-02-01T00:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.periodId").value("p-1"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.entryCount").doesNotExist())
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("POST /periods from>=to → 422 ACCOUNTING_PERIOD_INVALID_WINDOW")
    void openInvalidWindow() throws Exception {
        when(openPeriod.open(any(), any(), any(), anyString()))
                .thenThrow(new AccountingPeriodInvalidWindowException("from >= to"));

        mockMvc.perform(post("/api/finance/ledger/periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2026-02-01T00:00:00Z\",\"to\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNTING_PERIOD_INVALID_WINDOW"));
    }

    @Test
    @DisplayName("POST /periods overlapping → 422 ACCOUNTING_PERIOD_OVERLAP")
    void openOverlap() throws Exception {
        when(openPeriod.open(any(), any(), any(), anyString()))
                .thenThrow(new AccountingPeriodOverlapException("overlaps"));

        mockMvc.perform(post("/api/finance/ledger/periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2026-01-01T00:00:00Z\",\"to\":\"2026-02-01T00:00:00Z\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNTING_PERIOD_OVERLAP"));
    }

    @Test
    @DisplayName("POST /periods/{id}/close → 200 CLOSED with snapshot")
    void closeOk() throws Exception {
        AccountingPeriod period = AccountingPeriod.open("p-1", "finance", FROM, TO);
        period.close(Instant.parse("2026-02-01T01:00:00Z"), "user-1", 2L);
        PeriodBalanceSnapshot snapshot = PeriodBalanceSnapshot.of(List.of(
                new PeriodAccountTotal("CASH_CLEARING", krw(150_000), krw(0)),
                new PeriodAccountTotal("CUSTOMER_WALLET:acc-1", krw(0), krw(150_000))),
                Currency.KRW);
        when(closePeriod.close(eq("p-1"), eq("finance"), anyString()))
                .thenReturn(AccountingPeriodView.detail(period, snapshot));

        mockMvc.perform(post("/api/finance/ledger/periods/p-1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.entryCount").value(2))
                .andExpect(jsonPath("$.data.closedBy").value("user-1"))
                .andExpect(jsonPath("$.data.snapshot.inBalance").value(true))
                .andExpect(jsonPath("$.data.snapshot.grandDebitTotal.amount").value("150000"))
                .andExpect(jsonPath("$.data.snapshot.grandCreditTotal.amount").value("150000"))
                .andExpect(jsonPath("$.data.snapshot.accounts[0].ledgerAccountCode").value("CASH_CLEARING"));
    }

    @Test
    @DisplayName("POST /periods/{id}/close unknown → 404 ACCOUNTING_PERIOD_NOT_FOUND")
    void closeNotFound() throws Exception {
        when(closePeriod.close(eq("missing"), anyString(), anyString()))
                .thenThrow(new AccountingPeriodNotFoundException("not found"));

        mockMvc.perform(post("/api/finance/ledger/periods/missing/close"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNTING_PERIOD_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /periods/{id}/close already closed → 409 ACCOUNTING_PERIOD_ALREADY_CLOSED")
    void closeAlreadyClosed() throws Exception {
        when(closePeriod.close(eq("p-1"), anyString(), anyString()))
                .thenThrow(new AccountingPeriodAlreadyClosedException("already closed"));

        mockMvc.perform(post("/api/finance/ledger/periods/p-1/close"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNTING_PERIOD_ALREADY_CLOSED"));
    }

    @Test
    @DisplayName("GET /periods → 200 list with pagination meta (no snapshot)")
    void listOk() throws Exception {
        when(queryPeriod.listPeriods("finance")).thenReturn(List.of(
                AccountingPeriodView.summary(AccountingPeriod.open("p-1", "finance", FROM, TO))));

        mockMvc.perform(get("/api/finance/ledger/periods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].periodId").value("p-1"))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /periods/{id} OPEN → 200 detail without snapshot")
    void getOpenNoSnapshot() throws Exception {
        when(queryPeriod.getPeriod("p-1", "finance")).thenReturn(
                AccountingPeriodView.detail(AccountingPeriod.open("p-1", "finance", FROM, TO), null));

        mockMvc.perform(get("/api/finance/ledger/periods/p-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodId").value("p-1"))
                .andExpect(jsonPath("$.data.status").value(PeriodStatus.OPEN.name()))
                .andExpect(jsonPath("$.data.snapshot").doesNotExist());
    }

    @Test
    @DisplayName("GET /periods/{id} unknown → 404 ACCOUNTING_PERIOD_NOT_FOUND")
    void getNotFound() throws Exception {
        when(queryPeriod.getPeriod(Mockito.eq("missing"), anyString()))
                .thenThrow(new AccountingPeriodNotFoundException("not found"));

        mockMvc.perform(get("/api/finance/ledger/periods/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNTING_PERIOD_NOT_FOUND"));
    }
}
