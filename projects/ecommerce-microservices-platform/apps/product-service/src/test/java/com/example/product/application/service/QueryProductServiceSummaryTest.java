package com.example.product.application.service;

import com.example.common.summary.PeriodSummary;
import com.example.product.application.port.ProductQueryPort;
import com.example.product.domain.repository.ProductRepository;
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
 * Unit test for {@link QueryProductService#getPeriodSummary()} (TASK-BE-468,
 * TASK-MONO-322 fold of the standalone {@code ProductSummaryService}).
 *
 * <p>Uses a mocked {@link ProductRepository} to verify that:
 * <ul>
 *   <li>An empty tenant (all counts return 0) yields an all-zero summary.</li>
 *   <li>The count values returned by the repository are faithfully assembled
 *       into the {@link PeriodSummary} record.</li>
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
@DisplayName("QueryProductService.getPeriodSummary() 단위 테스트")
class QueryProductServiceSummaryTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductQueryPort productQueryPort;

    @InjectMocks
    private QueryProductService queryProductService;

    @Test
    @DisplayName("상품이 없을 때 모든 카운트는 0")
    void getPeriodSummary_empty_allZeros() {
        given(productRepository.countAllForTenant()).willReturn(0L);
        given(productRepository.countCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(0L);

        PeriodSummary summary = queryProductService.getPeriodSummary();

        assertThat(summary.total()).isZero();
        assertThat(summary.today()).isZero();
        assertThat(summary.week()).isZero();
        assertThat(summary.month()).isZero();
    }

    @Test
    @DisplayName("오늘 등록된 상품은 today/week/month/total 모두에 반영된다")
    void getPeriodSummary_todayProduct_countedInAllPeriods() {
        // total=5, every period query returns 1 (simulating one product created today)
        given(productRepository.countAllForTenant()).willReturn(5L);
        given(productRepository.countCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(1L);

        PeriodSummary summary = queryProductService.getPeriodSummary();

        assertThat(summary.total()).isEqualTo(5L);
        assertThat(summary.today()).isEqualTo(1L);
        assertThat(summary.week()).isEqualTo(1L);
        assertThat(summary.month()).isEqualTo(1L);
    }

    @Test
    @DisplayName("기간별 카운트가 서로 다를 때 각 값이 독립적으로 반영된다")
    void getPeriodSummary_differentPeriodCounts_returnedIndependently() {
        given(productRepository.countAllForTenant()).willReturn(100L);
        // First call = todayStart→now (today), second = weekStart→now (week), third = monthStart→now (month)
        given(productRepository.countCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(3L, 10L, 25L);

        PeriodSummary summary = queryProductService.getPeriodSummary();

        assertThat(summary.total()).isEqualTo(100L);
        assertThat(summary.today()).isEqualTo(3L);
        assertThat(summary.week()).isEqualTo(10L);
        assertThat(summary.month()).isEqualTo(25L);
    }
}
