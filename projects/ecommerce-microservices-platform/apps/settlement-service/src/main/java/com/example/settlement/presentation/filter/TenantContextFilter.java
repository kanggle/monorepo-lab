package com.example.settlement.presentation.filter;

import com.example.settlement.domain.tenant.TenantContext;
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
 * Binds the request's tenant context (ADR-MONO-030 §2.2 M2 layer 2) from the
 * gateway-injected {@code X-Tenant-Id} header for the read/admin path, then clears
 * it. Runs first so the context is present before any controller. A missing header
 * (standalone) → the default tenant (net-zero, D8).
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
