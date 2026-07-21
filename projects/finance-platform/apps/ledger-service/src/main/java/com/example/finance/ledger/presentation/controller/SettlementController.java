package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.DeleteFxCostFlowAccountConfigUseCase;
import com.example.finance.ledger.application.GetFxCostFlowAccountConfigsUseCase;
import com.example.finance.ledger.application.GetFxCostFlowConfigUseCase;
import com.example.finance.ledger.application.GetFxPositionLotsUseCase;
import com.example.finance.ledger.application.GetFxRateOverrideUseCase;
import com.example.finance.ledger.application.SetFxCostFlowAccountConfigCommand;
import com.example.finance.ledger.application.SetFxCostFlowAccountConfigUseCase;
import com.example.finance.ledger.application.SetFxCostFlowConfigCommand;
import com.example.finance.ledger.application.SetFxCostFlowConfigUseCase;
import com.example.finance.ledger.application.SetFxRateOverrideCommand;
import com.example.finance.ledger.application.SetFxRateOverrideUseCase;
import com.example.finance.ledger.application.SettleForeignPositionUseCase;
import com.example.finance.ledger.application.SettleForeignPositionUseCase.Result;
import com.example.finance.ledger.application.view.FxCostFlowAccountConfigView;
import com.example.finance.ledger.application.view.FxCostFlowConfigView;
import com.example.finance.ledger.application.view.FxPositionLotsView;
import com.example.finance.ledger.application.view.FxRateOverrideView;
import com.example.finance.ledger.domain.error.LedgerErrors.FxToleranceInvalidException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import com.example.finance.ledger.infrastructure.security.ActorContextResolver;
import com.example.finance.ledger.presentation.dto.ApiEnvelope;
import com.example.finance.ledger.presentation.dto.FxCostFlowAccountConfigDeleteResponse;
import com.example.finance.ledger.presentation.dto.FxCostFlowAccountConfigRequest;
import com.example.finance.ledger.presentation.dto.FxCostFlowAccountConfigResponse;
import com.example.finance.ledger.presentation.dto.FxCostFlowConfigRequest;
import com.example.finance.ledger.presentation.dto.FxCostFlowConfigResponse;
import com.example.finance.ledger.presentation.dto.FxPositionLotsResponse;
import com.example.finance.ledger.presentation.dto.FxRateOverrideRequest;
import com.example.finance.ledger.presentation.dto.FxRateOverrideResponse;
import com.example.finance.ledger.presentation.dto.SettlementRequest;
import com.example.finance.ledger.presentation.dto.SettlementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FX settlement REST endpoint (ledger-api.md § 11, 10th increment — TASK-FIN-BE-016).
 * An operator settles a foreign-currency position at a settlement (spot) rate, removing
 * the position at carrying and booking the realized {@code FX_GAIN}/{@code FX_LOSS}.
 * Carries NO {@code @Transactional} — the use case owns the single guarded write
 * boundary ({@link SettleForeignPositionUseCase} funnels through
 * {@code PostJournalEntryUseCase.post}); the controller never touches JPA repositories
 * directly (architecture.md § boundary rules, F1).
 *
 * <p>A booked settlement returns {@code 201} ({@code settled:true}); a no-op (no
 * position) or an idempotent replay returns {@code 200} ({@code settled:false}).
 * {@code .authenticated()} + the dual-accept tenant gate (parity with revaluation /
 * manual posting — no new scope-authority axis).
 *
 * <p><b>15th increment (TASK-FIN-BE-023)</b>: adds {@code GET} + {@code PUT
 * /cost-flow-config} for the per-tenant FX cost-flow method config (shadow — settlement
 * still uses weighted-average; FIN-BE-025 wires FIFO consumption). Tenant-scoped via
 * {@link ActorContext} exactly like the reconciliation fx-tolerance endpoints.
 *
 * <p><b>20th increment (TASK-FIN-BE-028)</b>: adds {@code GET
 * /{ledgerAccountCode}/{currency}/lots} — read-only surface exposing the tenant's
 * open FX acquisition lots for one {@code (account, currency)} position, ordered
 * {@code (acquired_at, seq)} ASC, plus a summary. Pure read; net-zero; no migration.
 *
 * <p><b>21st increment (TASK-FIN-BE-029)</b>: adds {@code GET /cost-flow-config/accounts},
 * {@code PUT /cost-flow-config/accounts/{ledgerAccountCode}}, and {@code DELETE
 * /cost-flow-config/accounts/{ledgerAccountCode}} — per-account FX cost-flow method overrides
 * layered on the per-tenant default (precedence {@code account override > tenant default >
 * WEIGHTED_AVERAGE}). Tenant-scoped + audited. The settlement path now resolves the effective
 * method via the per-account override ahead of the tenant default (net-zero when no override
 * row exists).
 */
