package com.example.product.application.service;

import com.example.product.application.dto.SellerPeriodSummary;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("SellerSummaryService 단위 테스트 — KST 기간별 카운트")
class SellerSummaryServiceTest {

    @Mock
    private SellerRepository sellerRepository;

    @InjectMocks
    private SellerSummaryService sellerSummaryService;

    @Test
    @DisplayName("셀러가 없을 때 모든 카운트는 0")
    void getSummary_empty_allZeros() {
        given(sellerRepository.countByTenant()).willReturn(0L);
        given(sellerRepository.countByTenantCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(0L);

        SellerPeriodSummary summary = sellerSummaryService.getSummary();

        assertThat(summary.total()).isZero();
        assertThat(summary.today()).isZero();
        assertThat(summary.week()).isZero();
        assertThat(summary.month()).isZero();
    }

    @Test
    @DisplayName("오늘 등록된 셀러는 today/week/month/total 모두에 반영된다")
    void getSummary_todaySeller_countedInAllPeriods() {
        // total=3, every period query returns 1 (simulating one seller created today)
        given(sellerRepository.countByTenant()).willReturn(3L);
        given(sellerRepository.countByTenantCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(1L);

        SellerPeriodSummary summary = sellerSummaryService.getSummary();

        assertThat(summary.total()).isEqualTo(3L);
        assertThat(summary.today()).isEqualTo(1L);
        assertThat(summary.week()).isEqualTo(1L);
        assertThat(summary.month()).isEqualTo(1L);
    }

    @Test
    @DisplayName("기간별 카운트가 서로 다를 때 각 값이 독립적으로 반영된다")
    void getSummary_differentPeriodCounts_returnedIndependently() {
        given(sellerRepository.countByTenant()).willReturn(50L);
        // First call = todayStart→now (today), second = weekStart→now (week), third = monthStart→now (month)
        given(sellerRepository.countByTenantCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(2L, 8L, 15L);

        SellerPeriodSummary summary = sellerSummaryService.getSummary();

        assertThat(summary.total()).isEqualTo(50L);
        assertThat(summary.today()).isEqualTo(2L);
        assertThat(summary.week()).isEqualTo(8L);
        assertThat(summary.month()).isEqualTo(15L);
    }
}
