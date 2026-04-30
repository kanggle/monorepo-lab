package com.example.account.infrastructure.config;

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
     * When {@code true}, the {@link InternalApiFilter} bypasses {@code /internal/**}
     * authentication when no token is configured. Intended for {@code @WebMvcTest}
     * slice tests. Production must keep this {@code false} (default) so that an
     * unconfigured token produces a fail-closed 401.
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
                        .requestMatchers("/api/accounts/signup").permitAll()
                        // TASK-BE-114: token in body is the auth — no JWT required.
                        .requestMatchers("/api/accounts/signup/verify-email").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().denyAll()
                );

        return http.build();
    }
}
