package com.wms.outbound.application.service.fakes;

import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.result.OrderSummaryResult;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakeOrderPersistencePort implements OrderPersistencePort {

    private final Map<UUID, Order> store = new HashMap<>();
    public int saveCalls;

    @Override
    public Order save(Order order) {
        saveCalls++;
        store.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsByOrderNo(String orderNo) {
        return store.values().stream().anyMatch(o -> o.getOrderNo().equals(orderNo));
    }

    @Override
    public List<OrderSummaryResult> findSummaries(OrderQueryCommand q) {
        // Project every stored order into a summary row. Tests can preload
        // orders via save() and observe how list() consumes them.
        List<OrderSummaryResult> rows = new ArrayList<>(store.size());
        for (Order o : store.values()) {
            long totalQty = 0L;
            for (OrderLine l : o.getLines()) {
                totalQty += l.getQtyOrdered();
            }
            rows.add(new OrderSummaryResult(
                    o.getId(),
                    o.getOrderNo(),
                    o.getSource().name(),
                    o.getCustomerPartnerId(),
                    o.getWarehouseId(),
                    o.getStatus().name(),
                    null,
                    o.getLines().size(),
                    totalQty,
                    o.getRequiredShipDate(),
                    o.getCreatedAt(),
                    o.getUpdatedAt()));
        }
        return rows;
    }

    @Override
    public long count(OrderQueryCommand q) {
        return store.size();
    }

    public int orderCount() {
        return store.size();
    }
}
