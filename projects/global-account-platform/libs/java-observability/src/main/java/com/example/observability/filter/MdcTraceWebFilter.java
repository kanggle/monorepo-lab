package com.example.observability.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcTraceWebFilter implements WebFilter {

    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = extractTraceId();
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(TRACE_ID_KEY, traceId))
                .doFirst(() -> MDC.put(TRACE_ID_KEY, traceId))
                .doFinally(signal -> MDC.remove(TRACE_ID_KEY));
    }

    private String extractTraceId() {
        try {
            SpanContext spanContext = Span.current().getSpanContext();
            if (spanContext.isValid()) {
                return spanContext.getTraceId();
            }
        } catch (Exception ignored) {
            // OpenTelemetry not available
        }
        return "";
    }
}
