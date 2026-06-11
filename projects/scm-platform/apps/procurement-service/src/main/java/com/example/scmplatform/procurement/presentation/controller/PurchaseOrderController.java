package com.example.scmplatform.procurement.presentation.controller;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.application.command.CancelPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.ConfirmPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.DraftFromSuggestionCommand;
import com.example.scmplatform.procurement.application.command.DraftPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.SubmitPurchaseOrderCommand;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.application.security.ActorContextResolver;
import com.example.scmplatform.procurement.presentation.dto.ApiEnvelope;
import com.example.scmplatform.procurement.presentation.dto.CancelPurchaseOrderRequest;
import com.example.scmplatform.procurement.presentation.dto.DraftFromSuggestionRequest;
import com.example.scmplatform.procurement.presentation.dto.DraftPurchaseOrderRequest;
import com.example.scmplatform.procurement.presentation.dto.PageResponse;
import com.example.scmplatform.procurement.presentation.dto.PurchaseOrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Purchase Order REST endpoints. Per
 * {@code projects/scm-platform/specs/contracts/http/procurement-api.md}.
 *
 * <p>All mutating endpoints require an {@code Idempotency-Key} header
 * (rules/traits/transactional.md T1, Edge Case #1). Missing the header
 * raises {@link org.springframework.web.bind.MissingRequestHeaderException}
 * which the {@link com.example.scmplatform.procurement.presentation.advice.GlobalExceptionHandler}
 * maps to 400 {@code IDEMPOTENCY_KEY_REQUIRED}.
 */
@RestController
@RequestMapping("/api/procurement/po")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderApplicationService service;

    @PostMapping
    public ResponseEntity<ApiEnvelope<PurchaseOrderResponse>> draft(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DraftPurchaseOrderRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        DraftPurchaseOrderCommand cmd = new DraftPurchaseOrderCommand(
                actor,
                req.supplierId(),
                req.currency(),
                req.lines().stream()
                        .map(l -> new DraftPurchaseOrderCommand.Line(
                                l.lineNo(), l.sku(), l.supplierSku(), l.quantity(), l.unitPrice()))
                        .toList()
        );
        PurchaseOrderView view = service.draft(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.of(PurchaseOrderResponse.from(view)));
    }

    /**
     * Additive internal entry (ADR-MONO-027 D5): materialize an approved reorder
     * suggestion into a DRAFT PO. Caller is {@code demand-planning-service}.
     *
     * <p>No {@code Idempotency-Key} header — idempotency is keyed on
     * {@code sourceSuggestionId}. The PO is created in DRAFT only; never
     * auto-SUBMITted.
     */
    @PostMapping("/from-suggestion")
    public ResponseEntity<ApiEnvelope<PurchaseOrderResponse>> draftFromSuggestion(
            @Valid @RequestBody DraftFromSuggestionRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        DraftFromSuggestionCommand cmd = new DraftFromSuggestionCommand(
                actor,
                req.supplierId(),
                req.currency(),
                req.sourceSuggestionId(),
                req.lines().stream()
                        .map(l -> new DraftFromSuggestionCommand.Line(
                                l.lineNo(), l.sku(), l.quantity(), l.unitPriceRef()))
                        .toList()
        );
        PurchaseOrderView view = service.draftFromSuggestion(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.of(PurchaseOrderResponse.from(view)));
    }

    @GetMapping("/{poId}")
    public ResponseEntity<ApiEnvelope<PurchaseOrderResponse>> get(@PathVariable String poId) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return ResponseEntity.ok(
                ApiEnvelope.of(PurchaseOrderResponse.from(service.get(poId, actor))));
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<PageResponse<PurchaseOrderResponse>>> search(
            @RequestParam(required = false) PoStatus status,
            @RequestParam(required = false) String supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        PageResult<PurchaseOrderView> result = service.search(actor, status, supplierId,
                PageQuery.of(page, size, "createdAt", "DESC"));
        return ResponseEntity.ok(ApiEnvelope.of(PageResponse.from(result.map(PurchaseOrderResponse::from))));
    }

    @PostMapping("/{poId}/submit")
    public ResponseEntity<ApiEnvelope<PurchaseOrderResponse>> submit(
            @PathVariable String poId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        PurchaseOrderView view = service.submit(new SubmitPurchaseOrderCommand(actor, poId, idempotencyKey));
        return ResponseEntity.ok(ApiEnvelope.of(PurchaseOrderResponse.from(view)));
    }

    @PostMapping("/{poId}/confirm")
    public ResponseEntity<ApiEnvelope<PurchaseOrderResponse>> confirm(
            @PathVariable String poId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        PurchaseOrderView view = service.confirm(new ConfirmPurchaseOrderCommand(actor, poId));
        return ResponseEntity.ok(ApiEnvelope.of(PurchaseOrderResponse.from(view)));
    }

    @PostMapping("/{poId}/cancel")
    public ResponseEntity<ApiEnvelope<PurchaseOrderResponse>> cancel(
            @PathVariable String poId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody(required = false) CancelPurchaseOrderRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        String reason = req == null ? null : req.reason();
        PurchaseOrderView view = service.cancel(new CancelPurchaseOrderCommand(actor, poId, reason));
        return ResponseEntity.ok(ApiEnvelope.of(PurchaseOrderResponse.from(view)));
    }
}
