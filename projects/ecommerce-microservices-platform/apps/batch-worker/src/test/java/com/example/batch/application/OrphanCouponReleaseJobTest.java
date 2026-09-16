package com.example.batch.application;

import com.example.batch.domain.model.BatchJobExecution;
import com.example.batch.domain.model.BatchJobStatus;
import com.example.batch.domain.repository.BatchJobExecutionRepository;
import com.example.batch.infrastructure.client.OrderServiceClient;
import com.example.batch.infrastructure.client.PromotionServiceClient;
import com.example.batch.infrastructure.client.PromotionServiceClient.StaleUsedCoupon;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OrphanCouponReleaseJob} (TASK-INT-028). Every case asserts on the releases actually sent,
 * because a wrong release is the failure that costs money: a customer gets to use a coupon twice.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OrphanCouponReleaseJob 단위 테스트 (TASK-INT-028)")
class OrphanCouponReleaseJobTest {

    @Mock
    private PromotionServiceClient promotionServiceClient;
    @Mock
    private OrderServiceClient orderServiceClient;
    @Mock
    private BatchJobExecutionRepository executionRepository;

    private MeterRegistry meterRegistry;
    private OrphanCouponReleaseJob job;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        job = new OrphanCouponReleaseJob(promotionServiceClient, orderServiceClient, executionRepository, meterRegistry);
        ReflectionTestUtils.setField(job, "enabled", true);
        ReflectionTestUtils.setField(job, "olderThanMinutes", 60);
        ReflectionTestUtils.setField(job, "limit", 200);
        ReflectionTestUtils.setField(job, "minScannedForAllAbsentAbort", 20);
    }

    private void historyIsSaved() {
        when(executionRepository.save(any(BatchJobExecution.class))).thenAnswer(invocation -> {
            BatchJobExecution arg = invocation.getArgument(0);
            return BatchJobExecution.reconstitute(arg.getId() != null ? arg.getId() : 1L, arg.getJobName(),
                    arg.getStatus(), arg.getStartedAt(), arg.getFinishedAt(), arg.getErrorMessage());
        });
    }

    private BatchJobExecution lastHistory() {
        ArgumentCaptor<BatchJobExecution> captor = ArgumentCaptor.forClass(BatchJobExecution.class);
        verify(executionRepository, times(2)).save(captor.capture());
        return captor.getAllValues().get(1);
    }

    private static StaleUsedCoupon coupon(String couponId, String orderId, String tenantId) {
        return new StaleUsedCoupon(couponId, orderId, tenantId, Instant.parse("2026-09-16T00:00:00Z"));
    }

    private static List<StaleUsedCoupon> absentOrderCoupons(int n) {
        List<StaleUsedCoupon> coupons = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            coupons.add(coupon("c-" + i, "o-" + i, "ecommerce"));
        }
        return coupons;
    }

    @Test
    @DisplayName("AC-3·AC-6: 없는 주문의 쿠폰만, 그 쿠폰의 테넌트로 푼다 — 있는 주문의 쿠폰은 건드리지 않는다")
    void releasesOnlyCouponsWhoseOrderIsAbsent_withTheCouponsOwnTenant() {
        historyIsSaved();
        when(promotionServiceClient.staleUsedCoupons(60, 200)).thenReturn(List.of(
                coupon("c-alive", "o-alive", "ecommerce"),
                coupon("c-orphan-a", "o-gone-a", "ecommerce"),
                coupon("c-orphan-b", "o-gone-b", "tenant-b")));
        when(orderServiceClient.existingOrderIds(List.of("o-alive", "o-gone-a", "o-gone-b")))
                .thenReturn(Set.of("o-alive"));

        job.execute();

        verify(promotionServiceClient).release("c-orphan-a", "o-gone-a", "ecommerce");
        verify(promotionServiceClient).release("c-orphan-b", "o-gone-b", "tenant-b");
        verify(promotionServiceClient, never()).release("c-alive", "o-alive", "ecommerce");
        assertThat(meterRegistry.find(OrphanCouponReleaseJob.RELEASED_COUNTER_NAME).counter().count()).isEqualTo(2.0);
        assertThat(lastHistory().getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
    }

    @Test
    @DisplayName("AC-4: order-service 가 실패하면 하나도 풀지 않고 FAILED — 모름은 없음이 아니다")
    void existenceCallFails_releasesNothing_andRecordsFailed() {
        historyIsSaved();
        when(promotionServiceClient.staleUsedCoupons(60, 200)).thenReturn(absentOrderCoupons(3));
        when(orderServiceClient.existingOrderIds(any())).thenThrow(new RuntimeException("order-service 503"));

        assertThatNoException().isThrownBy(() -> job.execute());

        verify(promotionServiceClient, never()).release(anyString(), anyString(), anyString());
        assertThat(lastHistory().getStatus()).isEqualTo(BatchJobStatus.FAILED);
    }

    @Test
    @DisplayName("전부-없음 브레이크: 20건 이상 봤는데 존재 0건이면 하나도 풀지 않고 FAILED")
    void allAbsentAtOrAboveTheBrake_releasesNothing() {
        historyIsSaved();
        when(promotionServiceClient.staleUsedCoupons(60, 200)).thenReturn(absentOrderCoupons(20));
        when(orderServiceClient.existingOrderIds(any())).thenReturn(Set.of());

        job.execute();

        verify(promotionServiceClient, never()).release(anyString(), anyString(), anyString());
        assertThat(meterRegistry.find(OrphanCouponReleaseJob.ABORTED_COUNTER_NAME).counter().count()).isEqualTo(1.0);
        assertThat(lastHistory().getStatus()).isEqualTo(BatchJobStatus.FAILED);
    }

    @Test
    @DisplayName("대조군: 브레이크 문턱 미만의 전부-없음은 정상적으로 푼다 — 브레이크가 실제 고아까지 막지 않는다")
    void allAbsentBelowTheBrake_isAGenuineSmallBacklog_andIsReleased() {
        historyIsSaved();
        when(promotionServiceClient.staleUsedCoupons(60, 200)).thenReturn(absentOrderCoupons(19));
        when(orderServiceClient.existingOrderIds(any())).thenReturn(Set.of());

        job.execute();

        verify(promotionServiceClient, times(19)).release(anyString(), anyString(), anyString());
        assertThat(lastHistory().getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
    }

    @Test
    @DisplayName("AC-7: release 한 건 실패는 나머지를 멈추지 않고, 회차는 COMPLETED")
    void oneReleaseFailure_doesNotStopTheOthers() {
        historyIsSaved();
        when(promotionServiceClient.staleUsedCoupons(60, 200)).thenReturn(absentOrderCoupons(3));
        when(orderServiceClient.existingOrderIds(any())).thenReturn(Set.of());
        // lenient: under STRICT_STUBS, calling release("c-1", …) against a stub keyed on "c-0" raises
        // PotentialStubbingProblem — which the job would catch and count as a release failure,
        // making this test measure the harness instead of the job.
        lenient().doThrow(new RuntimeException("timeout")).when(promotionServiceClient).release("c-0", "o-0", "ecommerce");

        job.execute();

        verify(promotionServiceClient).release("c-1", "o-1", "ecommerce");
        verify(promotionServiceClient).release("c-2", "o-2", "ecommerce");
        assertThat(meterRegistry.find(OrphanCouponReleaseJob.RELEASE_FAILED_COUNTER_NAME).counter().count()).isEqualTo(1.0);
        assertThat(lastHistory().getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
    }

    @Test
    @DisplayName("목록 조회가 실패하면 order-service 를 부르지 않고 FAILED")
    void staleListFails_neverAsksOrderService() {
        historyIsSaved();
        when(promotionServiceClient.staleUsedCoupons(anyInt(), anyInt())).thenThrow(new RuntimeException("promotion down"));

        job.execute();

        verify(orderServiceClient, never()).existingOrderIds(any());
        verify(promotionServiceClient, never()).release(anyString(), anyString(), anyString());
        assertThat(lastHistory().getStatus()).isEqualTo(BatchJobStatus.FAILED);
    }

    @Test
    @DisplayName("빈 목록이면 order-service 를 부르지 않고 COMPLETED")
    void emptyList_completesWithoutAskingOrderService() {
        historyIsSaved();
        when(promotionServiceClient.staleUsedCoupons(60, 200)).thenReturn(List.of());

        job.execute();

        verify(orderServiceClient, never()).existingOrderIds(any());
        assertThat(lastHistory().getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
    }

    @Test
    @DisplayName("꺼져 있으면 아무것도 하지 않는다")
    void disabled_doesNothing() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.execute();

        verify(executionRepository, never()).save(any());
        verify(promotionServiceClient, never()).staleUsedCoupons(anyInt(), anyInt());
    }
}
