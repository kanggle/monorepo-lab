package com.example.erp.approval.infrastructure.config;

import com.example.erp.approval.domain.delegation.DelegationGrantRepository;
import com.example.erp.approval.domain.delegation.DelegationResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires framework-free domain collaborators as Spring beans.
 *
 * <p>The {@code domain} layer stays free of framework stereotypes (architecture.md
 * § "domain/ ← pure Java, no framework"); collaborators that need dependency
 * injection are declared here in the infrastructure layer instead of carrying a
 * {@code @Component} annotation themselves.
 */
@Configuration
public class DomainConfig {

    @Bean
    public DelegationResolver delegationResolver(DelegationGrantRepository delegationGrantRepository) {
        return new DelegationResolver(delegationGrantRepository);
    }
}
