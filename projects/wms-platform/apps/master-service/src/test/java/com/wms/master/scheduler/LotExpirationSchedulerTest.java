package com.wms.master.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wms.master.application.port.in.ExpireLotsBatchUseCase;
import com.wms.master.application.port.in.ExpireLotsBatchUseCase.LotExpirationResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Unit tests for {@link LotExpirationScheduler}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Happy path — use case returns result; scheduler logs and returns
 *   <li>Top-level failure isolation — use case throws; scheduler catches and returns empty result
 *   <li>Disabled property guard — bean is NOT created when
 *       {@code wms.scheduler.lot-expiration.enabled=false}
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LotExpirationSchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 4, 20);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-04-20T00:05:00Z"), ZoneId.of("UTC"));

    // ---------- happy path ----------

    @Test
    @DisplayName("runNow 위임: 사용 사례가 성공 결과를 반환하면 스케줄러가 동일한 결과를 반환한다")
    void runNow_happyPath_returnsUseCaseResult() {
        ExpireLotsBatchUseCase useCase = mock(ExpireLotsBatchUseCase.class);
        LotExpirationResult expected = new LotExpirationResult(5, 5, 0);
        when(useCase.execute(TODAY)).thenReturn(expected);

        LotExpirationScheduler scheduler = new LotExpirationScheduler(useCase, FIXED_CLOCK);
        LotExpirationResult result = scheduler.runNow();

        assertThat(result.considered()).isEqualTo(5);
        assertThat(result.expired()).isEqualTo(5);
        assertThat(result.failed()).isZero();
        verify(useCase).execute(TODAY);
    }

    @Test
    @DisplayName("runScheduled 위임: runScheduled는 runNow와 동일한 경로로 사용 사례에 위임한다")
    void runScheduled_delegatesToRunNow() {
        ExpireLotsBatchUseCase useCase = mock(ExpireLotsBatchUseCase.class);
        LotExpirationResult expected = new LotExpirationResult(3, 2, 1);
        when(useCase.execute(TODAY)).thenReturn(expected);

        LotExpirationScheduler scheduler = new LotExpirationScheduler(useCase, FIXED_CLOCK);
        // runScheduled() calls runNow() internally
        scheduler.runScheduled();

        verify(useCase).execute(TODAY);
    }

    // ---------- failure isolation ----------

    @Test
    @DisplayName("최상위 예외 격리: 사용 사례가 RuntimeException을 던지면 스케줄러가 잡아서 빈 결과를 반환한다")
    void runNow_useCaseThrows_schedulerCatchesAndReturnsEmptyResult() {
        ExpireLotsBatchUseCase useCase = mock(ExpireLotsBatchUseCase.class);
        when(useCase.execute(any(LocalDate.class)))
                .thenThrow(new RuntimeException("simulated batch failure"));

        LotExpirationScheduler scheduler = new LotExpirationScheduler(useCase, FIXED_CLOCK);

        // The scheduler must NOT propagate the exception to the caller (scheduled thread).
        LotExpirationResult result = scheduler.runNow();

        assertThat(result).isNotNull();
        assertThat(result.considered()).isZero();
        assertThat(result.expired()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    @DisplayName("빈 배치: 만료 대상이 없을 때도 정상 결과를 반환한다")
    void runNow_emptyBatch_returnsZeroCounts() {
        ExpireLotsBatchUseCase useCase = mock(ExpireLotsBatchUseCase.class);
        when(useCase.execute(TODAY)).thenReturn(new LotExpirationResult(0, 0, 0));

        LotExpirationScheduler scheduler = new LotExpirationScheduler(useCase, FIXED_CLOCK);
        LotExpirationResult result = scheduler.runNow();

        assertThat(result.considered()).isZero();
        assertThat(result.expired()).isZero();
        assertThat(result.failed()).isZero();
    }

    // ---------- disabled-property guard ----------

    @Test
    @DisplayName("비활성 속성: wms.scheduler.lot-expiration.enabled=false 이면 빈이 생성되지 않는다")
    void scheduler_whenDisabled_beanIsNotCreated() {
        ExpireLotsBatchUseCase useCase = mock(ExpireLotsBatchUseCase.class);
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("wms.scheduler.lot-expiration.enabled=false").applyTo(ctx);
            ctx.registerBean("expireLotsBatchUseCase", ExpireLotsBatchUseCase.class, () -> useCase);
            ctx.register(LotExpirationScheduler.class);
            ctx.refresh();

            // Class-level @ConditionalOnProperty(havingValue="true") blocks creation.
            assertThatThrownBy(() -> ctx.getBean(LotExpirationScheduler.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }

    @Test
    @DisplayName("기본 활성: wms.scheduler.lot-expiration.enabled 없을 때 matchIfMissing=true 로 빈이 생성된다")
    void scheduler_whenPropertyMissing_beanIsCreated_matchIfMissingTrue() {
        // No property set → matchIfMissing=true → bean should be created.
        ExpireLotsBatchUseCase useCase = mock(ExpireLotsBatchUseCase.class);
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean("expireLotsBatchUseCase", ExpireLotsBatchUseCase.class, () -> useCase);
            ctx.register(LotExpirationScheduler.class);
            ctx.refresh();

            LotExpirationScheduler scheduler = ctx.getBean(LotExpirationScheduler.class);
            assertThat(scheduler).isNotNull();
        }
    }

}
