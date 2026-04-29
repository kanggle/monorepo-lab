package com.wms.outbound.adapter.out.persistence.adapter;

import com.wms.outbound.adapter.out.persistence.entity.OrderEntity;
import com.wms.outbound.adapter.out.persistence.entity.OrderLineEntity;
import com.wms.outbound.adapter.out.persistence.mapper.OrderMapper;
import com.wms.outbound.adapter.out.persistence.repository.OrderLineRepository;
import com.wms.outbound.adapter.out.persistence.repository.OrderRepository;
import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.result.OrderSummaryResult;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for the {@link Order} aggregate. Implements
 * {@link OrderPersistencePort}.
 *
 * <p>Lines are persisted as a separate child table; the adapter handles the
 * insert vs update branching. Lines are immutable after creation in this
 * scope (TASK-BE-037), so on save we either insert all lines (new aggregate)
 * or skip line writes entirely (existing aggregate).
 */
@Component
public class OrderPersistenceAdapter implements OrderPersistencePort {

    private final OrderRepository orderRepo;
    private final OrderLineRepository lineRepo;
    private final Clock clock;

    public OrderPersistenceAdapter(OrderRepository orderRepo,
                                   OrderLineRepository lineRepo,
                                   Clock clock) {
        this.orderRepo = orderRepo;
        this.lineRepo = lineRepo;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Order save(Order order) {
        Optional<OrderEntity> existing = orderRepo.findById(order.getId());
        OrderEntity entity;
        if (existing.isEmpty()) {
            entity = new OrderEntity(
                    order.getId(),
                    order.getOrderNo(),
                    order.getSource().name(),
                    order.getCustomerPartnerId(),
                    order.getWarehouseId(),
                    order.getStatus().name(),
                    order.getRequiredShipDate(),
                    order.getNotes(),
                    order.getCreatedAt(),
                    order.getCreatedBy(),
                    order.getUpdatedAt(),
                    order.getUpdatedBy());
            entity = orderRepo.save(entity);
            for (OrderLine line : order.getLines()) {
                OrderLineEntity le = new OrderLineEntity(
                        line.getId(),
                        line.getOrderId(),
                        line.getLineNo(),
                        line.getSkuId(),
                        line.getLotId(),
                        line.getQtyOrdered(),
                        clock.instant());
                lineRepo.save(le);
            }
        } else {
            entity = existing.get();
            entity.setStatus(order.getStatus().name());
            entity.setUpdatedAt(order.getUpdatedAt());
            entity.setUpdatedBy(order.getUpdatedBy());
            entity.setNotes(order.getNotes());
            entity = orderRepo.save(entity);
        }
        List<OrderLineEntity> lines = lineRepo.findByOrderIdOrderByLineNumberAsc(entity.getId());
        return OrderMapper.toDomain(entity, lines);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) {
        return orderRepo.findById(id).map(e ->
                OrderMapper.toDomain(e, lineRepo.findByOrderIdOrderByLineNumberAsc(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByOrderNo(String orderNo) {
        return orderRepo.existsByOrderNo(orderNo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryResult> findSummaries(OrderQueryCommand q) {
        List<OrderEntity> entities = orderRepo.findFiltered(
                q.status(), q.warehouseId(), q.customerPartnerId(),
                q.source(), q.orderNo(),
                q.requiredShipAfter(), q.requiredShipBefore(),
                q.createdAfter(), q.createdBefore(),
                PageRequest.of(q.page(), q.size()));
        return entities.stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count(OrderQueryCommand q) {
        return orderRepo.countFiltered(
                q.status(), q.warehouseId(), q.customerPartnerId(),
                q.source(), q.orderNo(),
                q.requiredShipAfter(), q.requiredShipBefore(),
                q.createdAfter(), q.createdBefore());
    }

    private OrderSummaryResult toSummary(OrderEntity e) {
        List<OrderLineEntity> ls = lineRepo.findByOrderIdOrderByLineNumberAsc(e.getId());
        long totalQty = ls.stream().mapToLong(OrderLineEntity::getRequestedQty).sum();
        return new OrderSummaryResult(
                e.getId(),
                e.getOrderNo(),
                e.getSource(),
                e.getCustomerPartnerId(),
                e.getWarehouseId(),
                e.getStatus(),
                null /* sagaState — joined separately by query service */,
                ls.size(),
                totalQty,
                e.getRequestedShipDate(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