@RestController
@RequestMapping("/api/finance/ledger/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettleForeignPositionUseCase settleForeignPosition;
    private final GetFxCostFlowConfigUseCase getFxCostFlowConfig;
    private final SetFxCostFlowConfigUseCase setFxCostFlowConfig;
    private final GetFxCostFlowAccountConfigsUseCase getFxCostFlowAccountConfigs;
    private final SetFxCostFlowAccountConfigUseCase setFxCostFlowAccountConfig;
    private final DeleteFxCostFlowAccountConfigUseCase deleteFxCostFlowAccountConfig;
    private final GetFxPositionLotsUseCase getFxPositionLots;
    private final GetFxRateOverrideUseCase getFxRateOverride;
    private final SetFxRateOverrideUseCase setFxRateOverride;

    @PostMapping
    public ResponseEntity<ApiEnvelope<SettlementResponse>> settle(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody SettlementRequest request) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        Result result = settleForeignPosition.settle(
                request.toCommand(actor.tenantId(), actor.identity(), idempotencyKey));
        SettlementResponse body = SettlementResponse.from(result);
        HttpStatus status = result.settled() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiEnvelope.of(body));
    }

    /**
     * Read the tenant's FX cost-flow method config (15th increment — TASK-FIN-BE-023).
     * Returns the persisted config or the {@code WEIGHTED_AVERAGE} default when unset.
     * Tenant-scoped.
     */
    @GetMapping("/cost-flow-config")
    public ResponseEntity<ApiEnvelope<FxCostFlowConfigResponse>> getCostFlowConfig() {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        FxCostFlowConfigView view = getFxCostFlowConfig.get(actor.tenantId());
        return ResponseEntity.ok(ApiEnvelope.of(FxCostFlowConfigResponse.from(view)));
    }

    /**
     * Upsert the tenant's FX cost-flow method config (operator config; 15th increment —
     * TASK-FIN-BE-023). Tenant-scoped + audited ({@code updated_by} = the actor identity).
     * Unknown method (e.g. {@code "LIFO"}) → {@code 400 VALIDATION_ERROR}. Shadow:
     * settlement computation is not changed — FIN-BE-025 wires FIFO consumption.
     */
    @PutMapping("/cost-flow-config")
    public ResponseEntity<ApiEnvelope<FxCostFlowConfigResponse>> setCostFlowConfig(
            @RequestBody FxCostFlowConfigRequest request) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        SetFxCostFlowConfigCommand command = new SetFxCostFlowConfigCommand(
                actor.tenantId(), request.method(), actor.identity());
        FxCostFlowConfigView view = setFxCostFlowConfig.set(command);
        return ResponseEntity.ok(ApiEnvelope.of(FxCostFlowConfigResponse.from(view)));
    }

    /**
     * List the tenant's per-account FX cost-flow method overrides (21st increment —
     * TASK-FIN-BE-029). Returns the configured override rows ordered by {@code ledger_account_code}
     * ASC (empty array when none) — an account with no override inherits the tenant default at
     * settlement time and does NOT appear here. Tenant-scoped. The literal {@code /accounts}
     * prefix is matched ahead of the {@code /{ledgerAccountCode}/{currency}/lots} pattern, so
     * there is no route ambiguity.
     */
    @GetMapping("/cost-flow-config/accounts")
    public ResponseEntity<ApiEnvelope<List<FxCostFlowAccountConfigResponse>>> getCostFlowAccountConfigs() {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        List<FxCostFlowAccountConfigResponse> body = getFxCostFlowAccountConfigs.list(actor.tenantId())
                .stream()
                .map(FxCostFlowAccountConfigResponse::from)
                .toList();
        return ResponseEntity.ok(ApiEnvelope.of(body));
    }

    /**
     * Upsert a per-account FX cost-flow method override (operator config; 21st increment —
     * TASK-FIN-BE-029). The override layers on top of the per-tenant default with the precedence
     * {@code account override > tenant default > WEIGHTED_AVERAGE}; it can upgrade OR downgrade.
     * Tenant-scoped + audited ({@code updated_by} = the actor identity). Unknown method (e.g.
     * {@code "LIFO"}) → {@code 400 VALIDATION_ERROR}, nothing persisted. The account is NOT
     * validated to exist (parity with the per-tenant config).
     */
    @PutMapping("/cost-flow-config/accounts/{ledgerAccountCode}")
    public ResponseEntity<ApiEnvelope<FxCostFlowAccountConfigResponse>> setCostFlowAccountConfig(
            @PathVariable String ledgerAccountCode,
            @RequestBody FxCostFlowAccountConfigRequest request) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        SetFxCostFlowAccountConfigCommand command = new SetFxCostFlowAccountConfigCommand(
                actor.tenantId(), ledgerAccountCode, request.method(), actor.identity());
        FxCostFlowAccountConfigView view = setFxCostFlowAccountConfig.set(command);
        return ResponseEntity.ok(ApiEnvelope.of(FxCostFlowAccountConfigResponse.from(view)));
    }

    /**
     * Remove a per-account FX cost-flow method override (21st increment — TASK-FIN-BE-029). The
     * account falls back to the per-tenant default. Idempotent — deleting a non-existent override
     * is a 200 no-op ({@code cleared=false}), not a 404. Tenant-scoped + audited
     * ({@code FX_COST_FLOW_ACCOUNT_METHOD_CLEARED}, written only when a row actually existed).
     */
    @DeleteMapping("/cost-flow-config/accounts/{ledgerAccountCode}")
    public ResponseEntity<ApiEnvelope<FxCostFlowAccountConfigDeleteResponse>> deleteCostFlowAccountConfig(
            @PathVariable String ledgerAccountCode) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        boolean cleared = deleteFxCostFlowAccountConfig.clear(
                actor.tenantId(), ledgerAccountCode, actor.identity());
        return ResponseEntity.ok(ApiEnvelope.of(
                new FxCostFlowAccountConfigDeleteResponse(ledgerAccountCode, cleared)));
    }

    /**
     * Read the tenant's FX contract-rate override for one foreign-currency pair (28th increment —
     * TASK-FIN-BE-042, ADR-002 § 3.1 per-tenant override / 특수 계약환율). Base is the fixed
     * reporting currency (KRW in v1); only the foreign leg is a path variable. Returns the
     * persisted contract rate or the "absent" view ({@code present:false}) when none is set
     * (resolution falls through to the market feed). Tenant-scoped — tenant A's override is
     * invisible to tenant B. The literal {@code /fx-rate-override} prefix is matched ahead of the
     * {@code /{ledgerAccountCode}/{currency}/lots} pattern, so there is no route ambiguity. An
     * unknown currency → {@code 400 VALIDATION_ERROR}.
     */
    @GetMapping("/fx-rate-override/{foreignCurrency}")
    public ResponseEntity<ApiEnvelope<FxRateOverrideResponse>> getRateOverride(
            @PathVariable String foreignCurrency) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        FxRateOverrideView view = getFxRateOverride.get(actor.tenantId(), foreignCurrency);
        return ResponseEntity.ok(ApiEnvelope.of(FxRateOverrideResponse.from(view)));
    }

    /**
     * Upsert the tenant's FX contract-rate override for one foreign-currency pair (operator config;
     * 28th increment — TASK-FIN-BE-042). The contract rate overrides the tenant-agnostic market
     * feed during FX resolution (precedence {@code manual > per-tenant override > feed}); an
     * explicit operator-supplied rate still wins. Tenant-scoped + audited
     * ({@code FX_RATE_OVERRIDE_SET}, {@code updated_by} = the actor identity, last-write-wins). A
     * non-positive / invalid rate or an unknown currency → {@code 400 VALIDATION_ERROR}, nothing
     * persisted. Base is the fixed reporting currency (KRW in v1).
     */
    @PutMapping("/fx-rate-override/{foreignCurrency}")
    public ResponseEntity<ApiEnvelope<FxRateOverrideResponse>> setRateOverride(
            @PathVariable String foreignCurrency,
            @RequestBody FxRateOverrideRequest request) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        SetFxRateOverrideCommand command = new SetFxRateOverrideCommand(
                actor.tenantId(), LedgerReportingCurrency.BASE.code(), foreignCurrency,
                request.parsedRate(), actor.identity());
        FxRateOverrideView view = setFxRateOverride.set(command);
        return ResponseEntity.ok(ApiEnvelope.of(FxRateOverrideResponse.from(view)));
    }

    /**
     * Read the tenant's open FX acquisition lots for one {@code (ledgerAccountCode,
     * currency)} position (20th increment — TASK-FIN-BE-028). Returns the lots ordered
     * {@code (acquired_at, seq)} ASC and a summary (Σ remaining foreign, Σ carrying
     * base, lot count). An empty position returns an empty list + zero summary (200,
     * not 404 — AC-3). An unknown {@code currency} string returns 400
     * {@code VALIDATION_ERROR} (AC-4). No idempotency key — pure read.
     */
    @GetMapping("/{ledgerAccountCode}/{currency}/lots")
    public ResponseEntity<ApiEnvelope<FxPositionLotsResponse>> getPositionLots(
            @PathVariable String ledgerAccountCode,
            @PathVariable String currency) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        Currency resolved = parseCurrencyOrValidationError(currency);
        FxPositionLotsView view = getFxPositionLots.get(actor.tenantId(), ledgerAccountCode, resolved);
        return ResponseEntity.ok(ApiEnvelope.of(FxPositionLotsResponse.from(view)));
    }

    /**
     * Parse the currency path variable to the supported {@link Currency} enum, mapping
     * an unsupported/unknown code to {@code 400 VALIDATION_ERROR} (AC-4 — consistent
     * with the {@link com.example.finance.ledger.domain.error.LedgerErrors.CostFlowMethodInvalidException}
     * pattern used by the cost-flow-config PUT). The existing
     * {@link Currency.UnsupportedCurrencyException} from {@link Currency#of} would map
     * to 422 {@code CURRENCY_MISMATCH} via the global handler — wrapping it here gives
     * the read-path the correct 400 {@code VALIDATION_ERROR} semantics (a path variable
     * that cannot be parsed is a client input error, not a domain currency-mismatch).
     */
    private static Currency parseCurrencyOrValidationError(String currencyCode) {
        return Currency.ofOrThrow(currencyCode, c -> new FxToleranceInvalidException(
                "unknown currency: " + c + " — supported: KRW, USD, EUR, JPY"));
    }
}
