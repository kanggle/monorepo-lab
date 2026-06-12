package com.example.finance.ledger.infrastructure.security;

import com.example.finance.ledger.presentation.security.PublicPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ledger-service Spring Security configuration.
 *
 * <ul>
 *   <li>{@code /actuator/{health,info,prometheus}} — public</li>
 *   <li>{@code /api/finance/**} — bearer token required (RS256, IAM JWKS)</li>
 *   <li>everything else — denied</li>
 * </ul>
 *
 * Read-only API (no mutating endpoints in the first increment). Mirrors
 * account-service exactly. Error handling (401/403) is delegated to
 * {@link SecurityErrorHandler}.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityErrorHandler securityErrorHandler;

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
                        .requestMatchers(exact).permitAll()
                        .requestMatchers(prefixed).permitAll()
                        .requestMatchers("/api/finance/**").authenticated()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new ActorContextJwtAuthenticationConverter()))
                        .authenticationEntryPoint(securityErrorHandler::onAuthenticationFailure)
                        .accessDeniedHandler(securityErrorHandler::onAccessDenied)
                );
        return http.build();
    }
}
