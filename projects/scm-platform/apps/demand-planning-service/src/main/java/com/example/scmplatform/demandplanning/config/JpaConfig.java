package com.example.scmplatform.demandplanning.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA configuration — enables transaction management.
 * Entity scan: Spring Boot auto-detects entities in the package tree.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa")
@EnableTransactionManagement
public class JpaConfig {
}
