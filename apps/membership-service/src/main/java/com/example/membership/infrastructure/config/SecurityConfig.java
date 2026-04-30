package com.example.membership.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${internal.api.token:}")
    private String internalApiToken;

    /**
     * When true, {@code /internal/**} paths are let through without a token when
     * the token is unconfigured. This is intended for {@code @WebMvcTest} slice
     * tests. The default is {@code false} (fail-closed).
     */
    @Value("${internal.api.bypass-when-unconfigured:false}")
    private boolean bypassProperty;

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public InternalApiFilter internalApiFilter() {
        boolean testProfileActive = Arrays.asList(environment.getActiveProfiles()).contains("test");
        boolean bypass = bypassProperty || testProfileActive;
        return new InternalApiFilter(internalApiToken, bypass);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(internalApiFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().denyAll()
                );
        return http.build();
    }
}
