package com.example.order.application.service;

import com.example.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Answers which order ids exist, for batch-worker's orphan-coupon reconciliation (TASK-INT-028,
 * contract {@code order-existence.md}).
 *
 * <p>"Exists" is any tenant and any status. batch-worker releases the coupon of every order it does
 * not find here, so the answer must never hide a real order: a cancelled, delivered, anonymized or
 * other-tenant order is still an order that was saved.
 */
@Service
@RequiredArgsConstructor
public class OrderExistenceQueryService {

    private final OrderRepository orderRepository;

    public Set<String> existing(Collection<String> orderIds) {
        return orderRepository.findExistingOrderIdsAcrossTenants(new LinkedHashSet<>(orderIds));
    }
}
