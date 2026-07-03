package com.example.order.application.service;

import com.example.order.application.dto.AdminOrderDetail;
import com.example.order.application.dto.AdminOrderSummary;
import com.example.order.application.dto.OrderDetail;
import com.example.order.application.dto.OrderInsights;
import com.example.order.application.dto.OrderSummary;
import com.example.order.application.exception.UnauthorizedOrderAccessException;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.common.summary.PeriodSummary;
import com.example.common.time.KstPeriodBounds;
import com.example.order.domain.repository.OrderRepository;
import com.example.order.domain.repository.ProductOrderRankingRow;
import com.example.order.domain.repository.SellerOrderRankingRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private static final int INSIGHTS_TOP_N = 5;

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
    public PeriodSummary getPeriodSummary() {
        KstPeriodBounds b = KstPeriodBounds.now();

        long total = orderRepository.countAllForTenant();
        long today = orderRepository.countCreatedBetween(b.todayStartInstant(), b.nowInstant());
        long week  = orderRepository.countCreatedBetween(b.weekStartInstant(), b.nowInstant());
        long month = orderRepository.countCreatedBetween(b.monthStartInstant(), b.nowInstant());

        return new PeriodSummary(today, week, month, total);
    }

    /**
     * Returns tenant-scoped, CANCELLED-excluded top-5 rankings of products and
     * sellers by order-count and by revenue, aggregated from {@code order_items}
     * (TASK-BE-469). Ties are broken by id ASC for deterministic ordering.
     */
    @Transactional(readOnly = true)
    public OrderInsights getInsights() {
        List<ProductOrderRankingRow> products = orderRepository.aggregateProductRanking();
        List<SellerOrderRankingRow> sellers = orderRepository.aggregateSellerRanking();
        return new OrderInsights(
                topN(products, ProductOrderRankingRow::orderCount, ProductOrderRankingRow::productId,
                        p -> new OrderInsights.RankedEntry(p.productId(), p.productName(), p.orderCount())),
                topN(products, ProductOrderRankingRow::revenue, ProductOrderRankingRow::productId,
                        p -> new OrderInsights.RankedEntry(p.productId(), p.productName(), p.revenue())),
                topN(sellers, SellerOrderRankingRow::orderCount, SellerOrderRankingRow::sellerId,
                        s -> new OrderInsights.RankedEntry(s.sellerId(), s.sellerId(), s.orderCount())),
                topN(sellers, SellerOrderRankingRow::revenue, SellerOrderRankingRow::sellerId,
                        s -> new OrderInsights.RankedEntry(s.sellerId(), s.sellerId(), s.revenue())));
    }

    // Sort DESC by the metric, ties broken by id ASC (deterministic), take top N, map.
    private static <T> List<OrderInsights.RankedEntry> topN(
            List<T> rows,
            java.util.function.ToLongFunction<T> metric,
            Function<T, String> id,
            Function<T, OrderInsights.RankedEntry> mapper) {
        return rows.stream()
                .sorted(java.util.Comparator.comparingLong(metric).reversed()
                        .thenComparing(id))
                .limit(INSIGHTS_TOP_N)
                .map(mapper)
                .toList();
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
