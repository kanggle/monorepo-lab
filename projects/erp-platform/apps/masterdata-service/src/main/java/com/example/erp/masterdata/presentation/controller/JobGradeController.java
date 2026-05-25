package com.example.erp.masterdata.presentation.controller;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.CreateJobGradeCommand;
import com.example.erp.masterdata.application.command.Commands.RetireJobGradeCommand;
import com.example.erp.masterdata.application.command.Commands.UpdateJobGradeCommand;
import com.example.erp.masterdata.application.view.JobGradeView;
import com.example.erp.masterdata.infrastructure.security.ActorContextResolver;
import com.example.erp.masterdata.presentation.dto.ApiEnvelope;
import com.example.erp.masterdata.presentation.dto.JobGradeRequests.CreateJobGradeRequest;
import com.example.erp.masterdata.presentation.dto.JobGradeRequests.RetireJobGradeRequest;
import com.example.erp.masterdata.presentation.dto.JobGradeRequests.UpdateJobGradeRequest;
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
@RequestMapping("/api/erp/masterdata/job-grades")
@RequiredArgsConstructor
public class JobGradeController {

    private final MasterdataApplicationService service;
    private final IdempotentExecution idempotency;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateJobGradeRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/job-grades",
                idempotencyKey, req, () -> {
                    JobGradeView v = service.createJobGrade(new CreateJobGradeCommand(
                            actor, req.code(), req.name(), req.displayOrder(), req.effectiveFrom()));
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(ApiEnvelope.of(v));
                });
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<JobGradeView>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        List<JobGradeView> data = service.listJobGrades(actor, page, size);
        return ResponseEntity.ok(ApiEnvelope.ofList(data, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<JobGradeView>> get(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate asOf) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        JobGradeView v = service.getJobGrade(id, actor, asOf);
        return ResponseEntity.ok(ApiEnvelope.of(v));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UpdateJobGradeRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "PATCH /api/erp/masterdata/job-grades/{id}",
                idempotencyKey, req, () -> {
                    JobGradeView v = service.updateJobGrade(new UpdateJobGradeCommand(
                            actor, id, req.name(), req.displayOrder(), req.effectiveFrom()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<?> retire(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RetireJobGradeRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/job-grades/{id}/retire",
                idempotencyKey, req, () -> {
                    JobGradeView v = service.retireJobGrade(new RetireJobGradeCommand(
                            actor, id, req.reason()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }
}
