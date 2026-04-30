package com.example.messaging.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxMetricsAutoConfiguration 조건부 동작 테스트")
class OutboxMetricsAutoConfigurationConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OutboxMetricsAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues("spring.application.name=auth-service");

    @Test
    @DisplayName("MeterRegistry가 classpath에 없으면 auto-configuration이 건너뛰어진다")
    void noMeterRegistryOnClasspath_autoConfigurationSkipped() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OutboxFailureHandler.class));
    }

    @Test
    @DisplayName("사용자가 직접 OutboxFailureHandler를 등록하면 auto-configuration이 양보한다")
    void userDefinedHandler_autoConfigurationBacksOff() {
        contextRunner
                .withUserConfiguration(UserHandlerConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(OutboxFailureHandler.class);
                    assertThat(ctx.getBean(OutboxFailureHandler.class))
                            .isSameAs(UserHandlerConfig.SENTINEL);
                });
    }

    @Test
    @DisplayName("사용자 정의 핸들러가 없으면 default 핸들러가 등록된다")
    void noUserHandler_defaultHandlerRegistered() {
        contextRunner.run(ctx ->
                assertThat(ctx).hasSingleBean(OutboxFailureHandler.class));
    }

    @Configuration
    static class UserHandlerConfig {
        static final OutboxFailureHandler SENTINEL = (eventType, aggregateId, e) -> { /* no-op */ };

        @Bean
        OutboxFailureHandler userHandler() {
            return SENTINEL;
        }
    }
}
