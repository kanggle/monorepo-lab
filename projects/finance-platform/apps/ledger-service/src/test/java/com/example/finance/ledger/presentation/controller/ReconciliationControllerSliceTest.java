package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.GetFxToleranceUseCase;
import com.example.finance.ledger.application.IngestStatementCommand;
import com.example.finance.ledger.application.IngestStatementUseCase;
import com.example.finance.ledger.application.QueryReconciliationUseCase;
import com.example.finance.ledger.application.ResolveDiscrepancyUseCase;
import com.example.finance.ledger.application.SetFxToleranceCommand;
import com.example.finance.ledger.application.SetFxToleranceUseCase;
import com.example.finance.ledger.application.view.DiscrepancyPageView;
import com.example.finance.ledger.application.view.DiscrepancyView;
import com.example.finance.ledger.application.view.FxToleranceView;
import com.example.finance.ledger.application.view.ReconciliationMatchView;
import com.example.finance.ledger.application.view.StatementView;
import com.example.finance.ledger.domain.error.LedgerErrors.FxToleranceInvalidException;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationAccountInvalidException;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationAlreadyResolvedException;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationDiscrepancyNotFoundException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.domain.reconciliation.DiscrepancyStatus;
import com.example.finance.ledger.domain.reconciliation.DiscrepancyType;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import com.example.finance.ledger.domain.reconciliation.ReconciliationMatch;
import com.example.finance.ledger.domain.reconciliation.ResolutionType;
import com.example.finance.ledger.domain.reconciliation.StatementSource;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link ReconciliationController} + the
 * {@link GlobalExceptionHandler} error envelope (mirrors
 * {@code PeriodControllerSliceTest}). Security filters bypassed; the
 * {@link ActorContext} is placed directly in the {@link SecurityContextHolder}.
 */
