package com.example.security.servlet;

import com.example.security.oauth2.AllowedIssuersValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * The two halves of a stateless JWT resource-server's <strong>chain assembly</strong>, promoted from
 * the copies every servlet service in the fleet was maintaining by hand (ADR-MONO-058 § D4):
 *
 * <ol>
 *   <li>{@link #jwtDecoder(String)} — the {@link NimbusJwtDecoder} + {@link DelegatingOAuth2TokenValidator}
 *       construction that each service wrote as a {@code jwtDecoder()} / {@code jwtTokenValidator()}
 *       {@code @Bean} pair.</li>
 *   <li>{@link #statelessJwtChain(HttpSecurity)} — the generic, non-domain tail of a servlet
 *       {@code SecurityConfig}: CSRF-disabled, {@code STATELESS} sessions, public-vs-authenticated path
 *       routing, and the {@code oauth2ResourceServer(...)} wiring.</li>
 * </ol>
 *
 * <h2>THIS IS NOT AN AUTO-CONFIGURATION</h2>
 *
 * <strong>Nothing in this class installs itself.</strong> There is no {@code @AutoConfiguration}, no
 * {@code @Configuration}, no {@code @Component}, no {@code @Bean}, and this module ships no
 * {@code META-INF/spring/…AutoConfiguration.imports} entry — a service that merely puts this module on
 * its classpath gets <em>zero</em> behaviour change. The service calls these builders from its own
 * {@code @Configuration} class and declares the resulting {@code JwtDecoder} / {@code SecurityFilterChain}
 * as its own beans.
 *
 * <p>That is a deliberate constraint, not an oversight: {@code platform/shared-library-policy.md}
 * § <em>No context-wide annotations in a shared {@code @AutoConfiguration}</em> forbids a shared library
 * from reconfiguring every consumer's {@code ApplicationContext}, and this class assembles the
 * <strong>authentication path</strong> — the one surface where a library that silently changed a
 * consumer's posture on a version bump would change who can call that service, without a diff in that
 * service. The wiring site stays in the service, in the open, where its own reviewers see it.
 *
 * <h2>Mechanism, not policy</h2>
 *
 * This class holds no path string, no property key, no tenant id, no issuer, no role name, and no import
 * from any {@code projects/} module. Everything a service decides it supplies:
 *
 * <table>
 *   <caption>Where each decision lives</caption>
 *   <tr><th>the service owns</th><th>this class owns</th></tr>
 *   <tr>
 *     <td>its issuer allow-list, its tenant-claim policy (built with
 *         {@code TenantClaimValidator.forTenant(…)} at the wiring site), its {@link PublicPathSet} data,
 *         its authenticated path patterns, its role gates, its error-response writers, and every
 *         {@code application.yml} property key those are bound from</td>
 *     <td>the <em>order</em> the validators run in, the duplicate-free {@code NimbusJwtDecoder}
 *         construction, and the CSRF/session/authorize/resource-server call sequence that fifteen-odd
 *         copies had each re-typed</td>
 *   </tr>
 * </table>
 *
 * <h2>Measured shape (why the defaults are what they are)</h2>
 *
 * The servlet resource-server chains audited for § D4 agreed on more than they disagreed on, and both
 * axes below were counted against the tree rather than inferred from the audit's prose:
 *
 * <ul>
 *   <li><strong>CSRF disabled + {@code STATELESS} sessions — unanimous.</strong> Every servlet
 *       resource-server chain examined disables CSRF and pins {@code SessionCreationPolicy.STATELESS}.
 *       They are therefore applied unconditionally here, and named in the factory method
 *       ({@code statelessJwtChain}) rather than hidden in a default, so an adopter cannot acquire the
 *       posture without reading it. The one chain in the fleet that keeps CSRF and an
 *       {@code IF_REQUIRED} session is a browser <em>login form</em> — not a resource server, and not
 *       what this class is for.</li>
 *   <li><strong>The {@code anyRequest()} tail — genuinely split.</strong> Fourteen of the nineteen
 *       chains end in {@code denyAll()}; the other five end in {@code authenticated()}. A split axis
 *       gets an explicit switch, and the switch defaults to the closed answer
 *       ({@link FilterChainBuilder#anyRequestDenied()}) — not to the majority answer, which is the
 *       same reasoning {@link TenantClaimEnforcer} records for its own switches. An adopter whose
 *       chain really does end in {@code authenticated()} says so, in one call, in its own file.</li>
 * </ul>
 *
 * @see PublicPathSet
 * @see TenantClaimEnforcer
 */
public final class ResourceServerChainAssembler {

    private ResourceServerChainAssembler() {
        // Assembly entry points are the two static factories; there is nothing to instantiate.
    }

    /**
     * Begins assembling a JWKS-backed {@link NimbusJwtDecoder} and its validator chain.
     *
     * <p>The caller must still supply an issuer allow-list ({@link JwtDecoderBuilder#allowedIssuers} or
     * {@link JwtDecoderBuilder#allowedIssuersCsv}); building without one fails loudly rather than
     * producing a decoder that accepts any issuer signed by the JWKS.
     *
     * @param jwkSetUri the JWKS endpoint; fetched lazily on first verification, so construction does
     *                  not couple service startup to the issuer's availability
     * @throws IllegalArgumentException if {@code jwkSetUri} is blank
     * @throws NullPointerException     if {@code jwkSetUri} is null
     */
    public static JwtDecoderBuilder jwtDecoder(String jwkSetUri) {
        return new JwtDecoderBuilder(jwkSetUri);
    }

    /**
     * Begins assembling a stateless, CSRF-disabled JWT resource-server {@link SecurityFilterChain} on
     * the supplied {@link HttpSecurity}.
     *
     * <p>Both parts of the name are load-bearing and both are unconditional: the built chain pins
     * {@link SessionCreationPolicy#STATELESS} and disables CSRF. If either is wrong for the surface
     * being secured, that surface is not what this builder assembles — configure it directly.
     */
    public static FilterChainBuilder statelessJwtChain(HttpSecurity http) {
        return new FilterChainBuilder(http);
    }

    // =====================================================================================
    // 1. The decoder + validator chain
    // =====================================================================================

    /**
     * Assembles the {@code jwtDecoder()} / {@code jwtTokenValidator()} pair.
     *
     * <pre>{@code
     * @Bean
     * OAuth2TokenValidator<Jwt> jwtTokenValidator() {
     *     return ResourceServerChainAssembler.jwtDecoder(jwkSetUri)
     *             .allowedIssuersCsv(allowedIssuersCsv)
     *             .validator(TenantClaimValidator.forTenant(requiredTenantId)
     *                     .allowSuperAdminWildcard()
     *                     .build())
     *             .buildValidator();
     * }
     *
     * @Bean
     * JwtDecoder jwtDecoder() {
     *     return ResourceServerChainAssembler.jwtDecoder(jwkSetUri)
     *             .allowedIssuersCsv(allowedIssuersCsv)
     *             .validator(…)
     *             .build();
     * }
     * }</pre>
     *
     * <p>A decoder whose validator chain is expensive to rebuild can equally be assembled once and the
     * validator exposed from it; the two entry points exist because the copies this replaces exposed
     * both as separate beans, and callers that read the validator bean must keep being able to.
     */
    public static final class JwtDecoderBuilder {

        private final String jwkSetUri;
        private final List<OAuth2TokenValidator<Jwt>> additional = new ArrayList<>();
        private List<String> allowedIssuers;

        private JwtDecoderBuilder(String jwkSetUri) {
            Objects.requireNonNull(jwkSetUri, "jwkSetUri");
            if (jwkSetUri.isBlank()) {
                throw new IllegalArgumentException("jwkSetUri must not be blank");
            }
            this.jwkSetUri = jwkSetUri;
        }

        /**
         * The issuers whose {@code iss} claim this decoder accepts. Required.
         *
         * @throws IllegalArgumentException if the collection is empty — an empty allow-list would
         *                                  reject every token, which is a misconfiguration worth
         *                                  surfacing at wiring time rather than at first request
         */
        public JwtDecoderBuilder allowedIssuers(Collection<String> allowedIssuers) {
            Objects.requireNonNull(allowedIssuers, "allowedIssuers");
            if (allowedIssuers.isEmpty()) {
                throw new IllegalArgumentException("allowedIssuers must not be empty");
            }
            this.allowedIssuers = List.copyOf(allowedIssuers);
            return this;
        }

        /**
         * The comma-separated form, as bound from a single configuration property.
         *
         * <p>Every copy of this assembly carried its own private {@code parseCsv} helper with exactly
         * these semantics — split on {@code ,}, trim each part, drop the blanks. It is the same three
         * lines fifteen times over, so it moves here with the rest of the mechanism. The property
         * <em>key</em> does not move: the service still binds it and passes the string.
         *
         * @throws IllegalArgumentException if the string yields no non-blank entry
         */
        public JwtDecoderBuilder allowedIssuersCsv(String csv) {
            return allowedIssuers(parseCsv(csv));
        }

        /**
         * Appends a validator to the chain, in call order, between the issuer check and the
         * Spring-Security defaults. This is the seam the service's own tenant-claim policy comes
         * through — the policy object is built at the wiring site and handed over already configured,
         * so no tenant id, wildcard decision or entitlement decision is ever expressed in this library.
         */
        public JwtDecoderBuilder validator(OAuth2TokenValidator<Jwt> validator) {
            additional.add(Objects.requireNonNull(validator, "validator"));
            return this;
        }

        /**
         * Builds the validator chain alone, in the order every copy used:
         *
         * <ol>
         *   <li>{@link JwtTimestampValidator}</li>
         *   <li>{@link AllowedIssuersValidator}</li>
         *   <li>each {@link #validator(OAuth2TokenValidator)} in call order</li>
         *   <li>{@link JwtValidators#createDefault()}</li>
         * </ol>
         *
         * <p><strong>The order is behaviour, not style.</strong> {@link DelegatingOAuth2TokenValidator}
         * runs every delegate and <em>accumulates</em> their errors rather than short-circuiting, so
         * the order decides which {@link org.springframework.security.oauth2.core.OAuth2Error} a
         * caller's entry point sees first when a token fails more than one check — and the entry points
         * in the copies this replaces pick the first non-{@code invalid_token} error to decide between
         * a 401 and a 403 response. Re-ordering this list silently re-labels those responses.
         *
         * <p>The timestamp check appears twice — explicitly, and again inside
         * {@code JwtValidators.createDefault()}. That duplication is in every copy. It is preserved
         * rather than tidied for the same reason: removing it removes one entry from the accumulated
         * error list.
         *
         * @throws IllegalStateException if no issuer allow-list was supplied
         */
        public OAuth2TokenValidator<Jwt> buildValidator() {
            if (allowedIssuers == null) {
                throw new IllegalStateException(
                        "allowedIssuers is required: a decoder with no issuer allow-list accepts any "
                        + "issuer the JWKS happens to sign for. Call allowedIssuers(...) or "
                        + "allowedIssuersCsv(...).");
            }
            List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
            validators.add(new JwtTimestampValidator());
            validators.add(new AllowedIssuersValidator(allowedIssuers));
            validators.addAll(additional);
            validators.add(JwtValidators.createDefault());
            return new DelegatingOAuth2TokenValidator<>(validators);
        }

        /**
         * Builds the decoder with {@link #buildValidator()} installed.
         *
         * <p>Returns the concrete {@link NimbusJwtDecoder} rather than {@link JwtDecoder} because a
         * service running more than one chain declares its decoders as named, non-{@code @Primary}
         * beans and wires each chain's decoder explicitly; the concrete type is what those
         * declarations use.
         *
         * @throws IllegalStateException if no issuer allow-list was supplied
         */
        public NimbusJwtDecoder build() {
            OAuth2TokenValidator<Jwt> chain = buildValidator();
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            decoder.setJwtValidator(chain);
            return decoder;
        }

        private static List<String> parseCsv(String csv) {
            List<String> out = new ArrayList<>();
            if (csv == null) {
                return out;
            }
            for (String part : csv.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
            return out;
        }
    }

    // =====================================================================================
    // 2. The filter chain
    // =====================================================================================

    /**
     * Assembles the generic tail of a servlet {@code SecurityConfig}.
     *
     * <pre>{@code
     * @Bean
     * SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
     *     return ResourceServerChainAssembler.statelessJwtChain(http)
     *             .publicPaths(PublicPaths.AS_SET)              // the service's own D5 value object
     *             .authenticated("/api/<its own prefix>/**")    // the service's own patterns
     *             .jwtAuthenticationConverter(converter)        // the service's own converter
     *             .authenticationEntryPoint(this::onAuthFailure)
     *             .accessDeniedHandler(this::onAccessDenied)
     *             .build();
     * }
     * }</pre>
     *
     * <h2>Rule order is fixed, and it is the order first-match-wins needs</h2>
     *
     * {@code authorizeHttpRequests} evaluates rules in registration order and the first match decides,
     * so a builder that let callers register rules in an arbitrary order would let a broad
     * {@code authenticated()} pattern shadow a narrower role gate that was meant to run first. The
     * order is therefore fixed here, narrowest first:
     *
     * <ol>
     *   <li>{@link #publicPaths(PublicPathSet)} — {@code permitAll()}</li>
     *   <li>{@link #authorizeRules(Customizer)} — the service's own rules (method-scoped matchers,
     *       role gates, anything Spring Security's DSL can express)</li>
     *   <li>{@link #authenticated(String...)} — the blanket bearer-required patterns</li>
     *   <li>{@code anyRequest()} — {@link #anyRequestDenied()} (default) or
     *       {@link #anyRequestAuthenticated()}</li>
     * </ol>
     */
    public static final class FilterChainBuilder {

        private final HttpSecurity http;
        private final List<String> authenticatedPatterns = new ArrayList<>();

        private String[] securityMatcherPatterns;
        private PublicPathSet publicPaths;
        private Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry>
                authorizeRules;
        private boolean anyRequestAuthenticated;
        private JwtDecoder jwtDecoder;
        private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter;
        private AuthenticationEntryPoint authenticationEntryPoint;
        private AccessDeniedHandler accessDeniedHandler;
        private Customizer<HttpSecurity> httpCustomizer;

        private FilterChainBuilder(HttpSecurity http) {
            this.http = Objects.requireNonNull(http, "http");
        }

        /**
         * Scopes this chain to the given patterns ({@link HttpSecurity#securityMatcher(String...)}).
         *
         * <p>Only needed by a service running more than one ordered chain. Omit it for the single-chain
         * case, which is the majority.
         */
        public FilterChainBuilder securityMatcher(String... patterns) {
            Objects.requireNonNull(patterns, "patterns");
            if (patterns.length == 0) {
                throw new IllegalArgumentException("securityMatcher requires at least one pattern");
            }
            this.securityMatcherPatterns = patterns.clone();
            return this;
        }

        /**
         * Permits the service's own public paths, taken from its {@link PublicPathSet}
         * (ADR-MONO-058 § D5).
         *
         * <p>Exact entries are registered as-is; prefix entries — which {@code PublicPathSet}
         * guarantees end in {@code /} — are registered with {@code **} appended, which is what every
         * copy of this tail did by hand.
         *
         * <p>Passing the <em>same</em> {@code PublicPathSet} instance that the service's
         * {@link TenantClaimEnforcer} exemption reads is the point of taking a value object here rather
         * than two string arrays: the paths Spring Security lets through unauthenticated and the paths
         * the tenant gate skips then cannot drift apart.
         */
        public FilterChainBuilder publicPaths(PublicPathSet publicPaths) {
            this.publicPaths = Objects.requireNonNull(publicPaths, "publicPaths");
            return this;
        }

        /** Adds patterns that require an authenticated caller. Additive across calls. */
        public FilterChainBuilder authenticated(String... patterns) {
            Objects.requireNonNull(patterns, "patterns");
            for (String pattern : patterns) {
                authenticatedPatterns.add(Objects.requireNonNull(pattern, "pattern"));
            }
            return this;
        }

        /**
         * The service's own authorization rules, registered after the public paths and before the
         * blanket {@link #authenticated(String...)} patterns — see the class-level ordering note.
         *
         * <p>This is where role gates and method-scoped matchers go. The library never learns a role
         * name; it only decides where in the sequence the service's rules run.
         */
        public FilterChainBuilder authorizeRules(
                Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry>
                        authorizeRules) {
            this.authorizeRules = Objects.requireNonNull(authorizeRules, "authorizeRules");
            return this;
        }

        /**
         * Ends the chain with {@code anyRequest().denyAll()}. This is the default; the method exists so
         * a service that wants the closed tail can still say so out loud.
         */
        public FilterChainBuilder anyRequestDenied() {
            this.anyRequestAuthenticated = false;
            return this;
        }

        /**
         * Ends the chain with {@code anyRequest().authenticated()} instead of {@code denyAll()}.
         *
         * <p>The open-er of the two answers the fleet gave, so it is never the default. It admits any
         * request carrying a valid token to any unlisted path — including one added later by a
         * developer who never looked at this file.
         */
        public FilterChainBuilder anyRequestAuthenticated() {
            this.anyRequestAuthenticated = true;
            return this;
        }

        /**
         * Pins the {@link JwtDecoder} this chain verifies with.
         *
         * <p>Required when the service declares more than one decoder bean. Omit it to let Spring
         * Security resolve the single {@code JwtDecoder} bean from the context, which is what a
         * single-chain service does.
         */
        public FilterChainBuilder jwtDecoder(JwtDecoder jwtDecoder) {
            this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder");
            return this;
        }

        /** The service's own {@code Jwt} → {@code Authentication} converter. */
        public FilterChainBuilder jwtAuthenticationConverter(
                Converter<Jwt, ? extends AbstractAuthenticationToken> converter) {
            this.jwtAuthenticationConverter = Objects.requireNonNull(converter, "converter");
            return this;
        }

        /** The service's own 401 writer. */
        public FilterChainBuilder authenticationEntryPoint(AuthenticationEntryPoint entryPoint) {
            this.authenticationEntryPoint = Objects.requireNonNull(entryPoint, "entryPoint");
            return this;
        }

        /** The service's own 403 writer. */
        public FilterChainBuilder accessDeniedHandler(AccessDeniedHandler accessDeniedHandler) {
            this.accessDeniedHandler = Objects.requireNonNull(accessDeniedHandler, "accessDeniedHandler");
            return this;
        }

        /**
         * A final hook on the {@link HttpSecurity} itself, applied after everything above and before
         * {@code http.build()} — for the configurers this builder deliberately has no opinion about.
         *
         * <p>It exists so that a service with one extra call does not have to abandon the shared
         * assembly and re-type all of it. It is not a place to re-do what the builder already did:
         * re-enabling CSRF or re-opening the session policy here would leave a chain whose posture
         * disagrees with the name it was built from.
         */
        public FilterChainBuilder httpCustomizer(Customizer<HttpSecurity> httpCustomizer) {
            this.httpCustomizer = Objects.requireNonNull(httpCustomizer, "httpCustomizer");
            return this;
        }

        /**
         * Applies the assembly and returns {@code http.build()}.
         *
         * @throws Exception propagated from the Spring Security DSL, as {@code http.build()} declares
         */
        public SecurityFilterChain build() throws Exception {
            if (securityMatcherPatterns != null) {
                http.securityMatcher(securityMatcherPatterns);
            }

            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(this::applyAuthorizationRules)
                    .oauth2ResourceServer(resourceServer -> {
                        resourceServer.jwt(jwt -> {
                            if (jwtDecoder != null) {
                                jwt.decoder(jwtDecoder);
                            }
                            if (jwtAuthenticationConverter != null) {
                                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter);
                            }
                        });
                        if (authenticationEntryPoint != null) {
                            resourceServer.authenticationEntryPoint(authenticationEntryPoint);
                        }
                        if (accessDeniedHandler != null) {
                            resourceServer.accessDeniedHandler(accessDeniedHandler);
                        }
                    });

            if (httpCustomizer != null) {
                httpCustomizer.customize(http);
            }
            return http.build();
        }

        private void applyAuthorizationRules(
                AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
            if (publicPaths != null) {
                String[] exact = publicPaths.exact().toArray(new String[0]);
                if (exact.length > 0) {
                    registry.requestMatchers(exact).permitAll();
                }
                String[] prefixed = publicPaths.prefixes().stream()
                        .map(prefix -> prefix + "**")
                        .toArray(String[]::new);
                if (prefixed.length > 0) {
                    registry.requestMatchers(prefixed).permitAll();
                }
            }
            if (authorizeRules != null) {
                authorizeRules.customize(registry);
            }
            if (!authenticatedPatterns.isEmpty()) {
                registry.requestMatchers(authenticatedPatterns.toArray(new String[0])).authenticated();
            }
            if (anyRequestAuthenticated) {
                registry.anyRequest().authenticated();
            } else {
                registry.anyRequest().denyAll();
            }
        }
    }
}
