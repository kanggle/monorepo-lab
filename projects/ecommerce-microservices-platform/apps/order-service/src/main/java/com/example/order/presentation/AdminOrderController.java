package com.example.order.presentation;

import com.example.order.application.dto.AdminOrderDetail;
import com.example.order.application.dto.AdminOrderStatusChangeResult;
import com.example.order.application.dto.AdminOrderSummary;
import com.example.order.application.dto.OrderInsights;
import com.example.order.application.service.AdminOrderStatusService;
import com.example.order.application.service.OrderQueryService;
import com.example.common.metrics.PerformanceMetrics;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.common.summary.PeriodSummary;
import com.example.common.time.MetricsPeriod;
import com.example.order.presentation.dto.AdminOrderDetailResponse;
import com.example.order.presentation.dto.AdminOrderListResponse;
import com.example.order.presentation.dto.AdminOrderStatusChangeRequest;
import com.example.order.presentation.dto.AdminOrderStatusChangeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Operator-plane order administration (ADR-MONO-031 Phase 1a, TASK-BE-366).
 *
 * <p>🔴 <b>TASK-MONO-795 (ADR-MONO-083 P8) — this controller now re-checks the business role</b>
 * ({@link OrderOperatorAccess}: reads = {@code ECOMMERCE_ORDER_READ ∨ ECOMMERCE_ORDER_WRITE} or the
 * platform wildcard read · status change = {@code ECOMMERCE_ORDER_WRITE}). The paragraph below is the
 * pre-795 history: the gateway is no longer the single admission point — it admits the derived entry role
 * {@code ECOMMERCE_OPERATOR} (any E-commerce business role), and the per-area split is decided here.
 *
 * <p><b>(history) Authorization is enforced at the ecommerce gateway, not in this
 * controller</b> (extension of the read-leg pattern established in
 * TASK-MONO-243 for {@code AdminProductController.list}, applied here to both
 * read and write endpoints):
 * {@code AccountTypeEnforcementFilter} requires {@code roles ∋ ECOMMERCE_OPERATOR}
 * for {@code /api/admin/**}, {@code TenantClaimValidator} requires a non-blank
 * {@code tenant_id}, and the repository {@code WHERE tenant_id} chokepoint
 * (Step 2 / M6) enforces tenant isolation. The platform-console operator
 * carries the {@code ECOMMERCE_OPERATOR} domain role via the ADR-MONO-035 4a assume-tenant
 * derivation (ecommerce-entitled tenant → {@code ECOMMERCE_OPERATOR}); the service applies
 * no additional ecommerce-local RBAC — the gateway is the single admission
 * point (header-trust service). Both the reads ({@code getOrders}/{@code getOrder})
 * and the write leg ({@code changeStatus}) admit uniformly on
 * {@code roles ∋ ECOMMERCE_OPERATOR}. (ADR-MONO-035 4b removed the legacy
 * {@code account_type=OPERATOR} gateway leg.)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderQueryService orderQueryService;
    private final AdminOrderStatusService adminOrderStatusService;

    @GetMapping("/summary")
    public ResponseEntity<PeriodSummary> getOrderSummary(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        OrderOperatorAccess.requireRead(userRole, tenantId);
        return ResponseEntity.ok(orderQueryService.getPeriodSummary());
    }

    /**
     * Overview performance metrics (ADR-MONO-084, TASK-MONO-807) — the shared shape of
     * {@code platform/contracts/performance-metrics-contract.md}. {@code period} is required;
     * a missing or unknown value is {@code 400 INVALID_ORDER_REQUEST} (MetricsPeriod.parse →
     * IllegalArgumentException → GlobalExceptionHandler). Declared {@code required = false} on
     * purpose: Spring's own missing-parameter path answers {@code VALIDATION_ERROR} here
     * ({@code handleMissingParam}), which would give one rule two codes.
     *
     * <p>TASK-MONO-817 — a wildcard tenant ({@code X-Tenant-Id = '*'}) is refused with 403, not answered
     * with zeros ({@link OrderOperatorAccess#requireRead} — since TASK-BE-659 every operator read refuses it).
     */
    @GetMapping("/metrics")
    public ResponseEntity<PerformanceMetrics> getMetrics(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(required = false) String period) {
        OrderOperatorAccess.requireRead(userRole, tenantId);   // refuses '*' too (TASK-MONO-817 · TASK-BE-659)
        return ResponseEntity.ok(orderQueryService.getMetrics(MetricsPeriod.parse(period)));
    }

    /**
     * Top-5 rankings. {@code period} is optional (TASK-MONO-807): with it, only orders paid
     * in that period's current window; without it, every realised sale up to now.
     */
    @GetMapping("/insights")
    public ResponseEntity<OrderInsights> getInsights(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(required = false) String period) {
        OrderOperatorAccess.requireRead(userRole, tenantId);
        return ResponseEntity.ok(orderQueryService.getInsights(
                period == null ? Optional.empty() : Optional.of(MetricsPeriod.parse(period))));
    }

    @GetMapping
    public ResponseEntity<AdminOrderListResponse> getOrders(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        OrderOperatorAccess.requireRead(userRole, tenantId);
        PageQuery pageQuery = OrderControllerUtils.buildPageQuery(page, size, status);
        PageResult<AdminOrderSummary> result = orderQueryService.getAllOrders(OrderControllerUtils.parseStatus(status), pageQuery);
        return ResponseEntity.ok(AdminOrderListResponse.from(result));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<AdminOrderDetailResponse> getOrder(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable String orderId
    ) {
        OrderOperatorAccess.requireRead(userRole, tenantId);
        AdminOrderDetail detail = orderQueryService.getOrderForAdmin(orderId);
        return ResponseEntity.ok(AdminOrderDetailResponse.from(detail));
    }

    @PostMapping("/{orderId}/status")
    public ResponseEntity<AdminOrderStatusChangeResponse> changeStatus(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String orderId,
            @Valid @RequestBody AdminOrderStatusChangeRequest request
    ) {
        OrderOperatorAccess.requireWrite(userRole);
        AdminOrderStatusChangeResult result = adminOrderStatusService.changeStatus(orderId, request.status());
        return ResponseEntity.ok(new AdminOrderStatusChangeResponse(result.orderId(), result.status()));
    }

}
