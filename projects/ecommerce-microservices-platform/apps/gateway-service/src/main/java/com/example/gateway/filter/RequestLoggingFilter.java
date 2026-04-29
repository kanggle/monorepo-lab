package com.example.gateway.filter;

import com.example.gateway.config.GatewayMetrics;
import com.example.gateway.security.RouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -99;

    private final GatewayMetrics gatewayMetrics;
    private final RouteService routeService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();

        return chain.filter(exchange).doFinally(signalType -> {
            long latency = System.currentTimeMillis() - startTime;
            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;
            log.info("{} {} {} {}ms",
                    request.getMethod(),
                    request.getPath().value(),
                    statusCode,
                    latency);

            String targetService = routeService.resolveTargetService(request.getPath().value());
            gatewayMetrics.incrementRequestsRouted(targetService);
            if (statusCode == 429) {
                gatewayMetrics.incrementRateLimited(targetService);
            }
            if (statusCode >= 500) {
                gatewayMetrics.incrementUpstreamError(targetService);
            }
        });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
