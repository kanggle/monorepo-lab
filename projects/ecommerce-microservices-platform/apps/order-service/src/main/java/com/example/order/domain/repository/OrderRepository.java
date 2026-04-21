package com.example.order.domain.repository;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    List<Order> saveAll(List<Order> orders);

    Optional<Order> findById(String orderId);

    PageResult<Order> findByUserId(String userId, PageQuery pageQuery);

    PageResult<Order> findByUserIdAndStatus(String userId, OrderStatus status, PageQuery pageQuery);

    List<Order> findByUserIdAndStatusIn(String userId, Collection<OrderStatus> statuses);

    boolean existsByUserIdAndProductIdAndStatus(String userId, String productId, OrderStatus status);

    PageResult<Order> findAll(PageQuery pageQuery);

    PageResult<Order> findByStatus(OrderStatus status, PageQuery pageQuery);

    PageResult<Order> findAllWithItems(PageQuery pageQuery);

    PageResult<Order> findByStatusWithItems(OrderStatus status, PageQuery pageQuery);
}
