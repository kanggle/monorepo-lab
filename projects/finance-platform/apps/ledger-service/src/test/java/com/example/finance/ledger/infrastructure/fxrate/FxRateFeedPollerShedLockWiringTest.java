package com.example.finance.ledger.infrastructure.fxrate;

import com.example.finance.ledger.infrastructure.config.SchedulerConfig;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring assertions for the ShedLock single-leader guard introduced in TASK-FIN-BE-041.
 *
 * <p>True single-leader behaviour (only one replica executes a given tick) is a multi-instance
 * deployment property validated by the {@code shedlock} table + a running cluster — not a unit
 * test. These assertions verify the structural wiring that makes the runtime guarantee possible:
 * (a) {@link FxRateFeedPoller#poll()} carries {@code @SchedulerLock} with a stable, non-blank
 * lock name, and (b) {@link SchedulerConfig} is wired to expose a {@link LockProvider} bean. The
 * integration test ({@code LedgerFxRateFeedIntegrationTest}, {@code @Tag("integration")}) exercises
 * actual Spring context wiring against a real MySQL container.
 *
 * <p>No Spring context required — pure reflection.
 */
class FxRateFeedPollerShedLockWiringTest {

    // -----------------------------------------------------------------------
    // AC-1: FxRateFeedPoller.poll() carries @SchedulerLock with a stable name
    // -----------------------------------------------------------------------

    @Test
    void poll_method_is_annotated_with_SchedulerLock() throws NoSuchMethodException {
        Method pollMethod = FxRateFeedPoller.class.getDeclaredMethod("poll");
        assertThat(pollMethod.isAnnotationPresent(SchedulerLock.class))
                .as("FxRateFeedPoller.poll() must be annotated with @SchedulerLock (AC-1)")
                .isTrue();
    }

    @Test
    void poll_SchedulerLock_has_stable_non_blank_name() throws NoSuchMethodException {
        Method pollMethod = FxRateFeedPoller.class.getDeclaredMethod("poll");
        SchedulerLock lock = pollMethod.getAnnotation(SchedulerLock.class);
        assertThat(lock.name())
                .as("@SchedulerLock name must be a stable, non-blank string (AC-1)")
                .isNotBlank()
                .isEqualTo("ledger-fx-rate-poll");
    }

    @Test
    void poll_SchedulerLock_lockAtMostFor_is_set() throws NoSuchMethodException {
        Method pollMethod = FxRateFeedPoller.class.getDeclaredMethod("poll");
        SchedulerLock lock = pollMethod.getAnnotation(SchedulerLock.class);
        assertThat(lock.lockAtMostFor())
                .as("@SchedulerLock lockAtMostFor must be set to bound stale locks on instance crash")
                .isNotBlank();
    }

    @Test
    void poll_retains_Scheduled_annotation() throws NoSuchMethodException {
        Method pollMethod = FxRateFeedPoller.class.getDeclaredMethod("poll");
        assertThat(pollMethod.isAnnotationPresent(org.springframework.scheduling.annotation.Scheduled.class))
                .as("FxRateFeedPoller.poll() must retain @Scheduled — existing scheduling unchanged (AC-1/AC-5)")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // AC-2: SchedulerConfig declares @EnableSchedulerLock
    // -----------------------------------------------------------------------

    @Test
    void SchedulerConfig_is_annotated_with_EnableSchedulerLock() {
        assertThat(SchedulerConfig.class.isAnnotationPresent(
                net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.class))
                .as("SchedulerConfig must carry @EnableSchedulerLock (AC-2)")
                .isTrue();
    }

    @Test
    void SchedulerConfig_lockProvider_method_exists_and_returns_LockProvider() throws NoSuchMethodException {
        Method lockProviderMethod = SchedulerConfig.class.getDeclaredMethod("lockProvider", javax.sql.DataSource.class);
        assertThat(LockProvider.class.isAssignableFrom(lockProviderMethod.getReturnType()))
                .as("SchedulerConfig.lockProvider() must return a LockProvider (AC-2)")
                .isTrue();
    }
}
