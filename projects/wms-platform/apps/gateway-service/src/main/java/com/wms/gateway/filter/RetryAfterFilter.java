package com.wms.gateway.filter;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds {@code Retry-After: 1} to every 429 response that does not already carry
 * the header. Required by {@code platform/api-gateway-policy.md} and
 * {@code specs/services/gateway-service/architecture.md} § Error Matrix.
 *
 * <p>Uses a response decorator so the header is injected immediately before the
 * response is written — after {@link org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter}
 * sets the status to {@code TOO_MANY_REQUESTS} but before the bytes reach the
 * client.
 */
@Component
public class RetryAfterFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {

            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                addRetryAfterIfRateLimited();
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> writeAndFlushWith(
                    Publisher<? extends Publisher<? extends DataBuffer>> body) {
                addRetryAfterIfRateLimited();
                return super.writeAndFlushWith(body);
            }

            @Override
            public Mono<Void> setComplete() {
                addRetryAfterIfRateLimited();
                return super.setComplete();
            }

            private void addRetryAfterIfRateLimited() {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode())
                        && !getHeaders().containsKey(HttpHeaders.RETRY_AFTER)) {
                    getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
                }
            }
        };
        return chain.filter(exchange.mutate().response(decorated).build());
    }

    @Override
    public int getOrder() {
        // Run after security (HIGHEST_PRECEDENCE) and RequestId filters so
        // the decorator wraps the final response write — not the innermost one.
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
