package com.example.scmplatform.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.filter.RequestIdFilter;
import com.example.apigateway.filter.RetryAfterFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the edge filter chain order across the library / service boundary.
 *
 * <p>{@code RequestIdFilter} and {@code RetryAfterFilter} moved to
 * {@code libs/java-gateway} (TASK-MONO-351) while {@link IdentityHeaderStripFilter}
 * stayed here — its strip set is per-domain until ADR-MONO-048 D7 step 2. The ordering
 * invariant now spans both, so it can only be asserted here.
 *
 * <p>The invariant is load-bearing, not cosmetic: identity headers must be stripped
 * <em>before</em> anything downstream is allowed to trust a header. Previously the
 * "strip runs first" half of this assertion lived inside this domain's copy of
 * {@code RequestIdFilterTest}; moving that test to the library would have dropped it,
 * since the library cannot see the strip filter. This test is where it survives.
 */
@DisplayName("게이트웨이 필터 순서 — strip → requestId → retryAfter")
class GatewayFilterOrderingTest {

    private final IdentityHeaderStripFilter strip = new IdentityHeaderStripFilter();
    private final RequestIdFilter requestId = new RequestIdFilter();
    private final RetryAfterFilter retryAfter = new RetryAfterFilter();

    @Test
    void identityStripRunsBeforeEverythingElse() {
        assertThat(strip.getOrder())
                .as("a header may not be trusted before the client's copy of it is removed")
                .isLessThan(requestId.getOrder())
                .isLessThan(retryAfter.getOrder());
    }

    @Test
    void requestIdRunsBeforeRetryAfter() {
        assertThat(requestId.getOrder()).isLessThan(retryAfter.getOrder());
    }
}
