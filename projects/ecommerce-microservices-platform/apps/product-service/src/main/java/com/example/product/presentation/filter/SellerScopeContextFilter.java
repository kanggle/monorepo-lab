package com.example.product.presentation.filter;

import com.example.product.domain.seller.SellerScopeContext;
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
 * axis, ABAC {@code org_scope} shape). Reads the gateway-injected
 * {@code X-Seller-Scope} header — the OPERATOR token's seller-scope claim — into
 * {@link SellerScopeContext} for the duration of the request, then clears it.
 *
 * <p>Runs at {@code HIGHEST_PRECEDENCE - 1}, i.e. immediately <em>after</em>
 * {@code TenantContextFilter}: the seller axis is always nested inside the tenant
 * axis (isolate-then-attribute, AC-6). The gateway only forwards this header on the
 * OPERATOR plane; CONSUMER requests carry no seller authority (F5), so the shared
 * catalog is never seller-narrowed. A missing/blank/{@code '*'} header leaves the
 * scope unset → unrestricted full-tenant view (net-zero / fail-OPEN, F1).
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
