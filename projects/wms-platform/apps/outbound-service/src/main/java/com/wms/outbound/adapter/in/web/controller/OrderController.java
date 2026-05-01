package com.wms.outbound.adapter.in.web.controller;

import com.wms.outbound.adapter.in.web.dto.request.CancelOrderRequest;
import com.wms.outbound.adapter.in.web.dto.request.CreateOrderRequest;
import com.wms.outbound.adapter.in.web.dto.response.OrderResponse;
import com.wms.outbound.application.command.CancelOrderCommand;
import com.wms.outbound.application.command.ReceiveOrderCommand;
import com.wms.outbound.application.command.ReceiveOrderLineCommand;
import com.wms.outbound.application.port.in.CancelOrderUseCase;
import com.wms.outbound.application.port.in.ReceiveOrderUseCase;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.domain.exception.WarehouseMismatchException;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mutating endpoints for the {@code Order} aggregate. Authorization is
 * enforced inside the application services (see {@code SecurityConfig} for
 * the coarse-grained role gate at the filter chain).
 *
 * <p>Endpoints per {@code outbound-service-api.md}:
 * <ul>
 *   <li>{@code POST /api/v1/outbound/orders}</li>
 *   <li>{@code POST /api/v1/outbound/orders/{id}:cancel}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/outbound/orders")
public class OrderController {

    private final ReceiveOrderUseCase receiveOrder;
    private final CancelOrderUseCase cancelOrder;

    public OrderController(ReceiveOrderUseCase receiveOrder,
                           CancelOrderUseCase cancelOrder) {
        this.receiveOrder = receiveOrder;
        this.cancelOrder = cancelOrder;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        validateLinesShareWarehouse(request);
        ReceiveOrderCommand command = new ReceiveOrderCommand(
                request.orderNo(),
                "MANUAL",
                request.customerPartnerId(),
                request.warehouseId(),
                request.requiredShipDate(),
                request.notes(),
                request.lines().stream()
                        .map(l -> new ReceiveOrderLineCommand(
                                l.lineNo(), l.skuId(), l.lotId(), l.qtyOrdered()))
                        .toList(),
                actorId(jwt),
                callerRoles(authentication));
        OrderResult result = receiveOrder.receive(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(String.valueOf(result.version()))
                .body(OrderResponse.from(result));
    }

    @PostMapping("/{id}:cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID id,
            @Valid @RequestBody CancelOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        CancelOrderCommand command = new CancelOrderCommand(
                id,
                request.reason(),
                request.version(),
                actorId(jwt),
                callerRoles(authentication));
        OrderResult result = cancelOrder.cancel(command);
        return ResponseEntity.ok(OrderResponse.from(result));
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
    }

    /**
     * v1 single-warehouse rule (W1): every line implicitly belongs to the
     * order's warehouse. Cross-warehouse orders are out of scope.
     */
    private static void validateLinesShareWarehouse(CreateOrderRequest request) {
        // Today the request has only one warehouseId and lines have no
        // warehouse reference, so this guard is structural / future-proof:
        // when we add a per-line warehouse field, swap this check for the
        // real comparison and surface WarehouseMismatchException.
        if (request.warehouseId() == null) {
            throw new WarehouseMismatchException("warehouseId is required");
        }
    }

    static Set<String> callerRoles(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        Set<String> roles = new HashSet<>();
        for (GrantedAuthority a : authentication.getAuthorities()) {
            roles.add(a.getAuthority());
        }
        return Set.copyOf(roles);
    }

    static String actorId(Jwt jwt) {
        if (jwt == null) {
            return "anonymous";
        }
        return jwt.getSubject() != null ? jwt.getSubject() : jwt.getClaimAsString("actorId");
    }
}
