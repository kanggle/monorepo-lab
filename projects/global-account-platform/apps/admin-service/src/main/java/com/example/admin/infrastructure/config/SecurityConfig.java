package com.example.admin.infrastructure.config;

import com.example.admin.application.port.TokenBlacklistPort;
import com.example.admin.infrastructure.security.BootstrapAuthenticationFilter;
import com.example.admin.infrastructure.security.BootstrapTokenService;
import com.example.admin.infrastructure.security.OperatorAuthenticationFilter;
import com.example.security.jwt.JwtVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;

/**
 * Admin-service security configuration.
 *
 * <p>Authorization is enforced exclusively by {@code RequiresPermissionAspect}
 * (see rbac.md "Permission Evaluation Algorithm"). Spring Security handles only
 * authentication (JWT verification via {@link OperatorAuthenticationFilter})
 * and the final fallthrough denyAll. {@code @EnableMethodSecurity} is
 * intentionally NOT present: all authorization decisions flow through the
 * single aspect path (one decision site, one audit write).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public OperatorAuthenticationFilter operatorAuthenticationFilter(
            JwtVerifier operatorJwtVerifier,
            @Value("${admin.jwt.expected-token-type:admin}") String expectedTokenType,
            TokenBlacklistPort tokenBlacklist) {
        return new OperatorAuthenticationFilter(operatorJwtVerifier, expectedTokenType, tokenBlacklist);
    }

    @Bean
    public BootstrapAuthenticationFilter bootstrapAuthenticationFilter(
            BootstrapTokenService bootstrapTokenService) {
        return new BootstrapAuthenticationFilter(bootstrapTokenService);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           OperatorAuthenticationFilter operatorFilter,
                                           BootstrapAuthenticationFilter bootstrapFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bootstrap filter runs first — it only matches the 2FA
                // enroll/verify sub-tree and is a no-op on every other path.
                .addFilterBefore(bootstrapFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(operatorFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        // Unauthenticated sub-tree (admin-api.md Authentication Exceptions).
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/.well-known/admin/jwks.json").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/admin/auth/login",
                                "/api/admin/auth/2fa/enroll",
                                "/api/admin/auth/2fa/verify",
                                // TASK-BE-040: refresh runs without operator JWT.
                                "/api/admin/auth/refresh").permitAll()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, resp, e) -> {
                            resp.setStatus(HttpStatus.UNAUTHORIZED.value());
                            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            resp.getWriter().write(
                                    "{\"code\":\"TOKEN_INVALID\",\"message\":\"Authentication required\""
                                            + ",\"timestamp\":\"" + Instant.now().toString() + "\"}");
                        })
                        .accessDeniedHandler((req, resp, e) -> {
                            resp.setStatus(HttpStatus.FORBIDDEN.value());
                            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            resp.getWriter().write(
                                    "{\"code\":\"PERMISSION_DENIED\",\"message\":\"Operator role insufficient\""
                                            + ",\"timestamp\":\"" + Instant.now().toString() + "\"}");
                        })
                );

        return http.build();
    }
}
