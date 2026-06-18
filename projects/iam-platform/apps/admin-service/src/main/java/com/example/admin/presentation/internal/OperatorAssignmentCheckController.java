package com.example.admin.presentation.internal;

import com.example.admin.application.OperatorAssignmentCheckUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
     * GET /internal/operator-assignments/check?oidcSubject=&subjectEmail=&tenantId=
     *
     * <p>Returns {@code {assigned: true|false, orgScope: [...]|null}} per the
     * operator's effective tenant scope (legacy home ∪ assignments; {@code '*'}
     * platform → true). Never leaks operator existence vs unassigned beyond the
     * boolean: an unknown subject, a non-ACTIVE operator, and an unassigned tenant
     * all return {@code {assigned:false}} with HTTP 200.
     *
     * <p>TASK-BE-338 (ADR-MONO-020 D3 amendment): {@code orgScope} is the selected
     * assignment's per-assignment data-scope (department subtree-root ids).
     * A {@code null} orgScope ⟺ {@code ["*"]} = whole tenant (net-zero) — applies
     * when the column is unset, when there is no explicit assignment row (legacy
     * home-tenant / platform-scope), and for every {@code assigned=false} case.
     * Because admin-service serializes with {@code @JsonInclude(NON_NULL)}, a null
     * orgScope is **OMITTED** from the JSON (the field is simply absent — NOT
     * rendered as {@code "orgScope": null}). The field is ADDITIVE: the
     * {@code assigned} verdict + status codes are byte-unchanged from TASK-BE-327;
     * the auth-service {@code AdminAssignmentClient} parses an absent/null/non-list
     * orgScope to {@code null} → {@code TenantClaimTokenCustomizer} injects
     * {@code ["*"]} (graceful net-zero).
     *
     * <p>TASK-MONO-295 (ADR-MONO-040 Phase 2): {@code subjectEmail} is an ADDITIVE,
     * OPTIONAL query param — the operator's login email from the subject token's
     * {@code email} claim, used as the DUAL-KEY legacy fallback. The use case
     * resolves the operator by the account_id {@code oidcSubject} first, then by
     * {@code subjectEmail} (the value {@code admin_operators.oidc_subject} is
     * currently seeded with). {@code required = false} keeps the contract
     * backward-compatible: an older auth-service that omits the param still
     * resolves on {@code oidcSubject} alone (account_id-keyed rows), and the
     * verdict + status codes are byte-unchanged for callers that supply it.
     */
    @GetMapping("/check")
    public ResponseEntity<AssignmentCheckResponse> check(
            @RequestParam String oidcSubject,
            @RequestParam(required = false) String subjectEmail,
            @RequestParam String tenantId) {
        OperatorAssignmentCheckUseCase.Result result =
                checkUseCase.check(oidcSubject, subjectEmail, tenantId);
        return ResponseEntity.ok(new AssignmentCheckResponse(result.assigned(), result.orgScope()));
    }

    /**
     * {@code {"assigned": true|false, "orgScope": [...]|null}} — TASK-BE-327
     * assignment-check response, extended ADDITIVELY by TASK-BE-338 with the
     * selected assignment's {@code orgScope} ({@code null} ⟺ {@code ["*"]}
     * net-zero).
     */
    public record AssignmentCheckResponse(boolean assigned, List<String> orgScope) {}
}
