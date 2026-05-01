package com.wms.outbound.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * outbound-service security wiring.
 *
 * <p>Authoritative reference: {@code specs/services/outbound-service/architecture.md}
 * § Security and {@code specs/contracts/webhooks/erp-order-webhook.md} § Endpoint
 * (HMAC-only — JWT permitted at this layer).
 *
 * <p>Roles in this service:
 * <ul>
 *   <li>{@code OUTBOUND_READ} — query endpoints</li>
 *   <li>{@code OUTBOUND_WRITE} — manual order creation, picking/packing
 *       confirmations, shipping confirmation</li>
 *   <li>{@code OUTBOUND_ADMIN} — order cancellation, manual TMS retry,
 *       force-saga-fail</li>
 * </ul>
 *
 * <p>The webhook endpoint {@code /webhooks/erp/order} is permitted without JWT —
 * HMAC signature verification is performed inside the controller. This matches
 * the contract spec which says JWT is replaced by per-environment HMAC.
 *
 * <p>JWT role claims may appear under {@code role} (single string or array) or
 * {@code roles} (Spring Security default). Both are mapped to {@code ROLE_*}
 * authorities so {@code @PreAuthorize("hasRole('OUTBOUND_READ')")} works.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            // HMAC-protected — JWT not enforced at the filter chain.
            "/webhooks/erp/order"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // NOTE: granular role enforcement (e.g. OUTBOUND_ADMIN
                        // for post-pick cancel + TMS retry) happens in the
                        // application services (CancelOrderService, etc.) where
                        // the role decision is data-dependent on order/saga
                        // state. The filter chain below only enforces the
                        // coarse READ/WRITE/ADMIN gate per HTTP method.
                        // Read-only paths
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("OUTBOUND_READ", "OUTBOUND_WRITE", "OUTBOUND_ADMIN")
                        // Mutating paths default to WRITE
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole("OUTBOUND_WRITE", "OUTBOUND_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasAnyRole("OUTBOUND_WRITE", "OUTBOUND_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyRole("OUTBOUND_WRITE", "OUTBOUND_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole("OUTBOUND_WRITE", "OUTBOUND_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHORIZED", "Authentication required"))
                        .accessDeniedHandler(forbiddenHandler(objectMapper)));
        return http.build();
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
