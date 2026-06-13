package com.example.order.presentation;

import com.example.order.application.dto.AdminOrderDetail;
import com.example.order.application.dto.AdminOrderStatusChangeResult;
import com.example.order.application.dto.AdminOrderSummary;
import com.example.order.application.service.AdminOrderStatusService;
import com.example.order.application.service.OrderQueryService;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.order.presentation.dto.AdminOrderDetailResponse;
import com.example.order.presentation.dto.AdminOrderListResponse;
import com.example.order.presentation.dto.AdminOrderStatusChangeRequest;
import com.example.order.presentation.dto.AdminOrderStatusChangeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Operator-plane order administration (ADR-MONO-031 Phase 1a, TASK-BE-366).
 *
 * <p><b>Authorization is enforced at the ecommerce gateway, not in this
 * controller</b> (extension of the read-leg pattern established in
 * TASK-MONO-243 for {@code AdminProductController.list}, applied here to both
 * read and write endpoints):
 * {@code AccountTypeEnforcementFilter} requires {@code account_type=OPERATOR}
 * for {@code /api/admin/**}, {@code TenantClaimValidator} requires a non-blank
 * {@code tenant_id}, and the repository {@code WHERE tenant_id} chokepoint
 * (Step 2 / M6) enforces tenant isolation. The platform-console OPERATOR
 * presents an IAM OIDC token with no ecommerce-local {@code ADMIN} role claim,
 * so ecommerce-local RBAC is intentionally not applied to any endpoint here
 * — reads ({@code getOrders}/{@code getOrder}) are open to the operator just
 * like the product list, and the write leg ({@code changeStatus}) follows the
 * same write-plane rationale.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderQueryService orderQueryService;
    private final AdminOrderStatusService adminOrderStatusService;

    @GetMapping
    public ResponseEntity<AdminOrderListResponse> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        PageQuery pageQuery = OrderControllerUtils.buildPageQuery(page, size, status);
        PageResult<AdminOrderSummary> result = orderQueryService.getAllOrders(OrderControllerUtils.parseStatus(status), pageQuery);
        return ResponseEntity.ok(AdminOrderListResponse.from(result));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<AdminOrderDetailResponse> getOrder(
            @PathVariable String orderId
    ) {
        AdminOrderDetail detail = orderQueryService.getOrderForAdmin(orderId);
        return ResponseEntity.ok(AdminOrderDetailResponse.from(detail));
    }

    @PostMapping("/{orderId}/status")
    public ResponseEntity<AdminOrderStatusChangeResponse> changeStatus(
            @PathVariable String orderId,
            @Valid @RequestBody AdminOrderStatusChangeRequest request
    ) {
        AdminOrderStatusChangeResult result = adminOrderStatusService.changeStatus(orderId, request.status());
        return ResponseEntity.ok(new AdminOrderStatusChangeResponse(result.orderId(), result.status()));
    }

}
