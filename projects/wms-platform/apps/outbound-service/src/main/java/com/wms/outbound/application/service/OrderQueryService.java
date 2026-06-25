package com.wms.outbound.application.service;

import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.application.port.in.QueryOrderUseCase;
import com.wms.outbound.application.port.out.CallerScopeProvider;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.application.security.CallerScope;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.application.result.OrderSummaryResult;
import com.wms.outbound.domain.exception.OrderNotFoundException;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OutboundSaga;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side use case for the order endpoints. Joins the saga state when
 * present so the response can carry {@code sagaState} per the contract.
 *
 * <p>The list path uses a single bulk saga lookup keyed by orderIds so the
 * total query count is independent of page size (constraint AC-03 of
 * TASK-BE-039 follow-up).
 */
@Service
public class OrderQueryService implements QueryOrderUseCase {

    private final OrderPersistencePort orderPersistence;
    private final SagaPersistencePort sagaPersistence;
    private final CallerScopeProvider callerScopeProvider;

    public OrderQueryService(OrderPersistencePort orderPersistence,
                             SagaPersistencePort sagaPersistence,
                             CallerScopeProvider callerScopeProvider) {
        this.orderPersistence = orderPersistence;
        this.sagaPersistence = sagaPersistence;
        this.callerScopeProvider = callerScopeProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResult findById(UUID orderId) {
        Order order = orderPersistence.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        // Cross-tenant guard (TASK-MONO-304): a tenant-scoped caller may only
        // read its own tenant's order; foreign / B2B orders → 403.
        callerScopeProvider.current().requireOrderAccess(order.getTenantId(), orderId);
        OutboundSaga saga = sagaPersistence.findByOrderId(orderId).orElse(null);
        return OrderResultMapper.toResult(order, saga);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult list(OrderQueryCommand command) {
        // Apply the caller's tenant scope (TASK-MONO-304): unrestricted callers
        // see the command unchanged; tenant-scoped callers are pinned to their
        // own tenant's FULFILLMENT_ECOMMERCE orders.
        CallerScope scope = callerScopeProvider.current();
        OrderQueryCommand scoped = scope.scopeListQuery(command);
        List<OrderSummaryResult> rows = orderPersistence.findSummaries(scoped);
        long total = orderPersistence.count(scoped);
        if (rows.isEmpty()) {
            return new PageResult(rows, total);
        }
        // Single bulk saga lookup — no per-row N+1 query.
        List<UUID> orderIds = new ArrayList<>(rows.size());
        for (OrderSummaryResult r : rows) {
            orderIds.add(r.orderId());
        }
        Map<UUID, String> sagaStates = sagaPersistence.findSagaStatesByOrderIds(orderIds);
        List<OrderSummaryResult> enriched = new ArrayList<>(rows.size());
        for (OrderSummaryResult r : rows) {
            enriched.add(new OrderSummaryResult(
                    r.orderId(),
                    r.orderNo(),
                    r.source(),
                    r.customerPartnerId(),
                    r.warehouseId(),
                    r.status(),
                    sagaStates.get(r.orderId()),
                    r.lineCount(),
                    r.totalQtyOrdered(),
                    r.requiredShipDate(),
                    r.createdAt(),
                    r.updatedAt()));
        }
        return new PageResult(enriched, total);
    }
}
