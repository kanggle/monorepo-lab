package com.wms.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.web.idempotency.IdempotencyKeyFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.adapter.out.idempotency.InMemoryIdempotencyStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

/**
 * Wiring proof for TASK-BE-505: inventory-service must register the shared
 * {@link IdempotencyKeyFilter} on its mutating REST path. Before this task the
 * {@code IdempotencyConfig} built only the {@code IdempotencyStore} bean and
 * nothing consumed it — this test would not compile (the registration bean
 * method did not exist), which is exactly the gap the task closes. It pins the
 * three properties the fleet's other services rely on: the filter type, the
 * {@code /api/v1/inventory/*} URL mapping, and the post-Security ordering.
 */
class IdempotencyConfigTest {

    @Test
    @DisplayName("IdempotencyConfig registers the shared IdempotencyKeyFilter on /api/v1/inventory/* after Spring Security")
    void registersIdempotencyFilterOnInventoryPath() {
        IdempotencyConfig config = new IdempotencyConfig();

        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> meterProvider = mock(ObjectProvider.class);
        when(meterProvider.getIfAvailable()).thenReturn(new SimpleMeterRegistry());

        FilterRegistrationBean<IdempotencyKeyFilter> registration =
                config.idempotencyFilterRegistration(
                        new InMemoryIdempotencyStore(),
                        new ObjectMapper().findAndRegisterModules(),
                        meterProvider);

        assertThat(registration).isNotNull();
        assertThat(registration.getFilter()).isInstanceOf(IdempotencyKeyFilter.class);
        assertThat(registration.getUrlPatterns()).containsExactly("/api/v1/inventory/*");
        assertThat(registration.getOrder())
                .as("must run after Spring Security (HIGHEST_PRECEDENCE) but before DispatcherServlet")
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 20);
    }

    @Test
    @DisplayName("falls back to NO_OP metrics when no MeterRegistry is present")
    void noMeterRegistry_stillRegistersFilter() {
        IdempotencyConfig config = new IdempotencyConfig();

        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> meterProvider = mock(ObjectProvider.class);
        when(meterProvider.getIfAvailable()).thenReturn(null);

        FilterRegistrationBean<IdempotencyKeyFilter> registration =
                config.idempotencyFilterRegistration(
                        new InMemoryIdempotencyStore(),
                        new ObjectMapper().findAndRegisterModules(),
                        meterProvider);

        assertThat(registration.getFilter()).isInstanceOf(IdempotencyKeyFilter.class);
    }
}
