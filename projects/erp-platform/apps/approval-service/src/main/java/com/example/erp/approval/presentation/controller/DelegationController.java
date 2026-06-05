package com.example.erp.approval.presentation.controller;

import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.application.DelegationApplicationService;
import com.example.erp.approval.application.command.Commands.CreateDelegationCommand;
import com.example.erp.approval.application.command.Commands.RevokeDelegationCommand;
import com.example.erp.approval.application.view.DelegationGrantView;
import com.example.erp.approval.infrastructure.security.ActorContextResolver;
import com.example.erp.approval.presentation.dto.ApiEnvelope;
import com.example.erp.approval.presentation.dto.DelegationRequests.CreateDelegationRequest;
import com.example.erp.approval.presentation.dto.DelegationRequests.RevokeDelegationRequest;
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
 * Delegation grant REST endpoints (approval-api.md § v2.1 amendment — 대결/위임).
 * Controllers never carry {@code @Transactional} — all persistence flows through
 * {@link DelegationApplicationService}. Create + revoke are wrapped in
 * {@link IdempotentExecution} (Idempotency-Key required; E4 / T1).
 */
@RestController
@RequestMapping("/api/erp/approval/delegations")
@RequiredArgsConstructor
public class DelegationController {

    private final DelegationApplicationService service;
    private final IdempotentExecution idempotency;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateDelegationRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/approval/delegations",
                idempotencyKey, req, () -> {
                    DelegationGrantView v = service.createDelegation(new CreateDelegationCommand(
                            actor, req.delegateId(), req.validFrom(), req.validTo(),
                            req.reason()));
                    return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.of(v));
                });
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<?> revoke(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RevokeDelegationRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/approval/delegations/{id}/revoke",
                idempotencyKey, req, () -> {
                    DelegationGrantView v = service.revokeDelegation(
                            new RevokeDelegationCommand(actor, id, req.reason()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<DelegationGrantView>>> list(
            @RequestParam(required = false) String role) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        List<DelegationGrantView> data = service.listDelegations(actor,
                DelegationApplicationService.parseRole(role));
        return ResponseEntity.ok(ApiEnvelope.ofList(data, 0, data.size()));
    }
}
