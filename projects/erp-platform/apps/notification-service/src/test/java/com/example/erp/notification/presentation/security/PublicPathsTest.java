package com.example.erp.notification.presentation.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Pins {@code PublicPaths}' own data and classification, independent of the security-chain
 * plumbing exercised elsewhere ({@code ErpTenantGatePolicyTest}).
 *
 * <p>ADR-MONO-058 § D5 promoted the matching <em>mechanism</em> to
 * {@code com.example.security.servlet.PublicPathSet}; this service's {@code EXACT}/
 * {@code PREFIXES} data stays exactly as it was before the promotion. This suite is the guard
 * against a copy-paste error dropping or adding an entry during that swap.
 */
@DisplayName("notification-service — PublicPaths data + classification, pinned")
class PublicPathsTest {

    @Test
    @DisplayName("EXACT is unchanged")
    void exactSetUnchanged() {
        assertThat(PublicPaths.EXACT)
                .containsExactlyInAnyOrder(
                        "/actuator/health", "/actuator/info", "/actuator/prometheus");
    }

    @Test
    @DisplayName("PREFIXES is unchanged")
    void prefixesSetUnchanged() {
        assertThat(PublicPaths.PREFIXES).containsExactlyInAnyOrder("/actuator/health/");
    }

    @Test
    @DisplayName("classification parity — before/after the PublicPathSet delegation swap")
    void classificationParityBeforeAndAfter() {
        Set<String> expectedPublic =
                Set.of(
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness");
        Set<String> expectedNotPublic =
                Set.of(
                        "/actuator/env",
                        "/actuator/heapdump",
                        "/api/erp/notifications/ntf-1",
                        "/internal/notification/access-check");

        for (String path : expectedPublic) {
            assertThat(PublicPaths.isPublic(path)).as("path=%s", path).isTrue();
        }
        for (String path : expectedNotPublic) {
            assertThat(PublicPaths.isPublic(path)).as("path=%s", path).isFalse();
        }
    }

    @Test
    @DisplayName("isPublic(HttpServletRequest) overload delegates to the same mechanism")
    void requestOverloadAgreesWithStringOverload() {
        MockHttpServletRequest publicRequest = new MockHttpServletRequest("GET", "/actuator/health");
        publicRequest.setRequestURI("/actuator/health");
        assertThat(PublicPaths.isPublic(publicRequest)).isTrue();

        MockHttpServletRequest privateRequest = new MockHttpServletRequest("GET", "/actuator/env");
        privateRequest.setRequestURI("/actuator/env");
        assertThat(PublicPaths.isPublic(privateRequest)).isFalse();
    }

    @Test
    @DisplayName("null path is never public")
    void nullPathIsNotPublic() {
        assertThat(PublicPaths.isPublic((String) null)).isFalse();
    }
}
