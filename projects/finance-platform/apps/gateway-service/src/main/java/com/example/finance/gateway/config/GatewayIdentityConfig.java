package com.example.finance.gateway.config;

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
 * finance's identity-header policy at the edge (TASK-MONO-357).
 *
 * <p>The filters live in {@code libs/java-gateway}; this class is the whole of what is
 * finance-specific about them. Both beans are read directly by
 * {@code IdentityHeaderStripFilterTest} / {@code JwtHeaderEnrichmentFilterTest}, so those tests
 * exercise the policy this file actually wires. Delete a mapping here and the suite goes red.
 */
@Configuration
public class GatewayIdentityConfig {

    /**
     * finance strips exactly {@link IdentityHeaderStripFilter#BASELINE_HEADERS} — the eight
     * headers every gateway strips. It adds none of its own.
     *
     * <p><strong>Nothing downstream reads any of them</strong> — a census across
     * {@code account-service} and {@code ledger-service} (TASK-MONO-357) found zero consumers of
     * any {@code X-*} identity header; both derive the actor from the verified JWT. That is
     * precisely why the strip has to exist <em>now</em> rather than when someone starts reading
     * one: the first reader must not inherit a forged value.
     *
     * <p>{@code TASK-MONO-356} is the cautionary case. ecommerce's {@code X-Seller-Scope} was a
     * header three services genuinely <em>did</em> trust from the inbound request, and it went
     * unstripped for as long as nobody looked — harmless only by luck of sequencing.
     */
    @Bean
    public IdentityHeaderStripFilter identityHeaderStripFilter() {
        return new IdentityHeaderStripFilter();
    }

    /**
     * finance injects the verified identity headers even though no service reads them today.
     * They are the platform's identity contract (`platform/api-gateway-policy.md` § Identity
     * Header Handling), and a service that starts reading one tomorrow should find a value the
     * gateway vouches for rather than nothing at all.
     *
     * <p>{@code X-User-Role} is written <strong>always</strong>, empty string included: a
     * downstream service that sees {@code ""} must read it as "no authorized role" and deny,
     * whereas one that sees no header may fall through to a default — silently, and open.
     */
    @Bean
    public JwtHeaderEnrichmentFilter jwtHeaderEnrichmentFilter() {
        return new JwtHeaderEnrichmentFilter(List.of(
                JwtHeaderMapping.skipIfNull("X-User-Id", JwtClaims::subject),
                JwtHeaderMapping.skipIfNull("X-Actor-Id", JwtClaims::subject),
                JwtHeaderMapping.skipIfNull("X-User-Email", JwtClaims::email),
                JwtHeaderMapping.always("X-User-Role", JwtClaims::role),
                JwtHeaderMapping.skipIfBlank("X-Tenant-Id", JwtClaims::tenantId)));
    }

    /**
     * finance's role-based admission (JWT rule 6 — TASK-MONO-416). finance is an
     * entitlement-plane operator platform ({@code ProductCatalog.ENTRIES}, symmetric with erp),
     * and TASK-MONO-416 added its row to the contract's rule-6 table. The shared
     * {@code SecurityConfig} authenticates; this leg authorizes. {@link RoleAdmissions#roleOrScope()}
     * admits a token carrying an operator role ({@code OPERATOR}/{@code ADMIN}/the
     * {@code SUPER_ADMIN} wildcard) or a machine scope, and 403s a token that carries neither.
     * Public routes pass. Runs at {@link RoleAdmissionFilter#ADMISSION_ORDER} — before enrichment.
     */
    @Bean
    public RoleAdmissionFilter roleAdmissionFilter(GatewayErrorHandler errorHandler) {
        return new RoleAdmissionFilter(
                RoleAdmissions.roleOrScope(),
                "finance access requires an authorized role",
                errorHandler);
    }
}
