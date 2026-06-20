package com.example.settlement.presentation;

import com.example.settlement.application.service.CloseSettlementPeriodUseCase;
import com.example.settlement.application.service.ExecuteSellerPayoutsUseCase;
import com.example.settlement.application.service.OpenSettlementPeriodUseCase;
import com.example.settlement.application.service.QuerySettlementPeriodUseCase;
import com.example.settlement.application.view.PayoutView;
import com.example.settlement.application.view.PeriodView;
import com.example.settlement.domain.tenant.TenantContext;
import com.example.settlement.presentation.dto.OpenPeriodRequest;
import com.example.settlement.presentation.dto.PayoutListResponse;
import com.example.settlement.presentation.dto.PeriodListResponse;
import com.example.settlement.presentation.dto.PeriodResponse;
import com.example.web.exception.AccessDeniedException;
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
 * Operator-plane settlement-period endpoints (settlement-api.md § Period close).
 * Open a period, close it (folding in-window accruals into PENDING {@code seller_payout}
 * rows + emitting {@code settlement.period.closed.v1}), list periods, and execute /
 * read payout rows (TASK-BE-416). All require an operator ({@code X-User-Role ∋ ADMIN})
 * and are tenant-scoped (gateway {@code X-Tenant-Id} via {@link TenantContext}). The
 * use cases own the transaction boundary — the controller never touches JPA directly
 * (architecture.md § boundary).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/settlements/periods")
public class SettlementPeriodController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final OpenSettlementPeriodUseCase openPeriod;
    private final CloseSettlementPeriodUseCase closePeriod;
    private final QuerySettlementPeriodUseCase queryPeriod;
    private final ExecuteSellerPayoutsUseCase executePayouts;

    @PostMapping
    public ResponseEntity<PeriodResponse> open(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @Valid @RequestBody OpenPeriodRequest request) {
        validateAdminRole(userRole);
        PeriodView view = openPeriod.open(TenantContext.currentTenant(), request.from(), request.to());
        return ResponseEntity.status(HttpStatus.CREATED).body(PeriodResponse.summary(view));
    }

    @PostMapping("/{periodId}/close")
    public ResponseEntity<PeriodResponse> close(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String periodId) {
        validateAdminRole(userRole);
        String closedBy = (userId == null || userId.isBlank())
                ? TenantContext.currentTenant() : userId;
        PeriodView view = closePeriod.close(periodId, TenantContext.currentTenant(), closedBy);
        return ResponseEntity.ok(PeriodResponse.detail(view));
    }

    @GetMapping
    public ResponseEntity<PeriodListResponse> list(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateAdminRole(userRole);
        return ResponseEntity.ok(PeriodListResponse.of(
                queryPeriod.listPeriods(TenantContext.currentTenant()), page, size));
    }

    /**
     * GET /api/admin/settlements/periods/{periodId}/payouts — list the period's
     * {@code seller_payout} rows. Tenant-scoped + seller-scope ABAC (same filter as
     * the accrual reads via {@link com.example.settlement.domain.seller.SellerScopeContext}).
     * Cross-tenant / absent period → 404 (settlement-api.md AC-5).
     */
    @GetMapping("/{periodId}/payouts")
    public ResponseEntity<PayoutListResponse> listPayouts(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String periodId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateAdminRole(userRole);
        List<PayoutView> views = executePayouts.list(periodId, TenantContext.currentTenant());
        return ResponseEntity.ok(PayoutListResponse.of(views, page, size));
    }

    /**
     * POST /api/admin/settlements/periods/{periodId}/payouts/execute — execute the
     * simulated {@link com.example.settlement.application.port.SellerPayoutPort} over
     * the period's PENDING payouts (PENDING→PAID|FAILED). Idempotent on
     * {@code (periodId, sellerId)}. Period must be CLOSED (OPEN → 409
     * {@code PERIOD_NOT_CLOSED}). Returns post-execution payout statuses.
     */
    @PostMapping("/{periodId}/payouts/execute")
    public ResponseEntity<PayoutListResponse> executePayouts(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String periodId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateAdminRole(userRole);
        List<PayoutView> views = executePayouts.execute(periodId, TenantContext.currentTenant());
        return ResponseEntity.ok(PayoutListResponse.of(views, page, size));
    }

    private void validateAdminRole(String userRole) {
        if (!hasAdminRole(userRole)) {
            throw new AccessDeniedException();
        }
    }

    private static boolean hasAdminRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return false;
        }
        for (String role : userRole.split(",")) {
            if (ROLE_ADMIN.equalsIgnoreCase(role.trim())) {
                return true;
            }
        }
        return false;
    }
}
