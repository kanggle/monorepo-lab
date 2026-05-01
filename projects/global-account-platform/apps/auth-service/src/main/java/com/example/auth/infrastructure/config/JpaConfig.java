package com.example.auth.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Explicit JPA scanning for the auth-service's own persistence packages.
 * Required because java-messaging's {@code OutboxJpaConfig} declares its own
 * {@code @EnableJpaRepositories}, which suppresses Spring Boot's default
 * JPA repository auto-scanning. See TASK-BE-047.
 *
 * <p>TASK-BE-252: added {@code oauth2.persistence} package for OAuthClientEntity et al.
 * Also exposes {@link PasswordEncoder} bean used by
 * {@link com.example.auth.infrastructure.oauth2.persistence.JpaRegisteredClientRepository}
 * to BCrypt-hash client secrets before storage.
 */
@Configuration
@EnableJpaRepositories(basePackages = {
        "com.example.auth.infrastructure.persistence",
        "com.example.auth.infrastructure.oauth2.persistence"
})
@EntityScan(basePackages = {
        "com.example.auth.infrastructure.persistence",
        "com.example.auth.infrastructure.oauth2.persistence"
})
public class JpaConfig {

    /**
     * Delegating password encoder — used for OAuth client secret hashing and verification.
     *
     * <p>The delegating encoder understands the {@code {bcrypt}hash} format stored in
     * the {@code oauth_clients.client_secret_hash} column (and returned by
     * {@link com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper} as
     * {@code RegisteredClient.clientSecret}). SAS's
     * {@code ClientSecretAuthenticationProvider} calls this encoder's
     * {@link PasswordEncoder#matches} method to verify submitted client secrets.
     *
     * <p>Using a plain {@code BCryptPasswordEncoder} here would cause verification to fail
     * because BCrypt does not understand the {@code {bcrypt}} prefix.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
