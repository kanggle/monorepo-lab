package com.example.erp.masterdata.presentation.controller;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.CreateEmployeeCommand;
import com.example.erp.masterdata.application.command.Commands.RetireEmployeeCommand;
import com.example.erp.masterdata.application.command.Commands.UpdateEmployeeCommand;
import com.example.common.page.PageResult;
import com.example.erp.masterdata.application.view.EmployeeView;
import com.example.erp.masterdata.domain.employee.repository.EmployeeListFilter;
import com.example.security.servlet.actor.ActorContextResolver;
import com.example.erp.masterdata.presentation.dto.ApiEnvelope;
import com.example.erp.masterdata.presentation.dto.EmployeeRequests.CreateEmployeeRequest;
import com.example.erp.masterdata.presentation.dto.EmployeeRequests.RetireEmployeeRequest;
import com.example.erp.masterdata.presentation.dto.EmployeeRequests.UpdateEmployeeRequest;
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
@RequestMapping("/api/erp/masterdata/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final MasterdataApplicationService service;
    private final IdempotentExecution idempotency;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateEmployeeRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/employees",
                idempotencyKey, req, () -> {
                    EmployeeView v = service.createEmployee(new CreateEmployeeCommand(
                            actor, req.employeeNumber(), req.name(), req.departmentId(),
                            req.costCenterId(), req.jobGradeId(), req.effectiveFrom()));
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(ApiEnvelope.of(v));
                });
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<EmployeeView>>> list(
            @RequestParam(required = false) LocalDate asOf,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String costCenterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        PageResult<EmployeeView> result = service.listEmployees(actor,
                new EmployeeListFilter(asOf, active, departmentId, costCenterId), page, size);
        // page/size/totalPages sourced from the result object (not the raw request) — guaranteed
        // to agree since the repository echoes the exact page/size it was called with (AC-4).
        return ResponseEntity.ok(ApiEnvelope.ofList(result.content(), result.page(), result.size(),
                result.totalElements(), result.totalPages()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<EmployeeView>> get(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate asOf) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        EmployeeView v = service.getEmployee(id, actor, asOf);
        return ResponseEntity.ok(ApiEnvelope.of(v));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UpdateEmployeeRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        return idempotency.run(actor.tenantId(),
                "PATCH /api/erp/masterdata/employees/{id}",
                idempotencyKey, req, () -> {
                    EmployeeView v = service.updateEmployee(new UpdateEmployeeCommand(
                            actor, id, req.name(), req.departmentId(),
                            req.costCenterId(), req.jobGradeId(), req.effectiveFrom()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<?> retire(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RetireEmployeeRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/employees/{id}/retire",
                idempotencyKey, req, () -> {
                    EmployeeView v = service.retireEmployee(new RetireEmployeeCommand(
                            actor, id, req.reason()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }
}
