package com.example.erp.readmodel.adapter.inbound.web;

import com.example.erp.readmodel.adapter.inbound.web.dto.ApiEnvelope;
import com.example.erp.readmodel.adapter.inbound.web.dto.DelegationFactResponse;
import com.example.erp.readmodel.application.QueryDelegationFactUseCase;
import com.example.erp.readmodel.application.query.DelegationFactPage;
import com.example.erp.readmodel.domain.delegation.DelegationFactProjection;
import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;
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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Read-only REST API serving the integrated delegation-fact projection (E5;
 * TASK-ERP-BE-015). Endpoints per read-model-api.md § Delegation facts:
 * <ul>
 *   <li>GET {@code /api/erp/read-model/delegations} — paginated list (filters:
 *       delegatorId / delegateId / status / activeAt / page / size; org_scope
 *       subtree filter on the delegator's department).</li>
 *   <li>GET {@code /api/erp/read-model/delegations/{grantId}} — single fact; 404
 *       {@code MASTERDATA_NOT_FOUND} on a projection miss OR an out-of-scope
 *       delegator (no existence leak).</li>
 * </ul>
 * Latest fact only — the authoritative grant state + audit history live on
 * {@code approval-service}. The READ gate ({@link ReadAuthorizationGate}, E6
 * fail-closed) + the org_scope read filter are reused from the org-view /
 * approval-fact surfaces.
 */
@RestController
@RequestMapping("/api/erp/read-model")
@RequiredArgsConstructor
public class DelegationFactController {

    private static final int MAX_SIZE = 100;

    private final QueryDelegationFactUseCase useCase;
    private final ReadAuthorizationGate readGate;

    @GetMapping("/delegations")
    public ResponseEntity<ApiEnvelope<List<DelegationFactResponse>>> list(
            @RequestParam(required = false) String delegatorId,
            @RequestParam(required = false) String delegateId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String activeAt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {

        readGate.requireRead(jwt);
        validatePaging(page, size);
        DelegationFactStatus statusFilter = parseStatus(status);
        Instant activeAtFilter = parseInstant(activeAt);

        // org_scope read filter (TASK-ERP-BE-008): null = no narrowing (net-zero).
        List<String> orgScopeRootIds = orgScopeRootIds(jwt);

        DelegationFactPage result = useCase.list(delegatorId, delegateId, statusFilter,
                activeAtFilter, orgScopeRootIds, page, Math.min(size, MAX_SIZE));
        List<DelegationFactResponse> data = result.content().stream()
                .map(DelegationFactResponse::from)
                .toList();
        return ResponseEntity.ok(ApiEnvelope.ofList(data, result.page(),
                Math.min(size, MAX_SIZE), result.totalElements()));
    }

    @GetMapping("/delegations/{grantId}")
    public ResponseEntity<ApiEnvelope<DelegationFactResponse>> getOne(
            @PathVariable String grantId,
            @AuthenticationPrincipal Jwt jwt) {

        readGate.requireRead(jwt);

        // org_scope read filter: out-of-scope → 404 (no existence leak).
        List<String> orgScopeRootIds = orgScopeRootIds(jwt);

        DelegationFactProjection fact = useCase.getOne(grantId, orgScopeRootIds);
        return ResponseEntity.ok(ApiEnvelope.of(DelegationFactResponse.from(fact), List.of()));
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

    private DelegationFactStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DelegationFactStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("status must be ACTIVE or REVOKED");
        }
    }

    private Instant parseInstant(String activeAt) {
        if (activeAt == null || activeAt.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(activeAt.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("activeAt must be an ISO-8601 UTC instant");
        }
    }
}
