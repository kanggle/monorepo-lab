package com.example.erp.masterdata.presentation.controller;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.CreateCostCenterCommand;
import com.example.erp.masterdata.application.command.Commands.RetireCostCenterCommand;
import com.example.erp.masterdata.application.command.Commands.UpdateCostCenterCommand;
import com.example.erp.masterdata.application.view.CostCenterView;
import com.example.erp.masterdata.infrastructure.security.ActorContextResolver;
import com.example.erp.masterdata.presentation.dto.ApiEnvelope;
import com.example.erp.masterdata.presentation.dto.CostCenterRequests.CreateCostCenterRequest;
import com.example.erp.masterdata.presentation.dto.CostCenterRequests.RetireCostCenterRequest;
import com.example.erp.masterdata.presentation.dto.CostCenterRequests.UpdateCostCenterRequest;
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
import java.util.Map;

@RestController
@RequestMapping("/api/erp/masterdata/cost-centers")
@RequiredArgsConstructor
public class CostCenterController {

    private final MasterdataApplicationService service;
    private final IdempotentExecution idempotency;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateCostCenterRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/cost-centers",
                idempotencyKey, req, () -> {
                    CostCenterView v = service.createCostCenter(new CreateCostCenterCommand(
                            actor, req.code(), req.name(), req.departmentId(), req.effectiveFrom()));
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(ApiEnvelope.of(v));
                });
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<CostCenterView>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        List<CostCenterView> data = service.listCostCenters(actor, page, size);
        Map<String, Object> meta = Map.of("page", page, "size", size,
                "totalElements", data.size());
        return ResponseEntity.ok(ApiEnvelope.of(data, new java.util.LinkedHashMap<>(meta)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<CostCenterView>> get(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate asOf) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        CostCenterView v = service.getCostCenter(id, actor, asOf);
        return ResponseEntity.ok(ApiEnvelope.of(v));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UpdateCostCenterRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "PATCH /api/erp/masterdata/cost-centers/{id}",
                idempotencyKey, req, () -> {
                    CostCenterView v = service.updateCostCenter(new UpdateCostCenterCommand(
                            actor, id, req.name(), req.departmentId(), req.effectiveFrom()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<?> retire(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RetireCostCenterRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/cost-centers/{id}/retire",
                idempotencyKey, req, () -> {
                    CostCenterView v = service.retireCostCenter(new RetireCostCenterCommand(
                            actor, id, req.reason()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }
}
