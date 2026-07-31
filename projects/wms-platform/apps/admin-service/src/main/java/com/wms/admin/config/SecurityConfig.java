package com.wms.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.api.dto.ApiErrorEnvelope;
import com.wms.admin.infra.security.PublicPaths;
import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.ResourceServerChainAssembler;
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
        // PRESERVED AS MEASURED, NOT AS INTENDED (TASK-BE-569, ADR-MONO-058 § D4).
        //
        // This handler is built and then never attached to anything — it was already
        // dead before the D4 adoption, and D4 is a mechanism swap that must not change
        // behaviour, so it is carried over verbatim rather than either wired up or
        // deleted. Nothing is lost by that today: this chain's authorization tail is
        // anyRequest().authenticated(), so there is no URL-level role expression for a
        // hierarchy to widen. The role hierarchy that IS load-bearing is the method-level
        // one — @EnableMethodSecurity picks up the roleHierarchy() bean below from the
        // context, which is why the @PreAuthorize("hasRole('WMS_VIEWER')") dashboards
        // admit a WMS_ADMIN token (pinned by SecurityChainAssemblyParityTest).
        //
        // Wiring it up (or removing it) is an authorization-policy decision, not part of
        // this mechanical adoption — flagged for a follow-up task.
        DefaultHttpSecurityExpressionHandler expressionHandler = new DefaultHttpSecurityExpressionHandler();
        expressionHandler.setRoleHierarchy(roleHierarchy);

        // The four configurers ResourceServerChainAssembler deliberately has no opinion
        // about, applied here rather than through its httpCustomizer(...) hook because
        // every one of these HttpSecurity methods declares `throws Exception`, which a
        // Customizer<HttpSecurity> lambda cannot propagate. Disabling a configurer only
        // removes it from the builder, so applying them before the assembler is
        // order-equivalent to the pre-D4 single fluent chain.
        //
        // cors/httpBasic/formLogin are belt-and-braces (none is installed by default on a
        // context that declares its own SecurityFilterChain bean); .logout(disable) is
        // load-bearing — HttpSecurityConfiguration DOES install LogoutFilter by default,
        // and leaving it on would make /logout answer 302 instead of the 401 this
        // stateless resource server returns today.
        http
                .cors(cors -> cors.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable());

        return ResourceServerChainAssembler.statelessJwtChain(http)
                .publicPaths(PublicPaths.asSet())
                // ADR-MONO-058 § D4 measured the anyRequest() tail as a genuinely split
                // axis and made the assembler default to the CLOSED answer (denyAll()).
                // admin-service is on the authenticated() side and says so out loud —
                // preserved verbatim from the pre-D4 chain, not inherited from a default.
                .anyRequestAuthenticated()
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                .authenticationEntryPoint(authenticationEntryPoint(objectMapper))
                .accessDeniedHandler(forbiddenHandler(objectMapper))
                .build();
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
