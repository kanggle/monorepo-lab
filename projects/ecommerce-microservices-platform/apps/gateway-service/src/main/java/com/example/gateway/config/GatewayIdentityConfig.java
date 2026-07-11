package com.example.gateway.config;

import com.example.apigateway.filter.IdentityHeaderStripFilter;
import com.example.apigateway.filter.JwtHeaderEnrichmentFilter;
import com.example.apigateway.filter.JwtHeaderMapping;
import com.example.apigateway.security.JwtClaims;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ecommerce's identity-header policy at the edge (ADR-MONO-048 D7 step 3).
 *
 * <p>The filters live in {@code libs/java-gateway}; this class is the whole of what is
 * ecommerce-specific about them. Both beans are read directly by
 * {@code IdentityHeaderStripFilterTest} / {@code JwtHeaderEnrichmentFilterTest}, so those
 * tests exercise the policy this file actually wires. Delete a mapping here and the suite
 * goes red.
 *
 * <p>These are {@code @Bean}s rather than scanned {@code @Component}s because ecommerce
 * deliberately does <strong>not</strong> component-scan {@code com.example.apigateway}: it
 * keeps its own {@code SecurityConfig} (route knowledge — ADR-MONO-048 § D4), and scanning
 * the library package would register the library's chain alongside it.
 */
@Configuration
public class GatewayIdentityConfig {

    /**
     * ecommerce strips the library baseline <strong>plus {@code X-Seller-Scope}</strong>.
     *
     * <h2>Two changes here, and both are additions</h2>
     *
     * <p><strong>1. The baseline adds {@code X-Account-Id} and {@code X-Roles}</strong>, which
     * ecommerce did not strip before. No ecommerce service reads either (verified by census at
     * TASK-MONO-356: the identity headers actually read downstream are {@code X-User-Id},
     * {@code X-User-Role}, {@code X-Tenant-Id} and {@code X-Seller-Scope}), so this is inert
     * defence-in-depth — but the first service to start reading one must not inherit a hole.
     *
     * <p><strong>2. {@code X-Seller-Scope} is new, and it is the one that matters.</strong>
     * Three services ({@code order}, {@code product}, {@code settlement}) each run a
     * {@code SellerScopeContextFilter} that reads this header <em>from the inbound request</em>
     * and binds it as the seller data-scope, which their repositories then apply as
     * {@code AND EXISTS(... seller_id = :sellerScope)}. Its javadoc calls it "the
     * gateway-injected header" and states that "the gateway only forwards this header on the
     * OPERATOR plane" — <strong>neither was true.</strong> This gateway did not strip it and
     * does not inject it, and it routes {@code /api/admin/orders/**},
     * {@code /api/admin/products/**} and {@code /api/admin/settlements/**}, so a client-supplied
     * value reached those filters intact.
     *
     * <p>It is not exploitable <em>today</em>, and only by luck of sequencing: the confinement
     * it feeds is inert (nothing in the repository produces this header — the claim→header
     * plumbing is ADR-MONO-030 Step 4, still unbuilt), and while it is inert an absent header
     * means "unrestricted", which is the documented net-zero / fail-OPEN invariant of
     * ADR-MONO-025. Forging it can only narrow one's own view.
     *
     * <p><strong>The day Step 4 injects it, the hole opens</strong> — a seller confined to their
     * own {@code seller_id} could send their own {@code X-Seller-Scope} and, with nothing
     * stripping it, escape into the full-tenant view. Stripping it now means the activation
     * cannot ship the hole. This is the same defect class as {@code TASK-BE-501}, caught one
     * step before it becomes reachable rather than one step after.
     */
    @Bean
    public IdentityHeaderStripFilter identityHeaderStripFilter() {
        return new IdentityHeaderStripFilter(Set.of("X-Seller-Scope"));
    }

    /**
     * ecommerce injects {@code X-User-Id} ← {@code sub} (ADR-MONO-040 Phase 3B: the SAS access
     * token's {@code sub} is the account UUID), {@code X-User-Email}, {@code X-User-Role}
     * (always — an empty value means "no authorized role" and must deny; an absent header would
     * let a service default open), and {@code X-Tenant-Id} (ADR-MONO-030 § 2.2 M2 layer 2 —
     * the multi-tenant edge admits every tenant, so downstream genuinely cannot infer it).
     *
     * <p>It injects no {@code X-Actor-Id}, {@code X-Account-Id}, {@code X-Roles},
     * {@code X-Scopes} or {@code X-Token-Type} — and, notably, no {@code X-Seller-Scope}: the
     * OPERATOR token's seller-scope claim is not yet plumbed (ADR-MONO-030 Step 4). Until it
     * is, the strip above is that header's <em>entire</em> defence.
     */
    @Bean
    public JwtHeaderEnrichmentFilter jwtHeaderEnrichmentFilter() {
        return new JwtHeaderEnrichmentFilter(List.of(
                JwtHeaderMapping.skipIfNull("X-User-Id", JwtClaims::subject),
                JwtHeaderMapping.skipIfNull("X-User-Email", JwtClaims::email),
                JwtHeaderMapping.always("X-User-Role", JwtClaims::role),
                JwtHeaderMapping.skipIfBlank("X-Tenant-Id", JwtClaims::tenantId)));
    }
}
