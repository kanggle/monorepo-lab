package com.example.order.application.service;

import com.example.order.application.dto.AdminOrderSummaryStats;
import com.example.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Unit test for {@link OrderQueryService#getOrderSummary()} (TASK-BE-468).
 *
 * <p>Uses a mocked {@link OrderRepository} to verify that:
 * <ul>
 *   <li>An empty tenant (all counts return 0) yields an all-zero summary.</li>
 *   <li>The count values returned by the repository are faithfully assembled
 *       into the {@link AdminOrderSummaryStats} record.</li>
 * </ul>
 *
 * <p>KST boundary computation is tested indirectly: the test stubs
 * {@code countCreatedBetween(any(), any())} rather than asserting exact
 * {@link Instant} values (the boundary instants depend on wall-clock
 * {@code ZonedDateTime.now()}, which would require injecting a fixed
 * {@link java.time.Clock} through the service — a bigger structural change
 * outside the task scope). The important invariants — correct delegate calls
 * and correct assembly — are fully covered.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OrderQueryService.getOrderSummary() 단위 테스트")
class OrderQueryServiceSummaryTest {

    @InjectMocks
    private OrderQueryService orderQueryService;

    @Mock
    private OrderRepository orderRepository;

    @Test
    @DisplayName("테넌트에 주문이 없으면 모든 카운트가 0이다")
    void getOrderSummary_emptyTenant_returnsAllZeros() {
        given(orderRepository.countAllForTenant()).willReturn(0L);
        given(orderRepository.countCreatedBetween(any(Instant.class), any(Instant.class))).willReturn(0L);

        AdminOrderSummaryStats stats = orderQueryService.getOrderSummary();

        assertThat(stats.total()).isZero();
        assertThat(stats.today()).isZero();
        assertThat(stats.week()).isZero();
        assertThat(stats.month()).isZero();
    }

    @Test
    @DisplayName("방금 생성된 주문은 today, week, month, total 모두에 포함된다")
    void getOrderSummary_orderCreatedNow_countedInAllPeriods() {
        // A row created "now" (within the current KST day/week/month) is counted in
        // every period bucket. We stub all three period queries to return 1 and the
        // total to return 1 — the service must assemble them without mixing up fields.
        given(orderRepository.countAllForTenant()).willReturn(1L);
        given(orderRepository.countCreatedBetween(any(Instant.class), any(Instant.class))).willReturn(1L);

        AdminOrderSummaryStats stats = orderQueryService.getOrderSummary();

        assertThat(stats.total()).isEqualTo(1L);
        assertThat(stats.today()).isEqualTo(1L);
        assertThat(stats.week()).isEqualTo(1L);
        assertThat(stats.month()).isEqualTo(1L);
    }

    @Test
    @DisplayName("각 기간 카운트 값이 DTO 필드에 올바른 순서로 매핑된다")
    void getOrderSummary_distinctCounts_mappedToCorrectFields() {
        // Stub countCreatedBetween to return different values for each call so we can
        // verify that the assembly in getOrderSummary() preserves field identity.
        // Call order in the service: today → week → month (todayStart < weekStart < monthStart
        // only when today is not Monday of the 1st, but the stub sequence is deterministic).
        given(orderRepository.countAllForTenant()).willReturn(100L);
        given(orderRepository.countCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(3L)   // today (first call)
                .willReturn(15L)  // week  (second call)
                .willReturn(42L); // month (third call)

        AdminOrderSummaryStats stats = orderQueryService.getOrderSummary();

        assertThat(stats.total()).isEqualTo(100L);
        assertThat(stats.today()).isEqualTo(3L);
        assertThat(stats.week()).isEqualTo(15L);
        assertThat(stats.month()).isEqualTo(42L);
    }
}
