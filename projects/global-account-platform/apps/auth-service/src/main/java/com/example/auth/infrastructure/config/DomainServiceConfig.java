package com.example.auth.infrastructure.config;

import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.token.TokenReuseDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires pure domain services (framework-free classes) as Spring beans so the
 * application layer can inject them without the domain package depending on
 * Spring stereotypes.
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public TokenReuseDetector tokenReuseDetector(RefreshTokenRepository refreshTokenRepository) {
        return new TokenReuseDetector(refreshTokenRepository);
    }
}
