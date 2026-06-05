package com.example.admin.support;

import com.example.admin.infrastructure.security.OperatorAuthenticationFilter;
import com.example.security.jwt.JwtVerifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;

/**
 * Test-only security config for {@link org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest}
 * slice tests.
 *
 * Mirrors the production {@code SecurityConfig} but takes a {@link JwtVerifier}
 * bean wired by the test class (typically backed by {@link OperatorJwtTestFixture}).
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class SliceTestSecurityConfig {

    @Bean
    public OperatorAuthenticationFilter operatorAuthenticationFilter(JwtVerifier operatorJwtVerifier) {
        return new OperatorAuthenticationFilter(operatorJwtVerifier, "admin");
    }

    @Bean
    public SecurityFilterChain testFilterChain(HttpSecurity http,
                                               OperatorAuthenticationFilter operatorFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(operatorFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().denyAll())
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
                        }));
        return http.build();
    }
}
