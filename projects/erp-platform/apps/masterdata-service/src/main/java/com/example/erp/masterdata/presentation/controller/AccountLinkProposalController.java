package com.example.erp.masterdata.presentation.controller;

import com.example.common.page.PageResult;
import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.AcceptAccountLinkCommand;
import com.example.erp.masterdata.application.command.Commands.DeclineAccountLinkCommand;
import com.example.erp.masterdata.application.command.Commands.RevokeAccountLinkCommand;
import com.example.erp.masterdata.application.view.EmployeeAccountLinkProposalView;
import com.example.erp.masterdata.application.view.EmployeeView;
import com.example.erp.masterdata.presentation.dto.AccountLinkRequests.DeclineAccountLinkRequest;
import com.example.erp.masterdata.presentation.dto.AccountLinkRequests.RevokeAccountLinkRequest;
import com.example.erp.masterdata.presentation.dto.ApiEnvelope;
import com.example.erp.masterdata.presentation.support.IdempotentExecution;
import com.example.security.servlet.actor.ActorContextResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.Map;

/**
 * {@code /api/erp/masterdata/account-link-proposals/**} — the account owner's side of the
 * employee ↔ IAM account link (TASK-ERP-BE-044) plus HR's revoke. The employee-scoped side
 * (propose / history / unlink / me / approver-ref) lives on {@link EmployeeController}.
 */
@RestController
@RequestMapping("/api/erp/masterdata/account-link-proposals")
@RequiredArgsConstructor
public class AccountLinkProposalController {

    private final MasterdataApplicationService service;
    private final IdempotentExecution idempotency;

    @GetMapping("/mine")
    public ResponseEntity<ApiEnvelope<List<EmployeeAccountLinkProposalView>>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        PageResult<EmployeeAccountLinkProposalView> result =
                service.listMyPendingLinkProposals(actor, page, size);
        return ResponseEntity.ok(ApiEnvelope.ofList(result.content(), result.page(), result.size(),
                result.totalElements(), result.totalPages()));
    }

    /** Request body is {@code {}} per contract; an absent body is accepted as the same. */
    @PostMapping("/{proposalId}/accept")
    public ResponseEntity<?> accept(
            @PathVariable String proposalId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/account-link-proposals/{proposalId}/accept",
                idempotencyKey, Map.of("proposalId", proposalId), () -> {
                    EmployeeView v = service.acceptAccountLink(
                            new AcceptAccountLinkCommand(actor, proposalId));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    @PostMapping("/{proposalId}/decline")
    public ResponseEntity<?> decline(
            @PathVariable String proposalId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody(required = false) DeclineAccountLinkRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        String reason = req == null ? null : req.reason();
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/account-link-proposals/{proposalId}/decline",
                idempotencyKey, payload(proposalId, reason), () -> {
                    EmployeeAccountLinkProposalView v = service.declineAccountLink(
                            new DeclineAccountLinkCommand(actor, proposalId, reason));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    @PostMapping("/{proposalId}/revoke")
    public ResponseEntity<?> revoke(
            @PathVariable String proposalId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RevokeAccountLinkRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        return idempotency.run(actor.tenantId(),
                "POST /api/erp/masterdata/account-link-proposals/{proposalId}/revoke",
                idempotencyKey, payload(proposalId, req.reason()), () -> {
                    EmployeeAccountLinkProposalView v = service.revokeAccountLink(
                            new RevokeAccountLinkCommand(actor, proposalId, req.reason()));
                    return ResponseEntity.ok(ApiEnvelope.of(v));
                });
    }

    /**
     * Idempotency payload — the path id is part of it: the stored-key scope is
     * {@code (key, endpoint TEMPLATE, tenant)}, so without the id the same key reused against
     * a different proposal would replay the first proposal's response instead of conflicting.
     */
    private static Map<String, Object> payload(String proposalId, String reason) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("proposalId", proposalId);
        m.put("reason", reason);
        return m;
    }
}
