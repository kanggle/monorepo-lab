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
import com.example.finance.ledger.application.view.StatementView;
import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.reconciliation.DiscrepancyStatus;
import com.example.finance.ledger.domain.reconciliation.ResolutionType;
import com.example.finance.ledger.domain.reconciliation.StatementSource;
import com.example.finance.ledger.infrastructure.security.ActorContextResolver;
import com.example.finance.ledger.presentation.dto.ApiEnvelope;
import com.example.finance.ledger.presentation.dto.DiscrepancyResponse;
import com.example.finance.ledger.presentation.dto.FxToleranceRequest;
import com.example.finance.ledger.presentation.dto.FxToleranceResponse;
import com.example.finance.ledger.presentation.dto.IngestStatementRequest;
import com.example.finance.ledger.presentation.dto.ResolveDiscrepancyRequest;
import com.example.finance.ledger.presentation.dto.StatementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconciliation REST endpoints (reconciliation-api.md, 4th increment). Ingest /
 * resolve are {@code .authenticated()} + the dual-accept tenant gate (parity with
 * the period endpoints — no new scope-authority axis). Carries NO
 * {@code @Transactional} — the use cases own the boundary; it never touches JPA
 * repositories directly (architecture.md § boundary rules).
 */
@RestController
@RequestMapping("/api/finance/ledger/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final IngestStatementUseCase ingestStatement;
    private final ResolveDiscrepancyUseCase resolveDiscrepancy;
    private final QueryReconciliationUseCase queryReconciliation;
    private final GetFxToleranceUseCase getFxTolerance;
    private final SetFxToleranceUseCase setFxTolerance;

    @PostMapping("/statements")
    public ResponseEntity<ApiEnvelope<StatementResponse>> ingest(
            @RequestBody IngestStatementRequest request) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        List<IngestStatementCommand.Line> lines = request.lines() == null ? List.of()
                : request.lines().stream()
                        .map(l -> new IngestStatementCommand.Line(
                                l.externalRef(),
                                l.money() == null ? null : l.money().toMoney(),
                                EntryDirection.valueOf(l.direction()),
                                l.valueDate(), l.description(),
                                l.baseAmount() == null ? null : l.baseAmount().toMoney()))
                        .toList();
        IngestStatementCommand command = new IngestStatementCommand(
                actor.tenantId(), request.ledgerAccountCode(),
                StatementSource.valueOf(request.source()), request.statementDate(),
                lines, actor.identity());
        StatementView view = ingestStatement.ingest(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.of(StatementResponse.from(view)));
    }

    @PostMapping("/discrepancies/{id}/resolve")
    public ResponseEntity<ApiEnvelope<DiscrepancyResponse>> resolve(
            @PathVariable String id, @RequestBody ResolveDiscrepancyRequest request) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        DiscrepancyView view = resolveDiscrepancy.resolve(
                id, actor.tenantId(), ResolutionType.valueOf(request.resolutionType()),
                request.note(), actor.identity());
        return ResponseEntity.ok(ApiEnvelope.of(DiscrepancyResponse.from(view)));
    }

    @GetMapping("/statements/{id}")
    public ResponseEntity<ApiEnvelope<StatementResponse>> getStatement(@PathVariable String id) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        StatementView view = queryReconciliation.getStatement(id, actor.tenantId());
        return ResponseEntity.ok(ApiEnvelope.of(StatementResponse.from(view)));
    }

    @GetMapping("/discrepancies")
    public ResponseEntity<ApiEnvelope<List<DiscrepancyResponse>>> listDiscrepancies(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        DiscrepancyStatus statusFilter = status == null || status.isBlank()
                ? null : DiscrepancyStatus.valueOf(status);
        DiscrepancyPageView pageView =
                queryReconciliation.listDiscrepancies(actor.tenantId(), statusFilter, page, size);
        List<DiscrepancyResponse> content = pageView.content().stream()
                .map(DiscrepancyResponse::from).toList();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", pageView.page());
        meta.put("size", pageView.size());
        meta.put("totalElements", pageView.totalElements());
        meta.put("totalPages", pageView.totalPages());
        return ResponseEntity.ok(ApiEnvelope.of(content, meta));
    }

    @GetMapping("/discrepancies/{id}")
    public ResponseEntity<ApiEnvelope<DiscrepancyResponse>> getDiscrepancy(@PathVariable String id) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        DiscrepancyView view = queryReconciliation.getDiscrepancy(id, actor.tenantId());
        return ResponseEntity.ok(ApiEnvelope.of(DiscrepancyResponse.from(view)));
    }

    /**
     * Read the tenant's base-leg FX reconciliation tolerance (13th increment —
     * TASK-FIN-BE-020). Returns the persisted config or the EXACT default
     * {@code { toleranceBps: 0, floorMinor: 0 }} when unset. Tenant-scoped.
     */
    @GetMapping("/fx-tolerance")
    public ResponseEntity<ApiEnvelope<FxToleranceResponse>> getFxTolerance() {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        FxToleranceView view = getFxTolerance.get(actor.tenantId());
        return ResponseEntity.ok(ApiEnvelope.of(FxToleranceResponse.from(view)));
    }

    /**
     * Upsert the tenant's base-leg FX reconciliation tolerance (operator config; 13th
     * increment — TASK-FIN-BE-020). Tenant-scoped + audited ({@code updated_by} = the
     * actor identity). Negative {@code toleranceBps}/{@code floorMinor} →
     * {@code 400 VALIDATION_ERROR}. {@code .authenticated()} + the dual-accept tenant
     * gate (parity with the ingest/resolve mutations; no new scope-authority axis).
     */
    @PutMapping("/fx-tolerance")
    public ResponseEntity<ApiEnvelope<FxToleranceResponse>> setFxTolerance(
            @RequestBody FxToleranceRequest request) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        SetFxToleranceCommand command = new SetFxToleranceCommand(
                actor.tenantId(), request.bpsOrZero(), request.floorOrZero(), actor.identity());
        FxToleranceView view = setFxTolerance.set(command);
        return ResponseEntity.ok(ApiEnvelope.of(FxToleranceResponse.from(view)));
    }

}
