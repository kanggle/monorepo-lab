package com.example.community.infrastructure.client;

import com.example.community.domain.access.ContentAccessChecker;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Spring-managed CircuitBreaker wrapping
 * {@link MembershipAccessClient#check(String, String)} is fail-closed:
 * when membership-service returns 503 repeatedly, the CB fallback
 * ({@code denyFallback}) returns {@code false} instead of propagating the error.
 */
@SpringBootTest(
        classes = MembershipAccessClientCbTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class MembershipAccessClientCbTest {

    private static WireMockServer wm;

    @BeforeAll
    static void startWireMock() {
        wm = new WireMockServer(wireMockConfig().dynamicPort());
        wm.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wm != null) wm.stop();
    }

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterEach
    void resetStubs() {
        wm.resetAll();
        circuitBreakerRegistry.circuitBreaker("membershipService").reset();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("community.membership-service.base-url", () -> "http://localhost:" + wm.port());
        // Timeouts tight enough to exercise failure path but generous enough for slow
        // CI runners on cold start (first WireMock response can exceed 500ms in CI).
        registry.add("community.membership-service.connect-timeout-ms", () -> "2000");
        registry.add("community.membership-service.read-timeout-ms", () -> "3000");
        // Tighten CB so it trips quickly in the test.
        registry.add("resilience4j.circuitbreaker.instances.membershipService.sliding-window-type", () -> "COUNT_BASED");
        registry.add("resilience4j.circuitbreaker.instances.membershipService.sliding-window-size", () -> "4");
        registry.add("resilience4j.circuitbreaker.instances.membershipService.minimum-number-of-calls", () -> "4");
        registry.add("resilience4j.circuitbreaker.instances.membershipService.failure-rate-threshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.instances.membershipService.wait-duration-in-open-state", () -> "10s");
        registry.add("resilience4j.circuitbreaker.instances.membershipService.permitted-number-of-calls-in-half-open-state", () -> "1");
        registry.add("resilience4j.circuitbreaker.instances.membershipService.automatic-transition-from-open-to-half-open-enabled", () -> "false");
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class})
    @Import(MembershipAccessClient.class)
    static class TestApp {
    }

    @Autowired
    ContentAccessChecker contentAccessChecker;

    @Test
    void returns_false_when_membership_service_returns_503() {
        wm.stubFor(get(urlPathEqualTo("/internal/membership/access"))
                .willReturn(aResponse().withStatus(503)));

        // Each call triggers a raw exception. The Spring-managed CB wrapping
        // the @CircuitBreaker method must fall back to denyFallback -> false.
        for (int i = 0; i < 6; i++) {
            assertThat(contentAccessChecker.check("fan-1", "FAN_CLUB"))
                    .as("Call #%d must be fail-closed (false)", i)
                    .isFalse();
        }
    }

    @Test
    void returns_true_on_healthy_allowed_response() {
        wm.stubFor(get(urlPathEqualTo("/internal/membership/access"))
                .willReturn(okJson("""
                        {"accountId":"fan-1","requiredPlanLevel":"FAN_CLUB","allowed":true,"activePlanLevel":"FAN_CLUB"}
                        """)));

        assertThat(contentAccessChecker.check("fan-1", "FAN_CLUB")).isTrue();
    }
}
