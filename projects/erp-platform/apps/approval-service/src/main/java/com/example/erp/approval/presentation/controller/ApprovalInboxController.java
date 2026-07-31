package com.example.erp.approval.presentation.controller;

import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.application.ApprovalApplicationService;
import com.example.common.page.PageResult;
import com.example.erp.approval.application.view.ApprovalSummaryView;
import com.example.security.servlet.actor.ActorContextResolver;
import com.example.erp.approval.presentation.dto.ApiEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Approval inbox endpoint (approval-api.md § GET /inbox) — the current
 * approver's pending {@code SUBMITTED} queue (basic; v2 adds filtering /
 * delegation views).
 */
@RestController
@RequestMapping("/api/erp/approval/inbox")
@RequiredArgsConstructor
public class ApprovalInboxController {

    private final ApprovalApplicationService service;

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<ApprovalSummaryView>>> inbox(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        PageResult<ApprovalSummaryView> result = service.inbox(actor, page, size);
        // page/size/totalPages sourced from the result object (not the raw request) — guaranteed
        // to agree since the repository echoes the exact page/size it was called with (AC-4).
        return ResponseEntity.ok(ApiEnvelope.ofList(result.content(), result.page(), result.size(),
                result.totalElements(), result.totalPages()));
    }
}
