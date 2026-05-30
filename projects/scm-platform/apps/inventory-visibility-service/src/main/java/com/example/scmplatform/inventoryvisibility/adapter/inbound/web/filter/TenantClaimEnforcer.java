package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Service-level fail-closed re-enforcement of {@code tenant_id}=scm.
 * Defense-in-depth: gateway, JWT decoder validator, and this filter each
 * independently enforce the same tenant invariant.
 * <p>
 * Mirrors procurement-service TenantClaimEnforcer pattern.
 *
 * <p>Applies the <strong>entitlement-trust dual-accept</strong> gate
 * (ADR-MONO-019 § D5): pass when legacy {@code tenant_id ∈ {expectedTenantId,
 * "*"}} <em>or</em> the signed {@code entitled_domains} claim contains
 * {@code expectedTenantId}; otherwise 403 {@code TENANT_FORBIDDEN}. Rejection
 * requires <strong>both</strong> branches to fail (fail-closed). This service
 * has no decode-time validator, so {@link #isEntitled} lives locally here (each
 * service owns its copy — the helper cannot be shared across modules).
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class TenantClaimEnforcer extends OncePerRequestFilter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WILDCARD_TENANT = "*";
    private static final String CLAIM_TENANT_ID = "tenant_id";
    private static final String CLAIM_ENTITLED_DOMAINS = "entitled_domains";

    private final String expectedTenantId;

    public TenantClaimEnforcer(
            @Value("${scmplatform.oauth2.required-tenant-id:scm}") String expectedTenantId) {
        this.expectedTenantId = expectedTenantId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String tenantId = jwtAuth.getToken().getClaimAsString(CLAIM_TENANT_ID);
            boolean entitled = isEntitled(jwtAuth.getToken(), expectedTenantId);
            if ((tenantId == null || tenantId.isBlank()) && !entitled) {
                writeError(response, HttpStatus.UNAUTHORIZED.value(),
                        "UNAUTHORIZED", "tenant_id claim is required");
                return;
            }
            boolean legacyOk = WILDCARD_TENANT.equals(tenantId)
                    || expectedTenantId.equals(tenantId);
            // Dual-accept: reject only when BOTH legacy slug and the signed
            // entitled_domains claim fail (fail-closed; entitlement only widens).
            if (!legacyOk && !entitled) {
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

    /**
     * Local entitlement-trust check (this service has no decode-time validator
     * to share with). Returns {@code true} iff the verified
     * {@code entitled_domains} claim is a list of strings that contains
     * {@code domain}. Any claim shape anomaly (absent / non-list / null or
     * non-string element) yields {@code false} (fail-closed — no NPE, no
     * blanket trust).
     */
    static boolean isEntitled(Jwt jwt, String domain) {
        if (jwt == null || domain == null) {
            return false;
        }
        Object raw = jwt.getClaims().get(CLAIM_ENTITLED_DOMAINS);
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        for (Object element : list) {
            if (element instanceof String s && s.equals(domain)) {
                return true;
            }
        }
        return false;
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
            response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
        }
    }
}
