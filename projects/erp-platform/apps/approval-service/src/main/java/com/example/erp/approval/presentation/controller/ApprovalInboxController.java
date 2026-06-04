package com.example.erp.approval.presentation.controller;

import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.application.ApprovalApplicationService;
import com.example.erp.approval.application.view.ApprovalSummaryView;
import com.example.erp.approval.infrastructure.security.ActorContextResolver;
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
        ActorContext actor = ActorContextResolver.currentOrThrow();
        List<ApprovalSummaryView> data = service.inbox(actor, page, size);
        return ResponseEntity.ok(ApiEnvelope.ofList(data, page, size));
    }
}
