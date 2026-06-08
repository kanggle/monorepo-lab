package com.wms.outbound.adapter.in.web.controller;

import com.wms.outbound.adapter.in.web.dto.response.OrderResponse;
import com.wms.outbound.adapter.in.web.dto.response.OrderSummaryResponse;
import com.wms.outbound.adapter.in.web.dto.response.PagedResponse;
import com.wms.outbound.adapter.in.web.dto.response.PickingRequestListResponse;
import com.wms.outbound.adapter.in.web.dto.response.PickingRequestResponse;
import com.wms.outbound.adapter.in.web.dto.response.SagaResponse;
import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.application.port.in.QueryOrderUseCase;
import com.wms.outbound.application.port.in.QueryPickingRequestUseCase;
import com.wms.outbound.application.port.in.QuerySagaUseCase;
import com.wms.outbound.application.result.OrderResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoints for the {@code Order} aggregate. Per
 * {@code outbound-service-api.md} §1.2 / §1.3 / §2.4.
 */
@RestController
@RequestMapping("/api/v1/outbound/orders")
public class OrderQueryController {

    private final QueryOrderUseCase queryOrder;
    private final QueryPickingRequestUseCase queryPickingRequest;
    private final QuerySagaUseCase querySaga;

    public OrderQueryController(QueryOrderUseCase queryOrder,
                                QueryPickingRequestUseCase queryPickingRequest,
                                QuerySagaUseCase querySaga) {
        this.queryOrder = queryOrder;
        this.queryPickingRequest = queryPickingRequest;
        this.querySaga = querySaga;
    }

    @GetMapping("/{id}/saga")
    public ResponseEntity<SagaResponse> getSaga(@PathVariable UUID id) {
        // Assert the order exists first → ORDER_NOT_FOUND (404) per §5.1, with the
        // canonical error envelope (the saga is created atomically with the order,
        // so a present order always has a saga).
        queryOrder.findById(id);
        return querySaga.findByOrderId(id)
                .map(SagaResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.<SagaResponse>notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        OrderResult result = queryOrder.findById(id);
        return ResponseEntity.ok()
                .eTag(String.valueOf(result.version()))
                .body(OrderResponse.from(result));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<OrderSummaryResponse>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID customerPartnerId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) LocalDate requiredShipAfter,
            @RequestParam(required = false) LocalDate requiredShipBefore,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) Instant createdBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        OrderQueryCommand command = new OrderQueryCommand(
                status, warehouseId, customerPartnerId, source, orderNo,
                requiredShipAfter, requiredShipBefore,
                createdAfter, createdBefore,
                page, size);
        QueryOrderUseCase.PageResult result = queryOrder.list(command);
        List<OrderSummaryResponse> items = result.items().stream()
                .map(OrderSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(new PagedResponse<>(items, page, size, result.total()));
    }

    /**
     * §2.4 — List picking requests for an order.
     *
     * <p>Asserts the order exists first (throws {@code OrderNotFoundException} →
     * 404 {@code ORDER_NOT_FOUND} if not). Then returns the order's picking
     * request(s) including planned lines. Returns {@code 200 { content: [] }}
     * (not 404) when the order exists but the saga has not yet created a picking
     * request.
     *
     * <p>Auth: {@code OUTBOUND_READ} — enforced by the {@code SecurityConfig}
     * HTTP-method gate (GET /api/** requires OUTBOUND_READ|WRITE|ADMIN).
     */
    @GetMapping("/{id}/picking-requests")
    public ResponseEntity<PickingRequestListResponse> listPickingRequests(@PathVariable UUID id) {
        // Assert order exists — propagates OrderNotFoundException → 404 ORDER_NOT_FOUND.
        queryOrder.findById(id);

        Optional<PickingRequestResponse> pickingResponse = queryPickingRequest
                .findByOrderId(id)
                .map(PickingRequestResponse::from);

        List<PickingRequestResponse> content = pickingResponse
                .map(List::of)
                .orElseGet(List::of);

        return ResponseEntity.ok(new PickingRequestListResponse(content));
    }
}
