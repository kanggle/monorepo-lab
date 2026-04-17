package com.example.observability.config;

import com.example.observability.filter.MdcTraceFilter;
import io.micrometer.core.instrument.config.MeterFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfig.class))
            .withPropertyValues("spring.application.name=test-service");

    @Test
    @DisplayName("서블릿 환경에서 MdcTraceFilter 빈이 등록된다")
    void mdcTraceFilter_registered_inServletEnvironment() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(MdcTraceFilter.class));
    }

    @Test
    @DisplayName("Micrometer 공통 태그 MeterFilter가 등록된다")
    void commonTagsMeterFilter_registered() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(MeterFilter.class));
    }
}
