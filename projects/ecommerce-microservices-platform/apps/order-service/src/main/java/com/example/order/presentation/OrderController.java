package com.example.order.presentation;

import com.example.order.application.dto.CancelOrderResult;
import com.example.order.application.dto.OrderDetail;
import com.example.order.application.dto.OrderSummary;
import com.example.order.application.dto.PlaceOrderResult;
import com.example.order.application.service.OrderCancellationService;
import com.example.order.application.service.OrderPlacementService;
import com.example.order.application.service.OrderQueryService;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.order.presentation.dto.CancelOrderResponse;
import com.example.order.presentation.dto.OrderDetailResponse;
import com.example.order.presentation.dto.OrderListResponse;
import com.example.order.presentation.dto.PlaceOrderRequest;
import com.example.order.presentation.dto.PlaceOrderResponse;
import com.example.order.presentation.dto.VerifyPurchaseResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderPlacementService orderPlacementService;
    private final OrderQueryService orderQueryService;
    private final OrderCancellationService orderCancellationService;

    @PostMapping
    public ResponseEntity<PlaceOrderResponse> placeOrder(
            @RequestHeader("X-User-Id") @NotBlank(message = "X-User-Id header is required") String userId,
            @Valid @RequestBody PlaceOrderRequest request
    ) {
        PlaceOrderResult result = orderPlacementService.placeOrder(request.toCommand(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(PlaceOrderResponse.from(result));
    }

    @GetMapping
    public ResponseEntity<OrderListResponse> getOrders(
            @RequestHeader("X-User-Id") @NotBlank(message = "X-User-Id header is required") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        PageQuery pageQuery = OrderControllerUtils.buildPageQuery(page, size, status);
        PageResult<OrderSummary> result = orderQueryService.getOrders(userId, OrderControllerUtils.parseStatus(status), pageQuery);
        return ResponseEntity.ok(OrderListResponse.from(result));
    }

    @GetMapping("/verify-purchase")
    public ResponseEntity<VerifyPurchaseResponse> verifyPurchase(
            @RequestHeader("X-User-Id") @NotBlank(message = "X-User-Id header is required") String userId,
            @RequestParam @NotNull(message = "productId is required") String productId
    ) {
        boolean purchased = orderQueryService.hasUserPurchasedProduct(userId, productId);
        return ResponseEntity.ok(new VerifyPurchaseResponse(purchased));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrder(
            @RequestHeader("X-User-Id") @NotBlank(message = "X-User-Id header is required") String userId,
            @PathVariable String orderId
    ) {
        OrderDetail detail = orderQueryService.getOrder(orderId, userId);
        return ResponseEntity.ok(OrderDetailResponse.from(detail));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<CancelOrderResponse> cancelOrder(
            @RequestHeader("X-User-Id") @NotBlank(message = "X-User-Id header is required") String userId,
            @PathVariable String orderId
    ) {
        CancelOrderResult result = orderCancellationService.cancelOrder(orderId, userId);
        return ResponseEntity.ok(CancelOrderResponse.from(result));
    }
}
