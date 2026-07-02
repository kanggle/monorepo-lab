package com.example.order.application.service;

import com.example.order.application.dto.AdminOrderDetail;
import com.example.order.application.dto.AdminOrderSummary;
import com.example.order.application.dto.AdminOrderSummaryStats;
import com.example.order.application.dto.OrderDetail;
import com.example.order.application.dto.OrderSummary;
import com.example.order.application.exception.UnauthorizedOrderAccessException;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public PageResult<OrderSummary> getOrders(String userId, OrderStatus status, PageQuery pageQuery) {
        PageResult<Order> orders = (status != null)
                ? orderRepository.findByUserIdAndStatus(userId, status, pageQuery)
                : orderRepository.findByUserId(userId, pageQuery);
        return mapPageResult(orders, OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public OrderDetail getOrder(String orderId, String requestingUserId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getUserId().equals(requestingUserId)) {
            throw new UnauthorizedOrderAccessException();
        }

        return OrderDetail.from(order);
    }

    @Transactional(readOnly = true)
    public PageResult<AdminOrderSummary> getAllOrders(OrderStatus status, PageQuery pageQuery) {
        PageResult<Order> orders = (status != null)
                ? orderRepository.findByStatusWithItems(status, pageQuery)
                : orderRepository.findAllWithItems(pageQuery);
        return mapPageResult(orders, AdminOrderSummary::from);
    }

    @Transactional(readOnly = true)
    public AdminOrderDetail getOrderForAdmin(String orderId) {
        // OPERATOR detail: tenant + nested net-zero seller scope (AC-6). A cross-seller
        // (or cross-tenant) id resolves to empty → 404 (M3). Absent / '*' scope =
        // full tenant view (F1).
        Order order = orderRepository.findByIdForAdmin(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return AdminOrderDetail.from(order);
    }

    @Transactional(readOnly = true)
    public boolean hasUserPurchasedProduct(String userId, String productId) {
        return orderRepository.existsByUserIdAndProductIdAndStatus(userId, productId, OrderStatus.DELIVERED);
    }

    /**
     * Returns tenant-scoped KST calendar-period-to-date order counts for the
     * admin summary dashboard (TASK-BE-468).
     *
     * <p>Boundaries are computed in Asia/Seoul (KST) so that "today", "this week",
     * and "this month" align with the Korean business calendar rather than UTC.
     * All three period starts are inclusive; {@code now} is the exclusive upper bound
     * (orders placed in the future cannot exist, so open-ended upper bound would give
     * the same result, but using {@code now.toInstant()} keeps the query deterministic
     * within the transaction).
     */
    @Transactional(readOnly = true)
    public AdminOrderSummaryStats getOrderSummary() {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        ZonedDateTime now = ZonedDateTime.now(kst);
        ZonedDateTime todayStart = now.toLocalDate().atStartOfDay(kst);
        ZonedDateTime weekStart  = now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(kst);
        ZonedDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(kst);

        long total = orderRepository.countAllForTenant();
        long today = orderRepository.countCreatedBetween(todayStart.toInstant(), now.toInstant());
        long week  = orderRepository.countCreatedBetween(weekStart.toInstant(), now.toInstant());
        long month = orderRepository.countCreatedBetween(monthStart.toInstant(), now.toInstant());

        return new AdminOrderSummaryStats(today, week, month, total);
    }

    private static <T> PageResult<T> mapPageResult(PageResult<Order> source, Function<Order, T> mapper) {
        return new PageResult<>(
                source.content().stream().map(mapper).toList(),
                source.page(),
                source.size(),
                source.totalElements(),
                source.totalPages()
        );
    }
}
