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
import com.example.common.metrics.PerformanceMetrics;
import com.example.common.summary.PeriodSummary;
import com.example.common.time.KstPeriodBounds;
import com.example.common.time.KstWindow;
import com.example.common.time.MetricsPeriod;
import com.example.order.domain.repository.OrderRepository;
import com.example.order.domain.repository.PaidOrderMetricRow;
import com.example.order.domain.repository.ProductOrderRankingRow;
import com.example.order.domain.repository.SellerOrderRankingRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToLongFunction;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private static final int INSIGHTS_TOP_N = 5;

    /** Metric keys of {@code GET /api/admin/orders/metrics} (order-api.md). */
    static final String METRIC_REVENUE = "revenue";
    static final String METRIC_UNITS = "units";
    static final String METRIC_ORDERS = "orders";

    /** Order prices are KRW integers (order-api.md — «less than 1 KRW to pay»). */
    private static final String CURRENCY = "KRW";

    private final OrderRepository orderRepository;
    private final Clock clock;

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
     * Overview performance metrics (ADR-MONO-084, TASK-MONO-807): revenue, units and orders
     * over the current period-to-date and the same-length previous window, plus a daily
     * series. Realised sales only ({@link OrderStatus#REALISED_SALE}, not refunded),
     * attributed to {@code paidAt} — see {@code order-api.md} § GET /api/admin/orders/metrics.
     */
    @Transactional(readOnly = true)
    public PerformanceMetrics getMetrics(MetricsPeriod period) {
        KstPeriodBounds bounds = KstPeriodBounds.from(clock);
        KstWindow current = bounds.currentWindow(period);
        KstWindow previous = bounds.previousWindow(period);

        List<PaidOrderMetricRow> currentRows =
                orderRepository.findPaidOrderMetrics(current.fromInstant(), current.toInstant());
        List<PaidOrderMetricRow> previousRows =
                orderRepository.findPaidOrderMetrics(previous.fromInstant(), previous.toInstant());

        Map<LocalDate, Map<String, Long>> daily = new HashMap<>();
        for (PaidOrderMetricRow row : currentRows) {
            Map<String, Long> day = daily.computeIfAbsent(KstPeriodBounds.dateOf(row.paidAt()), d -> new HashMap<>());
            day.merge(METRIC_REVENUE, row.revenue(), Long::sum);
            day.merge(METRIC_UNITS, row.units(), Long::sum);
            day.merge(METRIC_ORDERS, 1L, Long::sum);
        }

        return PerformanceMetrics.of(bounds, period, List.of(
                PerformanceMetrics.Metric.money(METRIC_REVENUE, CURRENCY,
                        sum(currentRows, PaidOrderMetricRow::revenue), sum(previousRows, PaidOrderMetricRow::revenue)),
                PerformanceMetrics.Metric.count(METRIC_UNITS,
                        sum(currentRows, PaidOrderMetricRow::units), sum(previousRows, PaidOrderMetricRow::units)),
                PerformanceMetrics.Metric.count(METRIC_ORDERS, currentRows.size(), previousRows.size())),
                daily);
    }

    /**
     * Returns tenant-scoped top-5 rankings of products and sellers by order-count and by
     * revenue, aggregated from {@code order_items} (TASK-BE-469). Ties are broken by id ASC
     * for deterministic ordering.
     *
     * <p>TASK-MONO-807 — realised sales only (the same basis as {@link #getMetrics}). With a
     * period, only orders paid in that period's current window; without one, every paid
     * order up to now (the call the console made before the period existed).
     */
    @Transactional(readOnly = true)
    public OrderInsights getInsights(Optional<MetricsPeriod> period) {
        KstPeriodBounds bounds = KstPeriodBounds.from(clock);
        Instant from = period.map(p -> bounds.currentWindow(p).fromInstant()).orElse(Instant.EPOCH);
        Instant to = bounds.nowInstant();
        List<ProductOrderRankingRow> products = orderRepository.aggregateProductRanking(from, to);
        List<SellerOrderRankingRow> sellers = orderRepository.aggregateSellerRanking(from, to);
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

    private static long sum(List<PaidOrderMetricRow> rows, ToLongFunction<PaidOrderMetricRow> value) {
        return rows.stream().mapToLong(value).sum();
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
