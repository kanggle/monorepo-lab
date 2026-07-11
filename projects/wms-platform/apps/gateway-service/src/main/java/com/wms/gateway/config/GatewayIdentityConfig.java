package com.wms.gateway.config;

import com.example.apigateway.filter.IdentityHeaderStripFilter;
import com.example.apigateway.filter.JwtHeaderEnrichmentFilter;
import com.example.apigateway.filter.JwtHeaderMapping;
import com.example.apigateway.security.JwtClaims;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * wms's identity-header policy at the edge: what is stripped, and what is re-asserted from
 * the verified JWT (ADR-MONO-048 D7 step 2).
 *
 * <p>The filters themselves live in {@code libs/java-gateway}; this class is the whole of
 * what is wms-specific about them. Both beans are read directly by
 * {@code IdentityHeaderStripFilterTest} / {@code JwtHeaderEnrichmentFilterTest}, so those
 * tests exercise the policy this file actually wires — not a copy of it constructed in the
 * test. Delete a mapping here and the suite goes red.
 */
@Configuration
public class GatewayIdentityConfig {

    /**
     * wms strips exactly {@link IdentityHeaderStripFilter#BASELINE_HEADERS} — the eight
     * headers all three gateways stripped after {@code TASK-BE-501}/{@code 502}. It adds
     * none of its own.
     */
    @Bean
    public IdentityHeaderStripFilter identityHeaderStripFilter() {
        return new IdentityHeaderStripFilter();
    }

    /**
     * wms injects the smallest header set of the three (ADR-MONO-035 4b-2a):
     * {@code X-User-Id} / {@code X-Actor-Id} ← {@code sub}, {@code X-User-Email} ←
     * {@code email}, {@code X-User-Role} ← {@code roles}/{@code role}.
     *
     * <p>Deliberately absent, and each for its own reason:
     * <ul>
     *   <li><strong>{@code X-Account-Type}</strong> — no downstream service reads it
     *       (ADR-032 D3). It is still <em>stripped</em>: inert defence-in-depth costs
     *       nothing, whereas the first service to start reading it must not inherit a
     *       hole.</li>
     *   <li><strong>{@code X-Tenant-Id}</strong> — wms's tenant gate is strict equality, so
     *       every request that reaches a downstream service is already known to be wms's.
     *       There is no tenant to disambiguate. It is stripped for the same reason.</li>
     * </ul>
     */
    @Bean
    public JwtHeaderEnrichmentFilter jwtHeaderEnrichmentFilter() {
        return new JwtHeaderEnrichmentFilter(List.of(
                JwtHeaderMapping.skipIfNull("X-User-Id", JwtClaims::subject),
                JwtHeaderMapping.skipIfNull("X-Actor-Id", JwtClaims::subject),
                JwtHeaderMapping.skipIfNull("X-User-Email", JwtClaims::email),
                JwtHeaderMapping.always("X-User-Role", JwtClaims::role)));
    }
}
