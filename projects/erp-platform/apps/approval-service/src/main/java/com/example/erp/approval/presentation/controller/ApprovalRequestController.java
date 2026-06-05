package com.example.erp.approval.presentation.controller;

import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.application.ApprovalApplicationService;
import com.example.erp.approval.application.command.Commands.ApproveCommand;
import com.example.erp.approval.application.command.Commands.CreateDraftCommand;
import com.example.erp.approval.application.command.Commands.RejectCommand;
import com.example.erp.approval.application.command.Commands.SubmitCommand;
import com.example.erp.approval.application.command.Commands.WithdrawCommand;
import com.example.erp.approval.application.view.ApprovalRequestView;
import com.example.erp.approval.application.view.ApprovalSummaryView;
import com.example.erp.approval.infrastructure.security.ActorContextResolver;
import com.example.erp.approval.presentation.dto.ApiEnvelope;
import com.example.erp.approval.presentation.dto.ApprovalRequests.ApproveRequest;
import com.example.erp.approval.presentation.dto.ApprovalRequests.CreateRequest;
import com.example.erp.approval.presentation.dto.ApprovalRequests.RejectRequest;
import com.example.erp.approval.presentation.dto.ApprovalRequests.WithdrawRequest;
import com.example.erp.approval.presentation.support.IdempotentExecution;
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

import java.util.List;

/**
 * Approval request REST endpoints (approval-api.md § Endpoints). Controllers
 * never carry {@code @Transactional} — all persistence flows through
 * {@link ApprovalApplicationService}. The 4 transitions + create are wrapped in
 * {@link IdempotentExecution} (Idempotency-Key required; E4 / T1).
 */
@RestController
@RequestMapping("/api/erp/approval/requests")
@RequiredArgsConstructor
public class ApprovalRequestController {

    private final ApprovalApplicationService service;
    private final IdempotentExecution idempotency;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/approval/requests",
                idempotencyKey, req, () -> {
                    ApprovalRequestView v = service.createDraft(new CreateDraftCommand(
                            actor, ApprovalApplicationService.parseSubjectType(req.subjectType()),
                            req.subjectId(), req.title(), req.reason(), req.resolveApproverIds()));
                    return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.of(v));
                });
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<ApprovalSummaryView>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        List<ApprovalSummaryView> data = service.list(actor,
                ApprovalApplicationService.parseStatus(status),
                ApprovalApplicationService.parseRole(role), page, size);
        return ResponseEntity.ok(ApiEnvelope.ofList(data, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<ApprovalRequestView>> detail(@PathVariable String id) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        ApprovalRequestView v = service.detail(id, actor);
        return ResponseEntity.ok(ApiEnvelope.of(v));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submit(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/approval/requests/{id}/submit",
                idempotencyKey, "{}", () -> {
                    ApprovalRequestView v = service.submit(new SubmitCommand(actor, id));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody(required = false) ApproveRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        String reason = req == null ? null : req.reason();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/approval/requests/{id}/approve",
                idempotencyKey, reason, () -> {
                    ApprovalRequestView v = service.approve(new ApproveCommand(actor, id, reason));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RejectRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/approval/requests/{id}/reject",
                idempotencyKey, req, () -> {
                    ApprovalRequestView v = service.reject(new RejectCommand(actor, id, req.reason()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<?> withdraw(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WithdrawRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/approval/requests/{id}/withdraw",
                idempotencyKey, req, () -> {
                    ApprovalRequestView v = service.withdraw(new WithdrawCommand(actor, id, req.reason()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }
}
