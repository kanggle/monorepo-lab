package com.kanggle.platformconsole.bff.infrastructure.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;
import java.time.Instant;

/**
 * console-bff Spring Security configuration.
 *
 * <p>Architecture.md § Auth Flow inbound (AC-7):
 * <ul>
 *   <li>{@code /actuator/health}, {@code /actuator/info} — public (Traefik probe)</li>
 *   <li>{@code /actuator/prometheus/**} — public (observability scrape)</li>
 *   <li>all other endpoints — {@code authenticated()} (RS256 JWKS = GAP issuer)</li>
 * </ul>
 *
 * <p>The {@code X-Operator-Token} header is NOT treated as an inbound principal.
 * It is read by {@code OperatorCredentialContext} ({@code @RequestScope}) for
 * outbound dispatch only — the inbound auth filter never sees it as a principal.
 *
 * <p>Three composition routes are live today — {@code DomainHealthController},
 * {@code NotificationAggregatorController}, and {@code OperatorOverviewController}
 * (all backed by the shared {@code CompositionEngine} fan-out). The
 * {@code authenticated()} rule ensures that any future composition endpoint
 * cannot silently slip past authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String[] exact = PublicPaths.EXACT.toArray(new String[0]);
        String[] prefixed = PublicPaths.PREFIXES.stream()
                .map(p -> p + "**")
                .toArray(String[]::new);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Actuator endpoints — defense in depth (3 matchers):
                        //   (1) AntPathRequestMatcher("/actuator/**") — pure path match,
                        //       bypasses MVC pattern resolution and endpoint id selectors
                        //       entirely. CI surface (PR #669 cycles 5-7): both
                        //       requestMatchers(String) and EndpointRequest variants
                        //       missed /actuator/prometheus though /actuator/health
                        //       matched — strongest hypothesis is matcher resolution
                        //       not seeing EndpointHandlerMapping at construction time.
                        //   (2) EndpointRequest.toAnyEndpoint() — actuator-aware,
                        //       redundant on top of (1) but kept as documented
                        //       Spring Boot pattern for clarity.
                        //   (3) The boundary is `management.endpoints.web.exposure.include`
                        //       in application.yml — any actuator endpoint not on that
                        //       list returns 404 regardless of authorization.
                        .requestMatchers(new AntPathRequestMatcher("/actuator/**")).permitAll()
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                        // Spring MVC's default ERROR dispatch forwards to /error. If a
                        // public endpoint (e.g. /actuator/prometheus) throws, the
                        // ExceptionTranslationFilter forwards to /error which is THEN
                        // re-evaluated against the security chain. Without permitAll on
                        // /error, the original throw is hidden behind a 401 from the
                        // error path — exactly what PR #669 8th cycle DEBUG output
                        // showed: "Securing GET /actuator/prometheus → Secured →
                        // Securing GET /error → 401". Standard Spring Boot practice is
                        // /error permitAll (anonymous OK) so the underlying exception
                        // surfaces with its proper status code + body.
                        .requestMatchers(new AntPathRequestMatcher("/error")).permitAll()
                        .requestMatchers(exact).permitAll()
                        .requestMatchers(prefixed).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> {}) // RS256, JWKS from spring.security.oauth2.resourceserver.jwt.jwk-set-uri
                        .authenticationEntryPoint(SecurityConfig::onAuthenticationFailure)
                        .accessDeniedHandler(SecurityConfig::onAccessDenied)
                );
        return http.build();
    }

    static void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        org.springframework.security.core.AuthenticationException e)
            throws IOException {
        String code = "UNAUTHORIZED";
        int status = HttpStatus.UNAUTHORIZED.value();
        String message = "Authentication required";

        OAuth2Error oauthError = extractOAuth2Error(e);
        if (oauthError != null && oauthError.getDescription() != null) {
            message = oauthError.getDescription();
        }
        writeError(response, status, code, message);
    }

    static void onAccessDenied(HttpServletRequest request,
                               HttpServletResponse response,
                               org.springframework.security.access.AccessDeniedException e)
            throws IOException {
        writeError(response, HttpStatus.FORBIDDEN.value(),
                "PERMISSION_DENIED", "Access denied");
    }

    private static OAuth2Error extractOAuth2Error(Throwable t) {
        Throwable cur = t;
        OAuth2Error fallback = null;
        while (cur != null) {
            if (cur instanceof JwtValidationException jve) {
                for (OAuth2Error err : jve.getErrors()) {
                    if (err != null && err.getErrorCode() != null
                            && !"invalid_token".equals(err.getErrorCode())) {
                        return err;
                    }
                }
            }
            if (cur instanceof InvalidBearerTokenException ibte) {
                OAuth2Error err = ibte.getError();
                if (err != null) fallback = err;
            }
            cur = cur.getCause();
        }
        return fallback;
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
            response.getWriter().write(
                    "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
        }
    }
}
