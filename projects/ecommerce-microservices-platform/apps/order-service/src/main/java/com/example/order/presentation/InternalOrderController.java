package com.example.order.presentation;

import com.example.order.application.dto.CancelOrderResult;
import com.example.order.application.dto.ConfirmPaidStaleResult;
import com.example.order.application.service.OperatorOrderCancellationService;
import com.example.order.application.service.StalePaidOrderConfirmService;
import com.example.order.presentation.dto.ConfirmPaidStaleRequest;
import com.example.order.presentation.dto.ConfirmPaidStaleResponse;
import com.example.order.presentation.dto.OperatorCancelOrderRequest;
import com.example.order.presentation.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal system-command endpoints for order-service (TASK-BE-412).
 *
 * <p>Mounted under the <b>gateway-excluded</b> {@code /api/internal/orders/**} route:
 * the ecommerce gateway routes only {@code /api/orders/**} and {@code /api/admin/orders/**}
 * to order-service, so this route has no external path and is reachable only on the
 * internal service network. A dedicated resource-server security chain
 * ({@code OrderSecurityConfig}) validates the inbound {@code client_credentials}
 * Bearer JWT (JWKS signature + timestamps + issuer + audience), fail-closed — a
 * missing/invalid token never reaches this controller (401 at the filter chain).
 *
 * <p>{@code POST confirm-paid-stale} forward-confirms paid-but-unconfirmed stale orders
 * ({@code PENDING AND payment_id IS NOT NULL} past a threshold) through the same
 * application path the normal saga uses, emitting the standard {@code OrderConfirmed}
 * event so downstream fulfillment fires. It is NOT the user cancel nor the admin status
 * endpoint (both carry ownership/role semantics that do not apply to a server-evaluated
 * batch sweep).
 *
 * <p>{@code POST {orderId}/cancel} is the operator manual-cancel path (TASK-BE-428): the
 * console operator cancels an order (most often a {@code BACKORDERED} one that will never
 * be replenished) without a customer ownership check, reusing the standard
 * {@code OrderCancelled} → refund + coupon-restore fan-out. A non-cancellable order
 * (SHIPPED/DELIVERED/terminal) surfaces {@code 422 ORDER_CANNOT_BE_CANCELLED} from the
 * domain via the {@code GlobalExceptionHandler}; an unknown order surfaces {@code 404}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/orders")
public class InternalOrderController {

    private final StalePaidOrderConfirmService stalePaidOrderConfirmService;
    private final OperatorOrderCancellationService operatorOrderCancellationService;

    @PostMapping("/confirm-paid-stale")
    public ResponseEntity<ConfirmPaidStaleResponse> confirmPaidStale(
            @RequestBody(required = false) ConfirmPaidStaleRequest request) {
        ConfirmPaidStaleRequest body = request != null
                ? request
                : new ConfirmPaidStaleRequest(null, null);

        int olderThanMinutes = body.resolvedOlderThanMinutes();
        int limit = body.resolvedLimit();

        if (olderThanMinutes < ConfirmPaidStaleRequest.MIN_OLDER_THAN_MINUTES) {
            throw new InvalidRequestException(
                    "olderThanMinutes must be >= " + ConfirmPaidStaleRequest.MIN_OLDER_THAN_MINUTES);
        }
        if (limit < ConfirmPaidStaleRequest.MIN_LIMIT || limit > ConfirmPaidStaleRequest.MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between "
                    + ConfirmPaidStaleRequest.MIN_LIMIT + " and " + ConfirmPaidStaleRequest.MAX_LIMIT);
        }

        ConfirmPaidStaleResult result = stalePaidOrderConfirmService.sweep(olderThanMinutes, limit);
        return ResponseEntity.ok(ConfirmPaidStaleResponse.from(result));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<CancelOrderResult> cancel(
            @PathVariable String orderId,
            @RequestBody(required = false) OperatorCancelOrderRequest request) {
        String reason = request != null ? request.reason() : null;
        CancelOrderResult result = operatorOrderCancellationService.cancel(orderId, reason);
        return ResponseEntity.ok(result);
    }
}
