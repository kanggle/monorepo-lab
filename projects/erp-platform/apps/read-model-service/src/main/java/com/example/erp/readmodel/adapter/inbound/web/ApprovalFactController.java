package com.example.erp.readmodel.adapter.inbound.web;

import com.example.erp.readmodel.adapter.inbound.web.dto.ApiEnvelope;
import com.example.erp.readmodel.adapter.inbound.web.dto.ApprovalFactResponse;
import com.example.erp.readmodel.application.QueryApprovalFactUseCase;
import com.example.erp.readmodel.application.query.ApprovalFactPage;
import com.example.erp.readmodel.domain.approval.ApprovalFactView;
import com.example.erp.readmodel.domain.approval.ApprovalStatus;
import com.example.erp.readmodel.domain.approval.ApprovalSubjectType;
import com.example.erp.readmodel.presentation.security.OrgScope;
import com.example.erp.readmodel.presentation.security.ReadAuthorizationGate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only REST API serving the integrated approval-fact projection (E5;
 * TASK-ERP-BE-010). Endpoints per read-model-api.md § Approval facts:
 * <ul>
 *   <li>GET {@code /api/erp/read-model/approvals} — paginated list (filters:
 *       status / subjectType / subjectId / approverId / submitterId / page /
 *       size; org_scope subtree filter on the subject's department).</li>
 *   <li>GET {@code /api/erp/read-model/approvals/{approvalRequestId}} — single
 *       fact; 404 {@code MASTERDATA_NOT_FOUND} on a projection miss OR an
 *       out-of-scope subject (no existence leak).</li>
 * </ul>
 * Latest fact only — the authoritative transition history lives on
 * {@code approval-service} ({@code GET /api/erp/approval/requests/{id}}). Every
 * response carries {@code meta.warning}; an unresolved subject adds
 * {@code meta.unresolved}. The READ gate ({@link ReadAuthorizationGate}, E6
 * fail-closed) + the org_scope read filter are reused from the org-view.
 */
@RestController
@RequestMapping("/api/erp/read-model")
@RequiredArgsConstructor
public class ApprovalFactController {

    private static final int MAX_SIZE = 100;

    private final QueryApprovalFactUseCase useCase;
    private final ReadAuthorizationGate readGate;

    @GetMapping("/approvals")
    public ResponseEntity<ApiEnvelope<List<ApprovalFactResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String approverId,
            @RequestParam(required = false) String submitterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {

        readGate.requireRead(jwt);
        validatePaging(page, size);
        ApprovalStatus statusFilter = parseStatus(status);
        ApprovalSubjectType subjectTypeFilter = parseSubjectType(subjectType);

        // org_scope read filter (TASK-ERP-BE-008): null = no narrowing (net-zero).
        List<String> orgScopeRootIds = orgScopeRootIds(jwt);

        ApprovalFactPage result = useCase.list(statusFilter, subjectTypeFilter, subjectId,
                approverId, submitterId, orgScopeRootIds, page, Math.min(size, MAX_SIZE));
        List<ApprovalFactResponse> data = result.content().stream()
                .map(ApprovalFactResponse::from)
                .toList();
        return ResponseEntity.ok(ApiEnvelope.ofList(data, result.page(),
                Math.min(size, MAX_SIZE), result.totalElements()));
    }

    @GetMapping("/approvals/{approvalRequestId}")
    public ResponseEntity<ApiEnvelope<ApprovalFactResponse>> getOne(
            @PathVariable String approvalRequestId,
            @AuthenticationPrincipal Jwt jwt) {

        readGate.requireRead(jwt);

        // org_scope read filter: out-of-scope → 404 (no existence leak).
        List<String> orgScopeRootIds = orgScopeRootIds(jwt);

        ApprovalFactView view = useCase.getOne(approvalRequestId, orgScopeRootIds);
        ApprovalFactResponse data = ApprovalFactResponse.from(view);
        return ResponseEntity.ok(ApiEnvelope.of(data, view.unresolved()));
    }

    private List<String> orgScopeRootIds(Jwt jwt) {
        OrgScope orgScope = readGate.orgScope(jwt);
        return orgScope.isPlatform() ? null : List.copyOf(orgScope.roots());
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    private ApprovalStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        ApprovalStatus parsed = ApprovalStatus.fromOrNull(status);
        if (parsed == null) {
            throw new IllegalArgumentException(
                    "status must be SUBMITTED, APPROVED, REJECTED, or WITHDRAWN");
        }
        return parsed;
    }

    private ApprovalSubjectType parseSubjectType(String subjectType) {
        if (subjectType == null || subjectType.isBlank()) {
            return null;
        }
        ApprovalSubjectType parsed = ApprovalSubjectType.fromOrNull(subjectType);
        if (parsed == null) {
            throw new IllegalArgumentException("subjectType must be DEPARTMENT or EMPLOYEE");
        }
        return parsed;
    }
}
