package com.wms.master.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.example.security.oauth2.TenantClaimValidator;
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
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

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
                        .authenticationEntryPoint(authenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(forbiddenHandler(objectMapper)));
        return http.build();
    }

    /**
     * Maps the JWT {@code role} claim to a Spring authority prefixed with
     * {@code ROLE_}. If a single token carries multiple roles, the claim may be
     * an array of strings.
     */
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

    /**
     * Authentication entry point that distinguishes cross-tenant token misuse
     * from generic authentication failures. {@link TenantClaimValidator} raises
     * the {@code tenant_mismatch} error code when the {@code tenant_id} claim
     * does not match the {@code wms} tenant; the OAuth2 Resource Server filter
     * normally surfaces all token-validation failures as 401, but here we map
     * tenant mismatch to 403 with the {@code TENANT_FORBIDDEN} envelope code
     * (TASK-MONO-019 acceptance criteria).
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

    /**
     * Walks the cause chain of an {@link org.springframework.security.core.AuthenticationException}
     * looking for the granular {@link OAuth2Error} attached by Spring Security's
     * Jwt validators. {@link JwtValidationException} preserves all individual
     * validator errors (e.g. {@code tenant_mismatch}, {@code invalid_issuer})
     * — prefer those over the generic {@code invalid_token} wrapper.
     */
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
