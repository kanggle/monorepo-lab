package com.wms.outbound.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cross-cutting beans: clock, MDC filter for traceId/requestId/actorId, and a
 * {@link TransactionTemplate} for background processors that need explicit TX
 * boundaries.
 *
 * <p>The MDC filter populates {@code requestId} and {@code traceId} for every
 * inbound HTTP request so the structured-log pattern in
 * {@code application.yml} carries them on every line.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }

    @Bean
    FilterRegistrationBean<Filter> mdcFilter() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>(new MdcFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }

    /**
     * Populates MDC keys {@code requestId}, {@code traceId} for every request.
     * Existing values from inbound headers are preserved; otherwise a fresh
     * UUID is minted.
     */
    static final class MdcFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            String requestId = null;
            String traceId = null;
            if (request instanceof HttpServletRequest http) {
                requestId = http.getHeader("X-Request-Id");
                traceId = http.getHeader("X-B3-TraceId");
            }
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }
            if (traceId == null || traceId.isBlank()) {
                traceId = requestId;
            }
            MDC.put("requestId", requestId);
            MDC.put("traceId", traceId);
            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove("requestId");
                MDC.remove("traceId");
            }
        }
    }
}
