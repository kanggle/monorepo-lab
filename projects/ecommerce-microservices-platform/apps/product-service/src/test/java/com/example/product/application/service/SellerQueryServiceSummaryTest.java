package com.example.product.application.service;

import com.example.common.summary.PeriodSummary;
import com.example.product.application.port.SellerQueryPort;
import com.example.product.domain.repository.SellerRepository;
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
 * Unit test for {@link SellerQueryService#getPeriodSummary()} (TASK-BE-468,
 * TASK-MONO-322 fold of the standalone {@code SellerSummaryService}).
 *
 * <p>Uses a mocked {@link SellerRepository} to verify that:
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
@DisplayName("SellerQueryService.getPeriodSummary() 단위 테스트")
class SellerQueryServiceSummaryTest {

    @Mock
    private SellerQueryPort sellerQueryPort;

    @Mock
    private SellerRepository sellerRepository;

    @InjectMocks
    private SellerQueryService sellerQueryService;

    @Test
    @DisplayName("셀러가 없을 때 모든 카운트는 0")
    void getPeriodSummary_empty_allZeros() {
        given(sellerRepository.countAllForTenant()).willReturn(0L);
        given(sellerRepository.countCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(0L);

        PeriodSummary summary = sellerQueryService.getPeriodSummary();

        assertThat(summary.total()).isZero();
        assertThat(summary.today()).isZero();
        assertThat(summary.week()).isZero();
        assertThat(summary.month()).isZero();
    }

    @Test
    @DisplayName("오늘 등록된 셀러는 today/week/month/total 모두에 반영된다")
    void getPeriodSummary_todaySeller_countedInAllPeriods() {
        // total=3, every period query returns 1 (simulating one seller created today)
        given(sellerRepository.countAllForTenant()).willReturn(3L);
        given(sellerRepository.countCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(1L);

        PeriodSummary summary = sellerQueryService.getPeriodSummary();

        assertThat(summary.total()).isEqualTo(3L);
        assertThat(summary.today()).isEqualTo(1L);
        assertThat(summary.week()).isEqualTo(1L);
        assertThat(summary.month()).isEqualTo(1L);
    }

    @Test
    @DisplayName("기간별 카운트가 서로 다를 때 각 값이 독립적으로 반영된다")
    void getPeriodSummary_differentPeriodCounts_returnedIndependently() {
        given(sellerRepository.countAllForTenant()).willReturn(50L);
        // First call = todayStart→now (today), second = weekStart→now (week), third = monthStart→now (month)
        given(sellerRepository.countCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(2L, 8L, 15L);

        PeriodSummary summary = sellerQueryService.getPeriodSummary();

        assertThat(summary.total()).isEqualTo(50L);
        assertThat(summary.today()).isEqualTo(2L);
        assertThat(summary.week()).isEqualTo(8L);
        assertThat(summary.month()).isEqualTo(15L);
    }
}
