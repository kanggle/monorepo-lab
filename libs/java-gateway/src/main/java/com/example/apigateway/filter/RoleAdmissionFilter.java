package com.example.apigateway.filter;

import com.example.apigateway.error.GatewayErrorHandler;
import com.example.apigateway.security.ReactiveJwtAccess;
import java.util.Objects;
import java.util.function.Predicate;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Role-based admission at the edge — JWT validation rule 6
 * ({@code platform/contracts/jwt-standard-claims.md}): admit iff the authenticated token
 * carries an authorization credential valid for the requested surface, otherwise
 * {@code 403 Forbidden}. Authentication (valid signature, issuer, {@code aud}, tenant) is
 * the shared {@code SecurityConfig}'s {@code .authenticated()}; <em>authorization</em>
 * (this filter) is the leg each gateway must add for its own surface. wms
 * ({@code AccountTypeValidationFilter}) and ecommerce ({@code AccountTypeEnforcementFilter})
 * carry equivalent per-service copies; this is the parameterized library form the three
 * un-wired gateways (fan / erp / scm) and finance adopt (TASK-MONO-416).
 *
 * <p><strong>The admission decision is the parameter.</strong> The library holds only the
 * mechanism — read the current JWT, apply a predicate, 403 on failure, pass a public route
 * through untouched — exactly the division {@link JwtHeaderMapping} draws for header
 * enrichment (ADR-MONO-048 § D3): what counts as admitted is a per-domain
 * {@code Predicate<Jwt>} supplied at the wiring site, so no {@code libs/} class grows a
 * branch specific to one platform's role vocabulary. {@link RoleAdmissions#roleOrScope()}
 * is the predicate every current consumer uses.
 *
 * <p><strong>Opt-in, not {@code @Component}.</strong> Like {@link JwtHeaderEnrichmentFilter}
 * — and unlike an auto-configured filter — this class is registered by an explicit
 * {@code @Bean} at each gateway (its predicate and 403 message have no platform-agnostic
 * default). A gateway that never declares the bean is therefore not silently protected; the
 * per-gateway admission test is what turns that omission red (ADR-MONO-049 § D5-7/8: a
 * missing edge check must fail a test, not pass quietly).
 *
 * <p><strong>Public routes pass.</strong> A request with no JWT security context (actuator
 * health, any {@code permitAll()} route) resolves to an empty {@link Mono}, and
 * {@code defaultIfEmpty(TRUE)} admits it — the same {@code Boolean}-intermediate shape wms
 * uses to avoid the {@code switchIfEmpty(chain.filter())} trap, where a {@code Mono<Void>}
 * success completion is indistinguishable from "no auth context present".
 *
 * <p><strong>Order {@value #ADMISSION_ORDER}</strong> runs after Spring Security's
 * authentication filter (the token must already be in the context) and after
 * {@link IdentityHeaderStripFilter}, but <em>before</em> {@link JwtHeaderEnrichmentFilter}
 * (order {@code -1}): a request about to be 403'd must not first have verified identity
 * headers injected. This matches wms and ecommerce (both {@code -2}).
 */
public class RoleAdmissionFilter implements GlobalFilter, Ordered {

    /**
     * After authentication + strip, before header enrichment ({@code -1}). Kept identical to
     * wms/ecommerce so the "reject before enrich" invariant is uniform across the fleet.
     */
    public static final int ADMISSION_ORDER = -2;

    private final Predicate<Jwt> admits;
    private final String denyMessage;
    private final GatewayErrorHandler errorHandler;

    /**
     * @param admits       the per-surface admission decision (e.g.
     *                     {@link RoleAdmissions#roleOrScope()}); a token is admitted iff this
     *                     returns {@code true}
     * @param denyMessage  the human-readable {@code 403} body message for a rejected token
     * @param errorHandler shared envelope writer (the library's {@code GatewayErrorHandler} bean)
     */
    public RoleAdmissionFilter(Predicate<Jwt> admits, String denyMessage,
                               GatewayErrorHandler errorHandler) {
        this.admits = Objects.requireNonNull(admits, "admits");
        this.denyMessage = Objects.requireNonNull(denyMessage, "denyMessage");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveJwtAccess.currentJwt()
                .map(admits::test)
                .defaultIfEmpty(Boolean.TRUE) // no JWT security context → public route → proceed
                .flatMap(admitted -> Boolean.TRUE.equals(admitted)
                        ? chain.filter(exchange)
                        : errorHandler.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", denyMessage));
    }

    @Override
    public int getOrder() {
        return ADMISSION_ORDER;
    }
}
