package com.example.order.application.service;

import com.example.common.time.MetricsPeriod;
import com.example.order.application.dto.OrderInsights;
import com.example.order.domain.repository.OrderRepository;
import com.example.order.domain.repository.ProductOrderRankingRow;
import com.example.order.domain.repository.SellerOrderRankingRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link OrderQueryService#getInsights(Optional)} (TASK-BE-469, TASK-MONO-807).
 *
 * <p>Uses a mocked {@link OrderRepository} to verify that:
 * <ul>
 *   <li>An empty tenant (no aggregation rows) yields four empty ranking arrays.</li>
 *   <li>More than {@code INSIGHTS_TOP_N} (5) groups are truncated to exactly 5,
 *       sorted DESC by the respective metric.</li>
 *   <li>The two product orderings (by order-count vs. by revenue) can diverge —
 *       a product ranked #1 by revenue need not be #1 by order-count.</li>
 *   <li>{@code RankedEntry.label}: product label is the denormalized product
 *       name; seller label equals the seller id (order-service has no seller
 *       display name — TASK-BE-469 Related Specs).</li>
 *   <li>TASK-MONO-807: the paid window handed to the repository — a period's current
 *       window, or everything up to now without one. Which orders count is the query's
 *       job and is pinned by {@code OrderMetricsQueryIntegrationTest}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OrderQueryService.getInsights() 단위 테스트")
class OrderQueryServiceInsightsTest {

    /** 2026-10-23 14:05 KST, a Friday. */
    private static final Instant NOW = Instant.parse("2026-10-23T05:05:00Z");

    @Mock
    private OrderRepository orderRepository;

    private OrderQueryService orderQueryService;

    @BeforeEach
    void setUp() {
        orderQueryService = new OrderQueryService(orderRepository, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("집계 결과가 비어있으면 네 랭킹 모두 빈 배열이다")
    void getInsights_emptyTenant_returnsAllEmptyRankings() {
        given(orderRepository.aggregateProductRanking(any(), any())).willReturn(List.of());
        given(orderRepository.aggregateSellerRanking(any(), any())).willReturn(List.of());

        OrderInsights insights = orderQueryService.getInsights(Optional.empty());

        assertThat(insights.topProductsByOrderCount()).isEmpty();
        assertThat(insights.topProductsByRevenue()).isEmpty();
        assertThat(insights.topSellersByOrderCount()).isEmpty();
        assertThat(insights.topSellersByRevenue()).isEmpty();
    }

    @Test
    @DisplayName("7개 상품 그룹이 있으면 정확히 5개로 잘리고 orderCount 내림차순 정렬된다")
    void getInsights_moreThanTopN_truncatesToFiveSortedDescending() {
        List<ProductOrderRankingRow> products = List.of(
                new ProductOrderRankingRow("p1", "상품1", 10, 1000),
                new ProductOrderRankingRow("p2", "상품2", 70, 2000),
                new ProductOrderRankingRow("p3", "상품3", 60, 3000),
                new ProductOrderRankingRow("p4", "상품4", 50, 4000),
                new ProductOrderRankingRow("p5", "상품5", 40, 5000),
                new ProductOrderRankingRow("p6", "상품6", 30, 6000),
                new ProductOrderRankingRow("p7", "상품7", 20, 7000)
        );
        given(orderRepository.aggregateProductRanking(any(), any())).willReturn(products);
        given(orderRepository.aggregateSellerRanking(any(), any())).willReturn(List.of());

        OrderInsights insights = orderQueryService.getInsights(Optional.empty());

        assertThat(insights.topProductsByOrderCount()).hasSize(5);
        assertThat(insights.topProductsByOrderCount())
                .extracting(OrderInsights.RankedEntry::id)
                .containsExactly("p2", "p3", "p4", "p5", "p6");
        assertThat(insights.topProductsByOrderCount())
                .extracting(OrderInsights.RankedEntry::value)
                .containsExactly(70L, 60L, 50L, 40L, 30L);
    }

    @Test
    @DisplayName("orderCount 최고 상품과 revenue 최고 상품이 다르면 두 랭킹의 1위가 서로 다르다")
    void getInsights_divergentOrderings_differentTopEntryPerMetric() {
        List<ProductOrderRankingRow> products = List.of(
                new ProductOrderRankingRow("p-low-count-high-revenue", "고가상품", 5, 100_000),
                new ProductOrderRankingRow("p-high-count-low-revenue", "저가상품", 50, 5_000)
        );
        given(orderRepository.aggregateProductRanking(any(), any())).willReturn(products);
        given(orderRepository.aggregateSellerRanking(any(), any())).willReturn(List.of());

        OrderInsights insights = orderQueryService.getInsights(Optional.empty());

        assertThat(insights.topProductsByOrderCount().get(0).id()).isEqualTo("p-high-count-low-revenue");
        assertThat(insights.topProductsByRevenue().get(0).id()).isEqualTo("p-low-count-high-revenue");
    }

    @Test
    @DisplayName("상품 RankedEntry.label 은 상품명, 판매자 RankedEntry.label 은 판매자 id 와 동일하다")
    void getInsights_rankedEntryLabels_productNameVsSellerIdEcho() {
        given(orderRepository.aggregateProductRanking(any(), any()))
                .willReturn(List.of(new ProductOrderRankingRow("p1", "베이직 티셔츠", 10, 100_000)));
        given(orderRepository.aggregateSellerRanking(any(), any()))
                .willReturn(List.of(new SellerOrderRankingRow("seller-1", 10, 100_000)));

        OrderInsights insights = orderQueryService.getInsights(Optional.empty());

        OrderInsights.RankedEntry productEntry = insights.topProductsByOrderCount().get(0);
        assertThat(productEntry.id()).isEqualTo("p1");
        assertThat(productEntry.label()).isEqualTo("베이직 티셔츠");

        OrderInsights.RankedEntry sellerEntry = insights.topSellersByOrderCount().get(0);
        assertThat(sellerEntry.id()).isEqualTo("seller-1");
        assertThat(sellerEntry.label()).isEqualTo("seller-1");
        assertThat(sellerEntry.label()).isEqualTo(sellerEntry.id());
    }

    @Test
    @DisplayName("기간이 있으면 그 기간의 현재 구간, 없으면 처음부터 지금까지를 넘긴다")
    void getInsights_paidWindow_periodOrAllTime() {
        given(orderRepository.aggregateProductRanking(any(), any())).willReturn(List.of());
        given(orderRepository.aggregateSellerRanking(any(), any())).willReturn(List.of());

        orderQueryService.getInsights(Optional.of(MetricsPeriod.MONTH));
        // 2026-10-01 00:00 KST → now
        verify(orderRepository).aggregateProductRanking(Instant.parse("2026-09-30T15:00:00Z"), NOW);
        verify(orderRepository).aggregateSellerRanking(Instant.parse("2026-09-30T15:00:00Z"), NOW);

        orderQueryService.getInsights(Optional.empty());
        verify(orderRepository).aggregateProductRanking(Instant.EPOCH, NOW);
        verify(orderRepository).aggregateSellerRanking(Instant.EPOCH, NOW);
    }
}
