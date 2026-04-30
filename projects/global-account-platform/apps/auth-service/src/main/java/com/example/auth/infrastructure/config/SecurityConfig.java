package com.example.auth.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/oauth/**").permitAll()
                        // Password change endpoint — gateway enforces JWT and forwards
                        // X-Account-Id (see PasswordController, auth-api.md PATCH /api/auth/password).
                        .requestMatchers("/api/auth/password").permitAll()
                        .requestMatchers("/api/auth/password-reset/**").permitAll()
                        // Session management endpoints — gateway enforces JWT and forwards
                        // X-Account-Id / X-Device-Id headers (see AccountSessionController).
                        .requestMatchers("/api/accounts/me/sessions/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().denyAll()
                );

        return http.build();
    }
}
