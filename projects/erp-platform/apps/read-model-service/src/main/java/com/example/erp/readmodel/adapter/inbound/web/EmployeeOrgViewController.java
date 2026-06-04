package com.example.erp.readmodel.adapter.inbound.web;

import com.example.erp.readmodel.adapter.inbound.web.dto.ApiEnvelope;
import com.example.erp.readmodel.adapter.inbound.web.dto.EmployeeOrgViewResponse;
import com.example.erp.readmodel.application.QueryEmployeeOrgViewUseCase;
import com.example.erp.readmodel.application.query.EmployeeOrgViewPage;
import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.orgview.EmployeeOrgView;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only REST API serving the integrated employee org-view (E5). Endpoints
 * per read-model-api.md:
 * <ul>
 *   <li>GET {@code /api/erp/read-model/employees} — paginated list
 *       ({@code page} / {@code size} / {@code asOf} / {@code departmentId}
 *       subtree / {@code status}).</li>
 *   <li>GET {@code /api/erp/read-model/employees/{id}} — single org-view;
 *       404 {@code MASTERDATA_NOT_FOUND} on a projection miss.</li>
 * </ul>
 * Every response carries {@code meta.warning} (eventually-consistent); an
 * unresolved reference adds {@code meta.unresolved}. The tenant gate is enforced
 * by the decode validator + {@code TenantClaimEnforcer} filter; the READ gate is
 * enforced here via {@link ReadAuthorizationGate} (E6 fail-closed).
 */
@RestController
@RequestMapping("/api/erp/read-model")
@RequiredArgsConstructor
public class EmployeeOrgViewController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final QueryEmployeeOrgViewUseCase useCase;
    private final ReadAuthorizationGate readGate;

    @GetMapping("/employees")
    public ResponseEntity<ApiEnvelope<List<EmployeeOrgViewResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String asOf,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false, defaultValue = "ACTIVE") String status,
            @AuthenticationPrincipal Jwt jwt) {

        readGate.requireRead(jwt);
        validatePaging(page, size);
        parseAsOf(asOf); // validates format (point-in-time parity, E2)
        MasterStatus statusFilter = parseStatus(status);

        EmployeeOrgViewPage result = useCase.list(statusFilter, departmentId, page,
                Math.min(size, MAX_SIZE));
        List<EmployeeOrgViewResponse> data = result.content().stream()
                .map(EmployeeOrgViewResponse::from)
                .toList();
        return ResponseEntity.ok(ApiEnvelope.ofList(data, result.page(),
                Math.min(size, MAX_SIZE), result.totalElements()));
    }

    @GetMapping("/employees/{id}")
    public ResponseEntity<ApiEnvelope<EmployeeOrgViewResponse>> getOne(
            @PathVariable String id,
            @RequestParam(required = false) String asOf,
            @AuthenticationPrincipal Jwt jwt) {

        readGate.requireRead(jwt);
        parseAsOf(asOf);

        EmployeeOrgView view = useCase.getOne(id);
        EmployeeOrgViewResponse data = EmployeeOrgViewResponse.from(view);
        return ResponseEntity.ok(ApiEnvelope.of(data, view.unresolved()));
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    private MasterStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return MasterStatus.ACTIVE;
        }
        try {
            return MasterStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("status must be ACTIVE or RETIRED");
        }
    }

    private LocalDate parseAsOf(String asOf) {
        if (asOf == null || asOf.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(asOf.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("asOf must be an ISO-8601 date (yyyy-MM-dd)");
        }
    }
}
