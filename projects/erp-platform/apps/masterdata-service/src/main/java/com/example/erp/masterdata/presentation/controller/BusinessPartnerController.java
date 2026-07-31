package com.example.erp.masterdata.presentation.controller;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.CreateBusinessPartnerCommand;
import com.example.erp.masterdata.application.command.Commands.RetireBusinessPartnerCommand;
import com.example.erp.masterdata.application.command.Commands.UpdateBusinessPartnerCommand;
import com.example.erp.masterdata.application.view.BusinessPartnerView;
import com.example.common.page.PageResult;
import com.example.erp.masterdata.domain.businesspartner.PartnerType;
import com.example.erp.masterdata.domain.businesspartner.repository.BusinessPartnerListFilter;
import com.example.security.servlet.actor.ActorContextResolver;
import com.example.erp.masterdata.presentation.dto.ApiEnvelope;
import com.example.erp.masterdata.presentation.dto.BusinessPartnerRequests.CreateBusinessPartnerRequest;
import com.example.erp.masterdata.presentation.dto.BusinessPartnerRequests.RetireBusinessPartnerRequest;
import com.example.erp.masterdata.presentation.dto.BusinessPartnerRequests.UpdateBusinessPartnerRequest;
import com.example.erp.masterdata.presentation.support.IdempotentExecution;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/erp/masterdata/business-partners")
@RequiredArgsConstructor
public class BusinessPartnerController {

    private final MasterdataApplicationService service;
    private final IdempotentExecution idempotency;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateBusinessPartnerRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/business-partners",
                idempotencyKey, req, () -> {
                    BusinessPartnerView v = service.createBusinessPartner(
                            new CreateBusinessPartnerCommand(actor, req.code(), req.name(),
                                    req.partnerType(),
                                    req.paymentTerms().termDays(),
                                    req.paymentTerms().method(),
                                    req.effectiveFrom()));
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(ApiEnvelope.of(v));
                });
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<BusinessPartnerView>>> list(
            @RequestParam(required = false) LocalDate asOf,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String partnerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        PageResult<BusinessPartnerView> result = service.listBusinessPartners(actor,
                new BusinessPartnerListFilter(asOf, active, parsePartnerType(partnerType)), page, size);
        // page/size/totalPages sourced from the result object (not the raw request) — guaranteed
        // to agree since the repository echoes the exact page/size it was called with (AC-4).
        return ResponseEntity.ok(ApiEnvelope.ofList(result.content(), result.page(), result.size(),
                result.totalElements(), result.totalPages()));
    }

    /**
     * Parse the optional {@code partnerType} filter (case-insensitive, mirroring
     * the create path). Blank/absent → no constraint; an unknown value raises
     * {@link IllegalArgumentException} → 400 VALIDATION_ERROR.
     */
    private static PartnerType parsePartnerType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return PartnerType.valueOf(raw.trim().toUpperCase());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<BusinessPartnerView>> get(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate asOf) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        BusinessPartnerView v = service.getBusinessPartner(id, actor, asOf);
        return ResponseEntity.ok(ApiEnvelope.of(v));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UpdateBusinessPartnerRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        return idempotency.run(actor.tenantId(),
                "PATCH /api/erp/masterdata/business-partners/{id}",
                idempotencyKey, req, () -> {
                    BusinessPartnerView v = service.updateBusinessPartner(
                            new UpdateBusinessPartnerCommand(actor, id, req.name(),
                                    req.partnerType(),
                                    req.paymentTerms() == null ? null : req.paymentTerms().termDays(),
                                    req.paymentTerms() == null ? null : req.paymentTerms().method(),
                                    req.effectiveFrom()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<?> retire(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RetireBusinessPartnerRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/business-partners/{id}/retire",
                idempotencyKey, req, () -> {
                    BusinessPartnerView v = service.retireBusinessPartner(
                            new RetireBusinessPartnerCommand(actor, id, req.reason()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }
}
