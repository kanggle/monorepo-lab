package com.kanggle.platformconsole.bff.adapter.outbound.http;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped bean that reads the inbound operator credentials forwarded by
 * {@code console-web}'s server routes.
 *
 * <p>Architecture.md § Auth Flow inbound: the browser never holds either token;
 * {@code console-web}'s App-Router server routes forward them on every BFF call:
 * <ul>
 *   <li>{@code X-Operator-Token} — RFC 8693 exchanged operator token (for GAP leg)</li>
 *   <li>{@code X-Tenant-Id} — active tenant from {@code getActiveTenant()}</li>
 * </ul>
 *
 * <p>The GAP OIDC access token is the inbound bearer ({@code Authorization} header)
 * parsed by Spring Security OAuth2 Resource Server — this bean does NOT read it again.
 *
 * <p>The inbound {@code Authorization: Bearer} token is the GAP OIDC access token
 * used as the BFF's inbound principal. The {@code X-Operator-Token} is the operator
 * credential forwarded separately for outbound GAP leg dispatch.
 *
 * <p>Both absent-token conditions fail closed (see {@link MissingTenantException}
 * and {@link com.kanggle.platformconsole.bff.domain.credential.MissingCredentialException}).
 */
@Component
@RequestScope
public class OperatorCredentialContext {

    static final String HEADER_OPERATOR_TOKEN = "X-Operator-Token";
    static final String HEADER_TENANT_ID = "X-Tenant-Id";

    private final String operatorToken;
    private final String tenantId;
    private final String gapOidcAccessToken;

    public OperatorCredentialContext(HttpServletRequest request) {
        this.operatorToken = request.getHeader(HEADER_OPERATOR_TOKEN);
        this.tenantId = request.getHeader(HEADER_TENANT_ID);
        // Extract bearer from the Authorization header (already validated by Spring Security)
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            this.gapOidcAccessToken = auth.substring(7);
        } else {
            this.gapOidcAccessToken = null;
        }
    }

    /**
     * Returns the RFC 8693 exchanged operator token from {@code X-Operator-Token}.
     * May be null or blank if absent; callers MUST handle fail-closed.
     */
    public String getOperatorToken() {
        return operatorToken;
    }

    /**
     * Returns the GAP OIDC access token from the {@code Authorization} bearer.
     * Already validated by Spring Security; may be null in test contexts where
     * security filters are bypassed.
     */
    public String getIamOidcAccessToken() {
        return gapOidcAccessToken;
    }

    /**
     * Returns the active tenant from {@code X-Tenant-Id}.
     * May be null or blank; composition use-cases MUST reject absent tenant
     * with {@code 400 NO_ACTIVE_TENANT} before any outbound call (D6.A).
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Returns true if the tenant is present and non-blank.
     */
    public boolean hasTenant() {
        return tenantId != null && !tenantId.isBlank();
    }
}
