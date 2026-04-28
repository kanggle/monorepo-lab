package com.wms.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.adapter.in.web.dto.response.ApiErrorEnvelope;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus"
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
