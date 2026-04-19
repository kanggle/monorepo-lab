package com.wms.gateway.filter;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Ensures every request carries {@code X-Request-Id}. If the client supplied a
 * value, it is echoed back on the response; otherwise a UUID v4 is generated.
 * Runs after the identity-strip filter so an incoming {@code X-Request-Id}
 * (which is not an identity header) is preserved.
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders incoming = exchange.getRequest().getHeaders();
        String requestId = incoming.getFirst(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        final String finalRequestId = requestId;

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER, finalRequestId)
                .build();
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set(HEADER, finalRequestId);
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
