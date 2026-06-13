package com.example.settlement.presentation.filter;

import com.example.settlement.domain.seller.SellerScopeContext;
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
 * Binds the request's seller scope (ADR-MONO-030 Step 3 §3.3 — inner marketplace
 * axis, ABAC {@code org_scope}) from the gateway-injected {@code X-Seller-Scope}
 * header, then clears it. Runs immediately <em>after</em> {@link TenantContextFilter}
 * (the seller axis is always nested inside the tenant axis, AC-8). A missing /
 * blank / {@code '*'} header → unrestricted full-tenant view (net-zero / fail-OPEN).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SellerScopeContextFilter extends OncePerRequestFilter {

    static final String SELLER_SCOPE_HEADER = "X-Seller-Scope";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            SellerScopeContext.set(request.getHeader(SELLER_SCOPE_HEADER));
            chain.doFilter(request, response);
        } finally {
            SellerScopeContext.clear();
        }
    }
}
