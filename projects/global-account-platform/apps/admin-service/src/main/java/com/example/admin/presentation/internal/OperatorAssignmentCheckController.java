package com.example.admin.presentation.internal;

import com.example.admin.application.OperatorAssignmentCheckUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2): internal read surface that
 * authorizes the auth-service assume-tenant exchange. auth-service calls this
 * one-shot at issuance time to verify the operator's D1 assignment to the
 * selected customer tenant before minting the domain-facing assumed token.
 *
 * <p>URL prefix: {@code /internal/operator-assignments/check}
 * <p>Authentication: under the {@code @Order(0)} {@code /internal/**}
 * resource-server chain — GAP {@code client_credentials} Bearer JWT (fail-closed;
 * the test/standalone bypass profile populates an authenticated principal).
 * See {@code infrastructure/config/SecurityConfig} (the new {@code /internal/**}
 * chain) — the operator {@code /api/admin/**} chain does NOT match this path.
 *
 * <p>Read-only — no {@code admin_actions} row (mirrors the ADR-014 token-exchange
 * "not audited" rule).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/operator-assignments")
public class OperatorAssignmentCheckController {

    private final OperatorAssignmentCheckUseCase checkUseCase;

    /**
     * GET /internal/operator-assignments/check?oidcSubject=&tenantId=
     *
     * <p>Returns {@code {assigned: true|false}} per the operator's effective
     * tenant scope (legacy home ∪ assignments; {@code '*'} platform → true).
     * Never leaks operator existence vs unassigned beyond the boolean: an unknown
     * subject, a non-ACTIVE operator, and an unassigned tenant all return
     * {@code {assigned:false}} with HTTP 200.
     */
    @GetMapping("/check")
    public ResponseEntity<AssignmentCheckResponse> check(
            @RequestParam String oidcSubject,
            @RequestParam String tenantId) {
        boolean assigned = checkUseCase.isAssigned(oidcSubject, tenantId);
        return ResponseEntity.ok(new AssignmentCheckResponse(assigned));
    }

    /** {@code {"assigned": true|false}} — TASK-BE-327 assignment-check response. */
    public record AssignmentCheckResponse(boolean assigned) {}
}