@WebMvcTest(ReconciliationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReconciliationControllerSliceTest {

    private static final ActorContext ACTOR =
            new ActorContext("user-1", "finance", java.util.Set.of("finance.read"));
    private static final String CODE = LedgerAccountCodes.CASH_CLEARING;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean IngestStatementUseCase ingestStatement;
    @MockitoBean ResolveDiscrepancyUseCase resolveDiscrepancy;
    @MockitoBean QueryReconciliationUseCase queryReconciliation;
    @MockitoBean GetFxToleranceUseCase getFxTolerance;
    @MockitoBean SetFxToleranceUseCase setFxTolerance;

    @BeforeEach
    void setUp() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(ACTOR, "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static Money krw(long m) {
        return Money.of(m, Currency.KRW);
    }

    private static final String INGEST_BODY = """
            { "ledgerAccountCode": "CASH_CLEARING", "source": "BANK",
              "statementDate": "2026-01-31",
              "lines": [
                { "externalRef": "R1", "money": { "amount": "150000", "currency": "KRW" },
                  "direction": "DEBIT", "valueDate": "2026-01-15" },
                { "externalRef": "R2", "money": { "amount": "70000", "currency": "KRW" },
                  "direction": "DEBIT", "valueDate": "2026-01-16" } ] }
            """;

    @Test
    @DisplayName("POST /statements → 201 with matches + OPEN discrepancies")
    void ingestCreated() throws Exception {
        ReconciliationMatch match = ReconciliationMatch.of("m-1", "finance", "line-1", "R1",
                "entry-a", CODE, krw(150_000), Instant.parse("2026-01-31T00:00:00Z"));
        ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.open("d-1", "finance",
                "stmt-1", CODE, DiscrepancyType.UNMATCHED_EXTERNAL, "R2", null,
                70_000L, 0L, Currency.KRW, Instant.parse("2026-01-31T00:00:00Z"));
        StatementView view = StatementView.of(
                com.example.finance.ledger.domain.reconciliation.ExternalStatement.open(
                        "stmt-1", "finance", CODE, StatementSource.BANK,
                        LocalDate.parse("2026-01-31"), Instant.parse("2026-01-31T00:00:00Z"),
                        List.of()),
                List.of(match), List.of(discrepancy));
        when(ingestStatement.ingest(any(IngestStatementCommand.class))).thenReturn(view);

        mockMvc.perform(post("/api/finance/ledger/reconciliation/statements")
                        .contentType(MediaType.APPLICATION_JSON).content(INGEST_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.statementId").value("stmt-1"))
                .andExpect(jsonPath("$.data.matchedCount").value(1))
                .andExpect(jsonPath("$.data.discrepancyCount").value(1))
                .andExpect(jsonPath("$.data.matches[0].statementLineExternalRef").value("R1"))
                .andExpect(jsonPath("$.data.matches[0].money.amount").value("150000"))
                .andExpect(jsonPath("$.data.discrepancies[0].type").value("UNMATCHED_EXTERNAL"))
                .andExpect(jsonPath("$.data.discrepancies[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.discrepancies[0].expectedMinor").value("70000"))
                .andExpect(jsonPath("$.data.discrepancies[0].actualMinor").value("0"));
    }

    @Test
    @DisplayName("POST /statements on a non-clearing account → 422 RECONCILIATION_ACCOUNT_INVALID")
    void ingestAccountInvalid() throws Exception {
        when(ingestStatement.ingest(any()))
                .thenThrow(new ReconciliationAccountInvalidException("not a clearing account"));

        mockMvc.perform(post("/api/finance/ledger/reconciliation/statements")
                        .contentType(MediaType.APPLICATION_JSON).content(INGEST_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_ACCOUNT_INVALID"));
    }

    @Test
    @DisplayName("POST /discrepancies/{id}/resolve → 200 RESOLVED with resolution")
    void resolveOk() throws Exception {
        ReconciliationDiscrepancy resolved = ReconciliationDiscrepancy.open("d-1", "finance",
                "stmt-1", CODE, DiscrepancyType.UNMATCHED_EXTERNAL, "R2", null,
                70_000L, 0L, Currency.KRW, Instant.parse("2026-01-31T00:00:00Z"));
        resolved.resolve(ResolutionType.WRITTEN_OFF, "bank fee", "user-1",
                Instant.parse("2026-02-01T10:00:00Z"));
        when(resolveDiscrepancy.resolve(eq("d-1"), eq("finance"),
                eq(ResolutionType.WRITTEN_OFF), any(), any()))
                .thenReturn(DiscrepancyView.from(resolved));

        mockMvc.perform(post("/api/finance/ledger/reconciliation/discrepancies/d-1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolutionType\":\"WRITTEN_OFF\",\"note\":\"bank fee\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolution.resolutionType").value("WRITTEN_OFF"))
                .andExpect(jsonPath("$.data.resolution.resolvedBy").value("user-1"));
    }

    @Test
    @DisplayName("POST /discrepancies/{id}/resolve already resolved → 409 RECONCILIATION_ALREADY_RESOLVED")
    void resolveAlreadyResolved() throws Exception {
        when(resolveDiscrepancy.resolve(eq("d-1"), any(), any(), any(), any()))
                .thenThrow(new ReconciliationAlreadyResolvedException("already resolved"));

        mockMvc.perform(post("/api/finance/ledger/reconciliation/discrepancies/d-1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolutionType\":\"ACCEPTED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_ALREADY_RESOLVED"));
    }

    @Test
    @DisplayName("POST /discrepancies/{id}/resolve unknown → 404 RECONCILIATION_DISCREPANCY_NOT_FOUND")
    void resolveNotFound() throws Exception {
        when(resolveDiscrepancy.resolve(eq("missing"), any(), any(), any(), any()))
                .thenThrow(new ReconciliationDiscrepancyNotFoundException("not found"));

        mockMvc.perform(post("/api/finance/ledger/reconciliation/discrepancies/missing/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolutionType\":\"ACCEPTED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_DISCREPANCY_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /discrepancies?status=OPEN → 200 queue with pagination meta")
    void listQueue() throws Exception {
        DiscrepancyView d = DiscrepancyView.from(ReconciliationDiscrepancy.open("d-1", "finance",
                "stmt-1", CODE, DiscrepancyType.UNMATCHED_EXTERNAL, "R2", null,
                70_000L, 0L, Currency.KRW, Instant.parse("2026-01-31T00:00:00Z")));
        when(queryReconciliation.listDiscrepancies(eq("finance"),
                eq(DiscrepancyStatus.OPEN), anyInt(), anyInt()))
                .thenReturn(new DiscrepancyPageView(List.of(d), 0, 20, 1, 1));

        mockMvc.perform(get("/api/finance/ledger/reconciliation/discrepancies")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].discrepancyId").value("d-1"))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /statements/{id} unknown handled by domain mapping is covered via use case mocks")
    void getStatementOk() throws Exception {
        StatementView view = StatementView.of(
                com.example.finance.ledger.domain.reconciliation.ExternalStatement.open(
                        "stmt-1", "finance", CODE, StatementSource.BANK,
                        LocalDate.parse("2026-01-31"), Instant.parse("2026-01-31T00:00:00Z"),
                        List.of()),
                List.of(), List.of());
        when(queryReconciliation.getStatement("stmt-1", "finance")).thenReturn(view);

        mockMvc.perform(get("/api/finance/ledger/reconciliation/statements/stmt-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statementId").value("stmt-1"))
                .andExpect(jsonPath("$.data.source").value("BANK"));
    }

    // ---- 13th increment: FX tolerance config (TASK-FIN-BE-020) ----

    @Test
    @DisplayName("GET /fx-tolerance unset → 200 EXACT default {0,0}, no audit fields")
    void getFxToleranceDefault() throws Exception {
        when(getFxTolerance.get("finance")).thenReturn(FxToleranceView.exact());

        mockMvc.perform(get("/api/finance/ledger/reconciliation/fx-tolerance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toleranceBps").value(0))
                .andExpect(jsonPath("$.data.floorMinor").value(0))
                .andExpect(jsonPath("$.data.updatedBy").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /fx-tolerance configured → 200 with bps/floor + audit fields")
    void getFxToleranceConfigured() throws Exception {
        when(getFxTolerance.get("finance")).thenReturn(new FxToleranceView(
                100, 50L, "user-1", Instant.parse("2026-02-01T10:00:00Z")));

        mockMvc.perform(get("/api/finance/ledger/reconciliation/fx-tolerance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toleranceBps").value(100))
                .andExpect(jsonPath("$.data.floorMinor").value(50))
                .andExpect(jsonPath("$.data.updatedBy").value("user-1"));
    }

    @Test
    @DisplayName("PUT /fx-tolerance → 200 persisted config, audited (updatedBy = actor)")
    void putFxToleranceOk() throws Exception {
        when(setFxTolerance.set(any(SetFxToleranceCommand.class))).thenReturn(new FxToleranceView(
                100, 50L, "user-1", Instant.parse("2026-02-01T10:00:00Z")));

        mockMvc.perform(put("/api/finance/ledger/reconciliation/fx-tolerance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toleranceBps\":100,\"floorMinor\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toleranceBps").value(100))
                .andExpect(jsonPath("$.data.floorMinor").value(50))
                .andExpect(jsonPath("$.data.updatedBy").value("user-1"));
    }

    @Test
    @DisplayName("PUT /fx-tolerance negative bps → 400 VALIDATION_ERROR")
    void putFxToleranceNegative() throws Exception {
        when(setFxTolerance.set(any(SetFxToleranceCommand.class)))
                .thenThrow(new FxToleranceInvalidException("toleranceBps must be >= 0: -1"));

        mockMvc.perform(put("/api/finance/ledger/reconciliation/fx-tolerance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toleranceBps\":-1,\"floorMinor\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
