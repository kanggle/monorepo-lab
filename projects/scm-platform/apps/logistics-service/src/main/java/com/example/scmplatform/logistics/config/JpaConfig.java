package com.example.scmplatform.logistics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA configuration — enables transaction management and scans the persistence adapter for
 * repositories. Entities are auto-detected across the app package tree.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.scmplatform.logistics.adapter.outbound.persistence")
@EnableTransactionManagement
public class JpaConfig {
}
