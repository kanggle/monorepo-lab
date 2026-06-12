package com.example.shipping.infrastructure.webhook;

import com.example.shipping.application.service.CarrierWebhookCleanupService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-5 net-zero guard (TASK-BE-361): the {@link CarrierWebhookCleanupScheduler} bean is gated by
 * {@code @ConditionalOnProperty(shipping.carrier.webhook.cleanup.enabled=true)}. With the
 * property absent (the default) or {@code false}, the scheduler bean must NOT be created — so no
 * {@code @Scheduled} tick ever runs and the dedup table is left byte-identical to the baseline.
 * It is created only when the property is explicitly {@code true}.
 *
 * <p>Uses {@link ApplicationContextRunner} (Docker-free, no Spring Boot test context) so this
 * runs in the fast {@code :check} lane.
 */
class CarrierWebhookCleanupSchedulerGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(StubConfig.class, CarrierWebhookCleanupScheduler.class);

    @Test
    void schedulerBeanAbsent_whenPropertyDefault() {
        runner.run(context ->
                assertThat(context).doesNotHaveBean(CarrierWebhookCleanupScheduler.class));
    }

    @Test
    void schedulerBeanAbsent_whenExplicitlyFalse() {
        runner.withPropertyValues("shipping.carrier.webhook.cleanup.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(CarrierWebhookCleanupScheduler.class));
    }

    @Test
    void schedulerBeanPresent_whenExplicitlyTrue() {
        runner.withPropertyValues("shipping.carrier.webhook.cleanup.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(CarrierWebhookCleanupScheduler.class));
    }

    @Configuration
    static class StubConfig {
        @Bean
        CarrierWebhookCleanupService carrierWebhookCleanupService() {
            return Mockito.mock(CarrierWebhookCleanupService.class);
        }
    }
}
