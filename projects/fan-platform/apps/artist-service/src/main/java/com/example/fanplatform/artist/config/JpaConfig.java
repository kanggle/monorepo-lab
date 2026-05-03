package com.example.fanplatform.artist.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for artist-service's persistence package.
 * Required because {@code java-messaging}'s {@code OutboxJpaConfig} declares
 * its own {@code @EnableJpaRepositories}, which suppresses Spring Boot's
 * default JPA repository auto-scanning.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.fanplatform.artist.adapter.out.persistence")
@EntityScan(basePackages = "com.example.fanplatform.artist.adapter.out.persistence")
public class JpaConfig {
}
