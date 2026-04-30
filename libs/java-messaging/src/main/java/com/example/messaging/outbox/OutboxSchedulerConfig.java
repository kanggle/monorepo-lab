package com.example.messaging.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Dedicated {@link ThreadPoolTaskScheduler} for {@link OutboxPollingScheduler}.
 *
 * <h2>Why a dedicated scheduler (TASK-BE-077)</h2>
 *
 * <p>The default Spring {@code @Scheduled} thread pool is a singleton that
 * outlives individual {@code ApplicationContext} instances inside a test JVM.
 * When Testcontainers-based integration tests rotate Spring contexts, the
 * orphaned {@code scheduling-1} thread kept polling with a closure that
 * captured the destroyed context's HikariCP pool, producing
 * {@code HikariPool-N Connection is not available ... total=0} and
 * {@code CannotCreateTransactionException} on every tick (diagnosed from PR
 * #44 / TASK-BE-076 CI artifacts).
 *
 * <p>Exposing the scheduler as a context-scoped bean with
 * {@code destroyMethod = "shutdown"} ties its thread pool's lifetime to the
 * owning {@code ApplicationContext}. When the context shuts down, the
 * executor is terminated; no subsequent tick can reference the destroyed
 * pool. Combined with {@code OutboxPollingScheduler.stop()} (which cancels
 * the {@code ScheduledFuture} before this bean is destroyed), the polling
 * loop unwinds deterministically on context close.
 *
 * <h2>Shutdown ordering</h2>
 *
 * <p>Spring destroys beans in reverse-dependency order. The subclass
 * {@code OutboxPollingScheduler} depends on the scheduler bean (it is
 * injected via constructor on the auto-configured base), so the scheduler
 * is torn down after the polling bean's {@code @PreDestroy} has cancelled
 * in-flight ticks. With {@code setWaitForTasksToCompleteOnShutdown(true)}
 * and {@code setAwaitTerminationSeconds(5)}, any currently-running
 * {@code pollAndPublish} call finishes within 5 seconds before the pool is
 * forcibly terminated.
 *
 * <h2>Disabling the scheduler</h2>
 *
 * <p>Set {@code outbox.polling.enabled=false} to skip both this bean and
 * the {@link OutboxPollingScheduler} wiring (see
 * {@code OutboxAutoConfiguration}). Production leaves the default
 * ({@code true}). Test profiles may opt out when they do not exercise the
 * relay path.
 */
@Configuration
@ConditionalOnProperty(name = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxSchedulerConfig {

    /**
     * Dedicated single-thread scheduler for outbox polling. The bean is
     * destroyed with the owning {@code ApplicationContext}, so its threads
     * cannot outlive the context that built them.
     */
    @Bean(name = "outboxTaskScheduler", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "outboxTaskScheduler")
    public ThreadPoolTaskScheduler outboxTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("outbox-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}
