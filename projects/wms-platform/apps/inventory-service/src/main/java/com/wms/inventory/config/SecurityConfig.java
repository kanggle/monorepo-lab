package com.wms.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.ResourceServerChainAssembler;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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

/**
 * inventory-service security wiring.
 *
 * <p>Authoritative reference:
 * {@code specs/contracts/http/inventory-service-api.md} § Auth.
 *
 * <p>Roles in this service:
 * <ul>
 *   <li>{@code INVENTORY_READ} — query endpoints</li>
 *   <li>{@code INVENTORY_WRITE} — adjustments / mark-damaged / transfers</li>
 *   <li>{@code INVENTORY_ADMIN} — write-off-damaged / RESERVED-bucket adjustment / manual release</li>
 *   <li>{@code INVENTORY_RESERVE} — service account used by {@code outbound-service}
 *       for the reservation REST surface</li>
 * </ul>
 *
 * <p>JWT role claims may appear under {@code role} (single string or array) or
 * {@code roles} (Spring Security default). Both are mapped to {@code ROLE_*}
 * authorities so {@code @PreAuthorize("hasRole('INVENTORY_READ')")} works.
 */
// TASK-MONO-335 (pattern from TASK-BE-334): servlet-web only. The filter chain
// depends on HttpSecurity, which exists solely in a servlet web context. Without
// this, a @SpringBootTest with webEnvironment=NONE (the inventory IT base) fails
// to load — securityFilterChain has no HttpSecurity bean to inject. Production
// runs as a servlet web app, so the condition is true there (security
// unchanged); non-web contexts (the Kafka-consumer / DB ITs) cleanly skip it.
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
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
                // inventory-service is on the authenticated() side and says so out loud —
                // preserved verbatim from the pre-D4 chain, not inherited from a default.
                .anyRequestAuthenticated()
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                .authenticationEntryPoint(authenticationEntryPoint(objectMapper))
                .accessDeniedHandler(forbiddenHandler(objectMapper))
                .build();
    }

    /**
     * TASK-MONO-019: distinguishes cross-tenant token misuse (403
     * {@code TENANT_FORBIDDEN}) from generic authentication failures.
     */
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

    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        JwtGrantedAuthoritiesConverter defaults = new JwtGrantedAuthoritiesConverter();
        defaults.setAuthorityPrefix("ROLE_");
        defaults.setAuthoritiesClaimName("roles");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(defaults.convert(jwt));
            Object roleClaim = jwt.getClaim("role");
            authorities.addAll(extractRoles(roleClaim));
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
