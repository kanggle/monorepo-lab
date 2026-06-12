package com.example.shipping.infrastructure.carrier;

import com.example.shipping.application.service.AutoCollectTrackingService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-3 net-zero guard (TASK-BE-360): the {@link AutoCollectTrackingScheduler} bean is gated by
 * {@code @ConditionalOnProperty(shipping.carrier.auto-collect.enabled=true)}. With the property
 * absent (the default) or {@code false}, the scheduler bean must NOT be created — so no
 * {@code @Scheduled} tick ever runs and behaviour stays byte-identical to the admin-driven
 * baseline. It is created only when the property is explicitly {@code true}.
 *
 * <p>Uses {@link ApplicationContextRunner} (Docker-free, no Spring Boot test context) so this
 * runs in the fast {@code :check} lane.
 */
class AutoCollectTrackingSchedulerGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(StubConfig.class, AutoCollectTrackingScheduler.class);

    @Test
    void schedulerBeanAbsent_whenPropertyDefault() {
        runner.run(context ->
                assertThat(context).doesNotHaveBean(AutoCollectTrackingScheduler.class));
    }

    @Test
    void schedulerBeanAbsent_whenExplicitlyFalse() {
        runner.withPropertyValues("shipping.carrier.auto-collect.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AutoCollectTrackingScheduler.class));
    }

    @Test
    void schedulerBeanPresent_whenExplicitlyTrue() {
        runner.withPropertyValues("shipping.carrier.auto-collect.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(AutoCollectTrackingScheduler.class));
    }

    @Configuration
    static class StubConfig {
        @Bean
        AutoCollectTrackingService autoCollectTrackingService() {
            return Mockito.mock(AutoCollectTrackingService.class);
        }
    }
}
