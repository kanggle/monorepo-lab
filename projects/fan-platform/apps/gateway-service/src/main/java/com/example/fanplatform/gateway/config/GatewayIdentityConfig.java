package com.example.fanplatform.gateway.config;

import com.example.apigateway.error.GatewayErrorHandler;
import com.example.apigateway.filter.IdentityHeaderStripFilter;
import com.example.apigateway.filter.JwtHeaderEnrichmentFilter;
import com.example.apigateway.filter.JwtHeaderMapping;
import com.example.apigateway.filter.RoleAdmissionFilter;
import com.example.apigateway.filter.RoleAdmissions;
import com.example.apigateway.security.JwtClaims;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * fan's identity-header policy at the edge (ADR-MONO-048 D7 step 2).
 *
 * <p>The filters live in {@code libs/java-gateway}; this class is the whole of what is
 * fan-specific about them. Both beans are read directly by
 * {@code IdentityHeaderStripFilterTest} / {@code JwtHeaderEnrichmentFilterTest}, so those
 * tests exercise the policy this file actually wires. Delete a mapping here and the suite
 * goes red.
 */
@Configuration
public class GatewayIdentityConfig {

    /** fan strips exactly {@link IdentityHeaderStripFilter#BASELINE_HEADERS} — no additions. */
    @Bean
    public IdentityHeaderStripFilter identityHeaderStripFilter() {
        return new IdentityHeaderStripFilter();
    }

    /**
     * fan injects the baseline identity headers plus {@code X-Tenant-Id} — its gate admits
     * the SUPER_ADMIN wildcard, so a downstream service cannot infer the tenant from the
     * fact that the request arrived.
     *
     * <p>It injects neither {@code X-Scopes} nor {@code X-Token-Type} (scm-only), and no
     * gateway injects {@code X-Account-Type} (ADR-032 D3) — though every gateway strips it.
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
                JwtHeaderMapping.always("X-Roles", JwtClaims::role)));
    }

    /**
     * fan's role-based admission (JWT rule 6 — TASK-MONO-416). The shared {@code SecurityConfig}
     * authenticates ({@code .authenticated()}); this leg authorizes. {@link RoleAdmissions#roleOrScope()}
     * admits any token carrying a role (FAN / ARTIST / operator / the {@code SUPER_ADMIN} wildcard)
     * or a machine scope, and 403s a token that carries neither. Public routes (no security context)
     * pass. Runs at {@link RoleAdmissionFilter#ADMISSION_ORDER} — before header enrichment, so a
     * rejected request is never enriched with identity headers.
     */
    @Bean
    public RoleAdmissionFilter roleAdmissionFilter(GatewayErrorHandler errorHandler) {
        return new RoleAdmissionFilter(
                RoleAdmissions.roleOrScope(),
                "fan-platform access requires an authorized role",
                errorHandler);
    }
}
