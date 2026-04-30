package com.example.admin.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

/**
 * Shared cryptographic primitives. Exposes a single {@link SecureRandom} bean
 * so randomness sources are substitutable in tests (deterministic seed) while
 * production retains the default strong JCA instance.
 *
 * <p>Consumers include {@link com.example.admin.application.TotpEnrollmentService}
 * (recovery codes) and may in future include {@code TotpGenerator} via
 * constructor injection. No domain logic here — plain infrastructure wiring.
 */
@Configuration
public class CryptoConfig {

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
