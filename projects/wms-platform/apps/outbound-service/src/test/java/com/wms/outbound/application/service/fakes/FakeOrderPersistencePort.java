package com.wms.outbound.application.service.fakes;

import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.result.OrderSummaryResult;
import com.wms.outbound.domain.model.Order;
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
        return new ArrayList<>();
    }

    @Override
    public long count(OrderQueryCommand q) {
        return store.size();
    }

    public int orderCount() {
        return store.size();
    }
}
