package com.example.fanplatform.membership.presentation.filter;

import com.example.fanplatform.membership.domain.tenant.TenantContext;
import com.example.fanplatform.membership.presentation.security.PublicPaths;
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
 * Service-level fail-closed re-enforcement of {@code tenant_id} on the END-USER
 * surface (defense-in-depth, third layer after gateway + JwtDecoder). Runs after
 * Spring Security has populated the SecurityContext.
 *
 * <p>Skips public actuator paths AND the {@code /internal/**} surface: the
 * internal workload-identity chain carries no {@code tenant_id} claim (it is a
 * client_credentials token), so this end-user tenant gate must not apply there.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class TenantClaimEnforcer extends OncePerRequestFilter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INTERNAL_PREFIX = "/internal/";

    private final String expectedTenantId;

    public TenantClaimEnforcer(
            @org.springframework.beans.factory.annotation.Value(
                    "${fanplatform.oauth2.required-tenant-id:fan-platform}") String expectedTenantId) {
        this.expectedTenantId = expectedTenantId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // /internal/** is workload-identity (no tenant_id claim) — not an end-user route.
        return PublicPaths.isPublic(request)
                || (uri != null && uri.startsWith(INTERNAL_PREFIX));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String tenantId = jwtAuth.getToken().getClaimAsString(TenantContext.CLAIM_TENANT_ID);
            if (tenantId == null || tenantId.isBlank()) {
                writeError(response, HttpStatus.UNAUTHORIZED.value(),
                        "UNAUTHORIZED", "tenant_id claim is required");
                return;
            }
            if (!TenantContext.WILDCARD_TENANT.equals(tenantId)
                    && !expectedTenantId.equals(tenantId)) {
                log.warn("TenantClaimEnforcer rejected cross-tenant request: tenant={} path={}",
                        tenantId, request.getRequestURI());
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
