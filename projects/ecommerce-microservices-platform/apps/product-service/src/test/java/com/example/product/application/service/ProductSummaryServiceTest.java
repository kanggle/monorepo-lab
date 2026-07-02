package com.example.product.application.service;

import com.example.product.application.dto.ProductPeriodSummary;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("ProductSummaryService 단위 테스트 — KST 기간별 카운트")
class ProductSummaryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductSummaryService productSummaryService;

    @Test
    @DisplayName("상품이 없을 때 모든 카운트는 0")
    void getSummary_empty_allZeros() {
        given(productRepository.countByTenant()).willReturn(0L);
        given(productRepository.countByTenantCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(0L);

        ProductPeriodSummary summary = productSummaryService.getSummary();

        assertThat(summary.total()).isZero();
        assertThat(summary.today()).isZero();
        assertThat(summary.week()).isZero();
        assertThat(summary.month()).isZero();
    }

    @Test
    @DisplayName("오늘 등록된 상품은 today/week/month/total 모두에 반영된다")
    void getSummary_todayProduct_countedInAllPeriods() {
        // total=5, every period query returns 1 (simulating one product created today)
        given(productRepository.countByTenant()).willReturn(5L);
        given(productRepository.countByTenantCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(1L);

        ProductPeriodSummary summary = productSummaryService.getSummary();

        assertThat(summary.total()).isEqualTo(5L);
        assertThat(summary.today()).isEqualTo(1L);
        assertThat(summary.week()).isEqualTo(1L);
        assertThat(summary.month()).isEqualTo(1L);
    }

    @Test
    @DisplayName("기간별 카운트가 서로 다를 때 각 값이 독립적으로 반영된다")
    void getSummary_differentPeriodCounts_returnedIndependently() {
        given(productRepository.countByTenant()).willReturn(100L);
        // First call = todayStart→now (today), second = weekStart→now (week), third = monthStart→now (month)
        given(productRepository.countByTenantCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(3L, 10L, 25L);

        ProductPeriodSummary summary = productSummaryService.getSummary();

        assertThat(summary.total()).isEqualTo(100L);
        assertThat(summary.today()).isEqualTo(3L);
        assertThat(summary.week()).isEqualTo(10L);
        assertThat(summary.month()).isEqualTo(25L);
    }
}
