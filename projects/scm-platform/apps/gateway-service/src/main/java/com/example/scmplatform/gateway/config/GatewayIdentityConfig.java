package com.example.scmplatform.gateway.config;

import com.example.apigateway.error.GatewayErrorHandler;
import com.example.apigateway.filter.IdentityHeaderStripFilter;
import com.example.apigateway.filter.JwtHeaderEnrichmentFilter;
import com.example.apigateway.filter.JwtHeaderMapping;
import com.example.apigateway.filter.RoleAdmissionFilter;
import com.example.apigateway.filter.RoleAdmissions;
import com.example.apigateway.security.JwtClaims;
import com.example.scmplatform.gateway.security.ScmTokenType;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * scm's identity-header policy at the edge (ADR-MONO-048 D7 step 2).
 *
 * <p>The filters live in {@code libs/java-gateway}; this class is the whole of what is
 * scm-specific about them. Both beans are read directly by
 * {@code IdentityHeaderStripFilterTest} / {@code JwtHeaderEnrichmentFilterTest}, so those
 * tests exercise the policy this file actually wires. Delete a mapping here and the suite
 * goes red.
 */
@Configuration
public class GatewayIdentityConfig {

    /**
     * scm strips the baseline <strong>plus</strong> {@code X-Token-Type} and
     * {@code X-Scopes} — the two headers it is alone in injecting, and which therefore only
     * it needs to refuse from a client.
     *
     * <p>The library's API takes <em>additions</em>: there is no way to spell "strip fewer
     * than the baseline" (ADR-MONO-048 § D3). Narrowing the set is the defect
     * {@code TASK-BE-501}/{@code 502} closed, and a config that could reopen it would look
     * like a setting rather than like a hole.
     */
    @Bean
    public IdentityHeaderStripFilter identityHeaderStripFilter() {
        return new IdentityHeaderStripFilter(Set.of("X-Token-Type", "X-Scopes"));
    }

    /**
     * scm injects the widest header set of the three: the baseline identity headers, plus
     * {@code X-Tenant-Id} (its gate admits the SUPER_ADMIN wildcard, so downstream services
     * genuinely cannot assume the tenant), plus {@code X-Scopes} and {@code X-Token-Type}
     * (Edge Case E1 — downstream services branch on machine-vs-human callers).
     *
     * <p>{@code X-Account-Type} is injected by nobody (ADR-032 D3) but stripped by everybody.
     */
    @Bean
    public JwtHeaderEnrichmentFilter jwtHeaderEnrichmentFilter() {
        return new JwtHeaderEnrichmentFilter(List.of(
                JwtHeaderMapping.skipIfNull("X-User-Id", JwtClaims::subject),
                JwtHeaderMapping.skipIfNull("X-Account-Id", JwtClaims::subject),
                JwtHeaderMapping.skipIfNull("X-Actor-Id", JwtClaims::subject),
                JwtHeaderMapping.skipIfNull("X-User-Email", JwtClaims::email),
                JwtHeaderMapping.skipIfBlank("X-Tenant-Id", JwtClaims::tenantId),
                JwtHeaderMapping.always("X-User-Role", JwtClaims::role),
                JwtHeaderMapping.always("X-Roles", JwtClaims::role),
                JwtHeaderMapping.skipIfBlank("X-Scopes", JwtClaims::scope),
                JwtHeaderMapping.always("X-Token-Type", ScmTokenType::of)));
    }

    /**
     * scm's role-based admission (JWT rule 6 — TASK-MONO-416). The shared {@code SecurityConfig}
     * authenticates; this leg authorizes. {@link RoleAdmissions#roleOrScope()} admits a token
     * carrying a role (scm's {@code OPERATOR}/{@code ADMIN}/{@code BUYER}, the {@code SUPER_ADMIN}
     * wildcard, or iam's assume-tenant {@code SCM_OPERATOR}) <strong>or</strong> a machine
     * {@code scope} — scm v1 is backend-only, so {@code client_credentials} tokens
     * ({@code scope}, no roles) are the primary caller shape (iam-integration.md Edge Case E3);
     * the "scope or role" gate is that spec's documented admission. A token with neither is 403'd.
     * Public routes pass. Runs at {@link RoleAdmissionFilter#ADMISSION_ORDER} — before enrichment.
     */
    @Bean
    public RoleAdmissionFilter roleAdmissionFilter(GatewayErrorHandler errorHandler) {
        return new RoleAdmissionFilter(
                RoleAdmissions.roleOrScope(),
                "scm access requires an authorized role",
                errorHandler);
    }
}
