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
import com.example.web.exception.AccessDeniedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final OrderQueryService orderQueryService;
    private final AdminOrderStatusService adminOrderStatusService;

    @GetMapping
    public ResponseEntity<AdminOrderListResponse> getOrders(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        validateAdminRole(userRole);
        PageQuery pageQuery = OrderControllerUtils.buildPageQuery(page, size, status);
        PageResult<AdminOrderSummary> result = orderQueryService.getAllOrders(OrderControllerUtils.parseStatus(status), pageQuery);
        return ResponseEntity.ok(AdminOrderListResponse.from(result));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<AdminOrderDetailResponse> getOrder(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String orderId
    ) {
        validateAdminRole(userRole);
        AdminOrderDetail detail = orderQueryService.getOrderForAdmin(orderId);
        return ResponseEntity.ok(AdminOrderDetailResponse.from(detail));
    }

    @PostMapping("/{orderId}/status")
    public ResponseEntity<AdminOrderStatusChangeResponse> changeStatus(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String orderId,
            @Valid @RequestBody AdminOrderStatusChangeRequest request
    ) {
        validateAdminRole(userRole);
        AdminOrderStatusChangeResult result = adminOrderStatusService.changeStatus(orderId, request.status());
        return ResponseEntity.ok(new AdminOrderStatusChangeResponse(result.orderId(), result.status()));
    }

    private void validateAdminRole(String userRole) {
        if (!ROLE_ADMIN.equalsIgnoreCase(userRole)) {
            throw new AccessDeniedException();
        }
    }

}
