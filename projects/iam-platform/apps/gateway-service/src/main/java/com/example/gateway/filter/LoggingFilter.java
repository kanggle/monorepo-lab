package com.example.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global logging filter that records HTTP method, path, status code, and latency.
 * Runs before all other filters (order = -300).
 * MUST NOT log Authorization header values (security rule).
 *
 * Sets MDC context (request-id, method, path) for structured logging.
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -300;

    static final String MDC_REQUEST_ID = "request-id";
    static final String MDC_METHOD = "method";
    static final String MDC_PATH = "path";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getPath().value();
        String requestId = request.getHeaders().getFirst("X-Request-ID");

        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(MDC_REQUEST_ID, requestId != null ? requestId : ""))
                .doFirst(() -> {
                    if (requestId != null) {
                        MDC.put(MDC_REQUEST_ID, requestId);
                    }
                    MDC.put(MDC_METHOD, method);
                    MDC.put(MDC_PATH, path);
                })
                .doFinally(signal -> {
                    long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    int status = statusCode != null ? statusCode.value() : 0;

                    log.info("HTTP {} {} {} {}ms", method, path, status, latencyMs);
                    MDC.remove(MDC_REQUEST_ID);
                    MDC.remove(MDC_METHOD);
                    MDC.remove(MDC_PATH);
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
