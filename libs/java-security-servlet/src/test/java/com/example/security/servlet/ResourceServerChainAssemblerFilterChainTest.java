package com.example.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The filter-chain half of the promoted assembly (ADR-MONO-058 § D4), driven through a <strong>real
 * Spring Security filter chain</strong> rather than asserted from the builder's fields.
 *
 * <p>A builder that returns a plausible-looking object and never routes a request correctly passes
 * every field-level unit test and 401s in production, so every assertion here is an HTTP outcome.
 *
 * <p>Every path, role and token in this file is synthetic. The library owns none of them — that is the
 * § D4 boundary, and a fixture that borrowed a real service's path list would quietly assert that
 * boundary away.
 */
@SpringBootTest(classes = ResourceServerChainAssemblerFilterChainTest.TestApp.class)
@AutoConfigureMockMvc
@DisplayName("ResourceServerChainAssembler.statelessJwtChain — routing through the real chain")
class ResourceServerChainAssemblerFilterChainTest {

    static final String USER_TOKEN = "user-token";
    static final String ADMIN_TOKEN = "admin-token";
    static final String UNKNOWN_TOKEN = "not-a-token";

    static final String ENTRY_POINT_MARKER = "custom-entry-point";
    static final String ACCESS_DENIED_MARKER = "custom-access-denied";

    @Autowired
    private MockMvc mvc;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Nested
    @DisplayName("public paths — the PublicPathSet input (D5) is routed to permitAll")
    class PublicPaths {

