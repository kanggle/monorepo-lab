package com.example.erp.masterdata.presentation.controller;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.CreateDepartmentCommand;
import com.example.erp.masterdata.application.command.Commands.MoveDepartmentParentCommand;
import com.example.erp.masterdata.application.command.Commands.RetireDepartmentCommand;
import com.example.erp.masterdata.application.command.Commands.UpdateDepartmentCommand;
import com.example.erp.masterdata.application.view.DepartmentView;
import com.example.erp.masterdata.infrastructure.security.ActorContextResolver;
import com.example.erp.masterdata.presentation.dto.ApiEnvelope;
import com.example.erp.masterdata.presentation.dto.DepartmentRequests.CreateDepartmentRequest;
import com.example.erp.masterdata.presentation.dto.DepartmentRequests.MoveParentRequest;
import com.example.erp.masterdata.presentation.dto.DepartmentRequests.RetireDepartmentRequest;
import com.example.erp.masterdata.presentation.dto.DepartmentRequests.UpdateDepartmentRequest;
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

/**
 * Department REST endpoints (masterdata-api.md § Department). Controllers
 * never carry {@code @Transactional} — all persistence flows through
 * {@link MasterdataApplicationService}.
 */
@RestController
@RequestMapping("/api/erp/masterdata/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final MasterdataApplicationService service;
    private final IdempotentExecution idempotency;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateDepartmentRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/departments",
                idempotencyKey, req, () -> {
                    DepartmentView v = service.createDepartment(new CreateDepartmentCommand(
                            actor, req.code(), req.name(), req.parentId(), req.effectiveFrom()));
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(ApiEnvelope.of(v));
                });
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<DepartmentView>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        List<DepartmentView> data = service.listDepartments(actor, page, size);
        return ResponseEntity.ok(ApiEnvelope.ofList(data, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<DepartmentView>> get(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate asOf) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        DepartmentView v = service.getDepartment(id, actor, asOf);
        return ResponseEntity.ok(ApiEnvelope.of(v));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UpdateDepartmentRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "PATCH /api/erp/masterdata/departments/{id}",
                idempotencyKey, req, () -> {
                    DepartmentView v = service.updateDepartment(new UpdateDepartmentCommand(
                            actor, id, req.name(), req.effectiveFrom()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<?> retire(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RetireDepartmentRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/departments/{id}/retire",
                idempotencyKey, req, () -> {
                    DepartmentView v = service.retireDepartment(new RetireDepartmentCommand(
                            actor, id, req.reason()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    @PostMapping("/{id}/move-parent")
    public ResponseEntity<?> moveParent(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MoveParentRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/departments/{id}/move-parent",
                idempotencyKey, req, () -> {
                    DepartmentView v = service.moveDepartmentParent(new MoveDepartmentParentCommand(
                            actor, id, req.newParentId(), req.effectiveFrom(), req.reason()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }
}
