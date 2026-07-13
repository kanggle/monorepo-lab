package com.wms.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.api.dto.ApiErrorEnvelope;
import com.example.security.oauth2.TenantClaimValidator;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.expression.DefaultHttpSecurityExpressionHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * admin-service Spring Security wiring.
 *
 * <ul>
 *   <li>OAuth2 Resource Server, RS256 JWT, GAP JWKS — see {@link com.wms.admin.infra.security.OAuth2ResourceServerConfig}.</li>
 *   <li>{@code @EnableMethodSecurity} — application-layer {@code @PreAuthorize} per
 *       architecture.md § Security.</li>
 *   <li>Role hierarchy:
 *       {@code WMS_SUPERADMIN > WMS_ADMIN > WMS_OPERATOR > WMS_VIEWER}.</li>
 *   <li>Stateless; CSRF disabled (token-based auth).</li>
 *   <li>Cross-tenant tokens surface as 403 {@code TENANT_FORBIDDEN}, generic
 *       auth failures as 401 {@code UNAUTHORIZED} — same pattern as
 *       master-service (TASK-MONO-019).</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus"
    };

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy(
                "ROLE_WMS_SUPERADMIN > ROLE_WMS_ADMIN\n"
                        + "ROLE_WMS_ADMIN > ROLE_WMS_OPERATOR\n"
                        + "ROLE_WMS_OPERATOR > ROLE_WMS_VIEWER");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                            RoleHierarchy roleHierarchy) throws Exception {
        DefaultHttpSecurityExpressionHandler expressionHandler = new DefaultHttpSecurityExpressionHandler();
        expressionHandler.setRoleHierarchy(roleHierarchy);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(forbiddenHandler(objectMapper)));

        // Apply role hierarchy to URL-level matchers (method-level uses
        // MethodSecurityConfig — see GlobalMethodSecurityConfig below).
        return http.build();
    }

    /**
     * Domain key for the entitlement-trust READ-visibility synthesis. A token
     * whose signed {@code entitled_domains} claim contains {@code wms} is granted
     * {@code ROLE_WMS_VIEWER} (READ only) even when it carries no WMS role claim.
     */
    static final String ENTITLEMENT_DOMAIN = "wms";

    /** The single READ-visibility role synthesised from entitlement-trust. */
    static final String VIEWER_ROLE = "ROLE_WMS_VIEWER";

    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        JwtGrantedAuthoritiesConverter defaults = new JwtGrantedAuthoritiesConverter();
        defaults.setAuthorityPrefix("ROLE_");
        defaults.setAuthoritiesClaimName("roles");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(defaults.convert(jwt));
            Object roleClaim = jwt.getClaim("role");
            authorities.addAll(extractRoles(roleClaim));
            // Entitlement-trust dual-accept (ADR-MONO-019 § D5, ADR-MONO-020 D4 —
            // TASK-MONO-162): a wms-entitled token (entitled_domains ∋ "wms") is
            // granted ROLE_WMS_VIEWER so the @PreAuthorize("hasRole('WMS_VIEWER')")
            // READ dashboards pass. This synthesises ONLY the VIEWER role — the
            // WRITE-gated roles (WMS_OPERATOR/WMS_ADMIN/WMS_SUPERADMIN) are
            // unaffected, so entitlement-trust never widens mutation authority
            // (READ visibility only; net-zero for role/scope/SUPER_ADMIN tokens —
            // entitled_domains is read only from the RS256/JWKS-verified token).
            if (TenantClaimValidator.isEntitled(jwt, ENTITLEMENT_DOMAIN)) {
                authorities.add(new SimpleGrantedAuthority(VIEWER_ROLE));
            }
            return authorities;
        });
        return converter;
    }

    private static List<GrantedAuthority> extractRoles(Object claim) {
        if (claim == null) {
            return List.of();
        }
        if (claim instanceof String s && !s.isBlank()) {
            return List.of(new SimpleGrantedAuthority("ROLE_" + s));
        }
        if (claim instanceof Collection<?> list) {
            List<GrantedAuthority> out = new ArrayList<>();
            for (Object elem : list) {
                if (elem instanceof String s && !s.isBlank()) {
                    out.add(new SimpleGrantedAuthority("ROLE_" + s));
                }
            }
            return out;
        }
        return List.of();
    }

    private AccessDeniedHandler forbiddenHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> writeError(response, objectMapper,
                HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                "Insufficient privileges for this operation");
    }

    private AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            OAuth2Error oauthError = extractOAuth2Error(authException);
            if (oauthError != null
                    && TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(oauthError.getErrorCode())) {
                String message = oauthError.getDescription() != null
                        ? oauthError.getDescription()
                        : "Cross-tenant access denied";
                writeError(response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                        "TENANT_FORBIDDEN", message);
                return;
            }
            writeError(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED", "Authentication required");
        };
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

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper,
                                   int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        byte[] body = objectMapper.writeValueAsBytes(ApiErrorEnvelope.of(code, message));
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.getOutputStream().flush();
    }
}
