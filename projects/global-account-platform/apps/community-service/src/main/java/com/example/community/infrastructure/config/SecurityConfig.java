package com.example.community.infrastructure.config;

import com.example.community.infrastructure.security.AccountAuthenticationFilter;
import com.gap.security.jwt.JwtVerifier;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public AccountAuthenticationFilter accountAuthenticationFilter(JwtVerifier communityJwtVerifier) {
        return new AccountAuthenticationFilter(communityJwtVerifier);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AccountAuthenticationFilter accountFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(accountFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/internal/**").denyAll()
                        .requestMatchers("/api/community/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, resp, e) -> {
                            resp.setStatus(HttpStatus.UNAUTHORIZED.value());
                            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            resp.getWriter().write(
                                    "{\"code\":\"TOKEN_INVALID\",\"message\":\"Authentication required\""
                                            + ",\"timestamp\":\"" + Instant.now() + "\"}");
                        })
                        .accessDeniedHandler((req, resp, e) -> {
                            resp.setStatus(HttpStatus.FORBIDDEN.value());
                            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            resp.getWriter().write(
                                    "{\"code\":\"PERMISSION_DENIED\",\"message\":\"Access denied\""
                                            + ",\"timestamp\":\"" + Instant.now() + "\"}");
                        })
                );
        return http.build();
    }
}
