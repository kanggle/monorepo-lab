package com.wms.outbound.adapter.in.web.controller;

import com.wms.outbound.adapter.in.web.dto.response.OrderResponse;
import com.wms.outbound.adapter.in.web.dto.response.OrderSummaryResponse;
import com.wms.outbound.adapter.in.web.dto.response.PagedResponse;
import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.application.port.in.QueryOrderUseCase;
import com.wms.outbound.application.result.OrderResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoints for the {@code Order} aggregate. Per
 * {@code outbound-service-api.md} §1.2 / §1.3.
 */
@RestController
@RequestMapping("/api/v1/outbound/orders")
public class OrderQueryController {

    private final QueryOrderUseCase queryOrder;

    public OrderQueryController(QueryOrderUseCase queryOrder) {
        this.queryOrder = queryOrder;
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
}
