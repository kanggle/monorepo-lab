package com.example.finance.account.presentation.filter;

import com.example.finance.account.infrastructure.security.TenantClaimValidator;
import com.example.finance.account.presentation.security.PublicPaths;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Service-level fail-closed re-enforcement of {@code tenant_id} (defense-in-
 * depth — gateway + {@link TenantClaimValidator} during decode + this filter
 * each independently enforce the invariant). Cross-tenant → 403
 * {@code TENANT_FORBIDDEN}.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class TenantClaimEnforcer extends OncePerRequestFilter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WILDCARD_TENANT = "*";

    private final String expectedTenantId;

    public TenantClaimEnforcer(
            @org.springframework.beans.factory.annotation.Value(
                    "${financeplatform.oauth2.required-tenant-id:finance}")
            String expectedTenantId) {
        this.expectedTenantId = expectedTenantId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PublicPaths.isPublic(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String tenantId = jwtAuth.getToken()
                    .getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID);
            if (tenantId == null || tenantId.isBlank()) {
                writeError(response, HttpStatus.UNAUTHORIZED.value(),
                        "UNAUTHORIZED", "tenant_id claim is required");
                return;
            }
            if (!WILDCARD_TENANT.equals(tenantId)
                    && !expectedTenantId.equals(tenantId)) {
                log.warn("TenantClaimEnforcer rejected cross-tenant request: "
                        + "tenant={} path={}", tenantId, request.getRequestURI());
                writeError(response, HttpStatus.FORBIDDEN.value(),
                        "TENANT_FORBIDDEN",
                        "tenant_id '" + tenantId + "' is not allowed");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static void writeError(HttpServletResponse response, int status,
                                   String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ObjectNode node = JSON.createObjectNode();
        node.put("code", code);
        node.put("message", message);
        node.put("timestamp", Instant.now().toString());
        try {
            response.getWriter().write(JSON.writeValueAsString(node));
        } catch (JsonProcessingException ex) {
            response.getWriter().write(
                    "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
        }
    }
}
