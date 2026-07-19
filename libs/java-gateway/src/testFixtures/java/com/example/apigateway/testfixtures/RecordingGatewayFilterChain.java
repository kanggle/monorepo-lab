package com.example.apigateway.testfixtures;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * A {@link GatewayFilterChain} test-double that records whether the filter under test delegated
 * downstream ({@link #wasCalled()}) and, if so, the {@link ServerWebExchange} it forwarded
 * ({@link #capturedExchange()}).
 *
 * <p>This collapses ~6 near-identical private {@code CapturingChain} copies that lived across the
 * gateway filter suites (4 in {@code libs/java-gateway}, 2 in finance's gateway-service). Two shapes
 * had drifted — some copies exposed only a {@code boolean called} flag, others only the captured
 * exchange — so this single double exposes both, and each caller reads whichever it asserts on. No
 * behaviour changes: it still returns {@link Mono#empty()} without touching the exchange, exactly as
 * every copy did (TASK-MONO-429).
 */
public final class RecordingGatewayFilterChain implements GatewayFilterChain {

    private boolean called = false;
    private ServerWebExchange captured;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange) {
        this.called = true;
        this.captured = exchange;
        return Mono.empty();
    }

    /** Whether the filter under test delegated downstream (i.e. did not short-circuit). */
    public boolean wasCalled() {
        return called;
    }

    /** The exchange the filter forwarded, or {@code null} if it never delegated. */
    public ServerWebExchange capturedExchange() {
        return captured;
    }
}
