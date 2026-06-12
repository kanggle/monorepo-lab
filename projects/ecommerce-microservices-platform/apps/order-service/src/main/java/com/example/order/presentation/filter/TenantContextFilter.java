package com.example.order.presentation.filter;

import com.example.order.domain.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds the request's tenant context (ADR-MONO-030 §2.2 M2 layer 2). Reads the
 * gateway-injected {@code X-Tenant-Id} header — already validated by the gateway
 * {@code TenantClaimValidator} (entitlement-trust, increment A) — into
 * {@link TenantContext} for the duration of the request, then clears it.
 *
 * <p>order-service trusts gateway-derived identity headers ({@code X-User-Id},
 * {@code X-User-Role}); it runs no Spring Security filter chain, so this is the
 * service-side tenant entry point. A missing/blank header (standalone, public
 * no-tenant token) leaves the context unset → {@link TenantContext#currentTenant()}
 * yields the default tenant (net-zero, D8). Runs early so the context is present
 * before any controller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantContextFilter extends OncePerRequestFilter {

    static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            TenantContext.set(request.getHeader(TENANT_HEADER));
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
