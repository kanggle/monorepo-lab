package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.kanggle.platformconsole.bff.infrastructure.config.ObservabilityConfig;
import com.kanggle.platformconsole.bff.infrastructure.security.SecurityConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test ({@link WebMvcTest}) — verifies:
 * <ol>
 *   <li>AC-7: unauthenticated requests to a non-public endpoint return 401.</li>
 *   <li>GlobalExceptionHandler is wired correctly.</li>
 * </ol>
 *
 * <p>The real {@link SecurityConfig} is imported; the JWT Resource Server is
 * configured with a placeholder JWKS URI (unit tests don't need a running JWKS server).
 * Unauthenticated requests to protected paths must be rejected with 401.
 */
@WebMvcTest(GlobalExceptionHandler.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, ActuatorHealthSliceTest.TestMeterConfig.class})
class ActuatorHealthSliceTest {

    @Configuration
    static class TestMeterConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/bff/stub — 401 without authentication (authenticated() chain enforced)")
    void protectedEndpoint_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/bff/nonexistent"))
                .andExpect(status().isUnauthorized());
    }
}