        @Test
        @DisplayName("an EXACT public path is reachable with no token")
        void exactPublicPathIsAnonymous() throws Exception {
            mvc.perform(get("/probe"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("probe"));
        }

        @Test
        @DisplayName("a path under a PREFIX entry is reachable with no token")
        void prefixPublicPathIsAnonymous() throws Exception {
            // The builder appends "**" to each prefix, which is what the hand-written copies did.
            mvc.perform(get("/probe/deep/detail"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("deep"));
        }
    }

    @Nested
    @DisplayName("authenticated paths")
    class AuthenticatedPaths {

        @Test
        @DisplayName("no token → 401 through the supplied entry point")
        void anonymousIsRejectedByTheSuppliedEntryPoint() throws Exception {
            mvc.perform(get("/api/resource"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(ENTRY_POINT_MARKER));
        }

        @Test
        @DisplayName("an undecodable token → 401 through the supplied entry point")
        void badTokenIsRejected() throws Exception {
            mvc.perform(get("/api/resource").header(HttpHeaders.AUTHORIZATION, bearer(UNKNOWN_TOKEN)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(ENTRY_POINT_MARKER));
        }

        @Test
        @DisplayName("a valid token → 200")
        void validTokenIsAdmitted() throws Exception {
            mvc.perform(get("/api/resource").header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("resource"));
        }
    }

    @Nested
    @DisplayName("rule order — authorizeRules must run BEFORE the blanket authenticated patterns")
    class RuleOrder {

        @Test
        @DisplayName("an under-privileged caller on a role-gated path → 403 through the supplied handler")
        void roleGateWinsOverTheBroaderAuthenticatedRule() throws Exception {
            // /api/restricted/** is ALSO matched by the blanket .authenticated("/api/**") rule. First
            // match wins in authorizeHttpRequests, so this 403 is only reachable if the builder
            // registered the caller's rules ahead of that blanket rule. Register them the other way
            // round and this test returns 200 — which is the whole point of fixing the order.
            mvc.perform(get("/api/restricted/resource")
                            .header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(ACCESS_DENIED_MARKER));
        }

        @Test
        @DisplayName("a privileged caller on the same path → 200")
        void roleGateAdmitsTheRightRole() throws Exception {
            mvc.perform(get("/api/restricted/resource")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("restricted"));
        }

        @Test
        @DisplayName("public paths still win over both — they are registered first")
        void publicPathsOutrankEverything() throws Exception {
            mvc.perform(get("/probe")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("the anyRequest() tail")
    class AnyRequestTail {

        @Test
        @DisplayName("default: an unlisted path denies an anonymous caller (401 via the entry point)")
        void unlistedPathDeniesAnonymous() throws Exception {
            mvc.perform(get("/unlisted"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(ENTRY_POINT_MARKER));
        }

        @Test
        @DisplayName("default: an unlisted path denies even a VALID token — denyAll, not authenticated")
        void unlistedPathDeniesAValidToken() throws Exception {
            // This is the assertion that separates denyAll from authenticated. Under an
            // authenticated() tail this request would be 200.
            mvc.perform(get("/unlisted").header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(ACCESS_DENIED_MARKER));
        }

        @Test
        @DisplayName("anyRequestAuthenticated(): the second chain admits any valid token")
        void authenticatedTailAdmitsAnyValidToken() throws Exception {
            mvc.perform(get("/scoped/resource").header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("scoped"));
        }

        @Test
        @DisplayName("anyRequestAuthenticated(): still 401 without a token")
        void authenticatedTailStillRequiresAToken() throws Exception {
            mvc.perform(get("/scoped/resource")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("securityMatcher — the scoped chain is a separate chain")
    class ScopedChain {

        @Test
        @DisplayName("the scoped chain does not inherit the other chain's entry point")
        void scopedChainIsNotTheDefaultChain() throws Exception {
            // Both chains exist in one context. If securityMatcher had not scoped the first one, the
            // ordered chains would collapse into each other and this body would carry the other
            // chain's marker.
            MvcResult result = mvc.perform(get("/scoped/resource"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString()).doesNotContain(ENTRY_POINT_MARKER);
        }

        @Test
        @DisplayName("a path outside the scope falls through to the other chain")
        void outOfScopePathUsesTheOtherChain() throws Exception {
            mvc.perform(get("/api/resource"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(ENTRY_POINT_MARKER));
        }
    }

    @Nested
    @DisplayName("the unconditional posture — stateless, CSRF-disabled")
    class Posture {

        @Test
        @DisplayName("CSRF is disabled: a POST with no CSRF token is not 403'd")
        void csrfIsDisabled() throws Exception {
            mvc.perform(post("/api/resource").header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("created"));
        }

        @Test
        @DisplayName("sessions are STATELESS: an authenticated request creates no HTTP session")
        void noSessionIsCreated() throws Exception {
            MvcResult result = mvc.perform(
                            get("/api/resource").header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getRequest().getSession(false))
                    .as("SessionCreationPolicy.STATELESS must leave no session behind")
                    .isNull();
        }
    }

    // =================================================================================
    // Fixture
    // =================================================================================

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableWebSecurity
    static class TestApp {

        /**
         * A stub decoder: the library never decodes anything itself, so the real signing machinery
         * would only add moving parts to a test about routing. The token STRING selects the claims.
         */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                List<String> roles = switch (token) {
                    case USER_TOKEN -> List.of("USER");
                    case ADMIN_TOKEN -> List.of("ADMIN");
                    default -> throw new BadJwtException("unrecognised test token: " + token);
                };
                Instant now = Instant.now();
                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject("actor-1")
                        .claim("roles", roles)
                        .issuedAt(now.minusSeconds(60))
                        .expiresAt(now.plusSeconds(600))
                        .build();
            };
        }

        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
            authorities.setAuthorityPrefix("ROLE_");
            authorities.setAuthoritiesClaimName("roles");
            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(authorities);
            return converter;
        }

        /** A scoped chain with the OPEN-er tail, so both tails are exercised in one context. */
        @Bean
        @Order(1)
        SecurityFilterChain scopedChain(HttpSecurity http,
                                        JwtDecoder jwtDecoder,
                                        JwtAuthenticationConverter converter) throws Exception {
            return ResourceServerChainAssembler.statelessJwtChain(http)
                    .securityMatcher("/scoped/**")
                    .anyRequestAuthenticated()
                    .jwtDecoder(jwtDecoder)
                    .jwtAuthenticationConverter(converter)
                    .build();
        }

        /** The single-chain shape, with the default (closed) tail. */
        @Bean
        @Order(2)
        SecurityFilterChain mainChain(HttpSecurity http,
                                      JwtDecoder jwtDecoder,
                                      JwtAuthenticationConverter converter) throws Exception {
            return ResourceServerChainAssembler.statelessJwtChain(http)
                    .publicPaths(PublicPathSet.of(Set.of("/probe"), Set.of("/probe/deep/")))
                    .authorizeRules(registry ->
                            registry.requestMatchers("/api/restricted/**").hasRole("ADMIN"))
                    .authenticated("/api/**")
                    .jwtDecoder(jwtDecoder)
                    .jwtAuthenticationConverter(converter)
                    .authenticationEntryPoint((request, response, exception) -> {
                        response.setStatus(401);
                        response.getWriter().write(ENTRY_POINT_MARKER);
                    })
                    .accessDeniedHandler((request, response, exception) -> {
                        response.setStatus(403);
                        response.getWriter().write(ACCESS_DENIED_MARKER);
                    })
                    .build();
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe")
        String probe() {
            return "probe";
        }

        @GetMapping("/probe/deep/detail")
        String deep() {
            return "deep";
        }

        @GetMapping("/api/resource")
        String resource() {
            return "resource";
        }

        @PostMapping("/api/resource")
        String createResource() {
            return "created";
        }

        @GetMapping("/api/restricted/resource")
        String restricted() {
            return "restricted";
        }

        @GetMapping("/unlisted")
        String unlisted() {
            return "unlisted";
        }

        @GetMapping("/scoped/resource")
        String scoped() {
            return "scoped";
        }
    }
}
