package com.wms.admin.config;

import com.wms.admin.infra.observability.ReadModelLagHeaderInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the {@link ReadModelLagHeaderInterceptor} into the dashboard +
 * operations URL space.
 *
 * <p>Path patterns deliberately exclude write surfaces (users / roles /
 * assignments / settings) — the lag header is meaningful only for read-model
 * queries.
 *
 * <p>Uses {@link ObjectProvider} so {@code @WebMvcTest} slices that don't pull
 * in {@code MetricsAutoConfiguration} can still load this configuration; the
 * interceptor falls back to a {@link SimpleMeterRegistry} that simply has no
 * timers, so the header is never emitted (the gating contract: emit only when
 * {@code worstLagSeconds > threshold}, which an empty registry cannot satisfy).
 */
@Configuration
public class DashboardWebMvcConfig implements WebMvcConfigurer {

    private final MeterRegistry meterRegistry;
    private final double thresholdSeconds;

    public DashboardWebMvcConfig(ObjectProvider<MeterRegistry> meterRegistryProvider,
                                 @Value("${admin.read-model-lag.threshold-seconds:5.0}")
                                         double thresholdSeconds) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
        this.thresholdSeconds = thresholdSeconds;
    }

    @Bean
    public ReadModelLagHeaderInterceptor readModelLagHeaderInterceptor() {
        return new ReadModelLagHeaderInterceptor(meterRegistry, thresholdSeconds);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(readModelLagHeaderInterceptor())
                .addPathPatterns("/api/v1/admin/dashboard/**", "/api/v1/admin/operations/**");
    }
}
