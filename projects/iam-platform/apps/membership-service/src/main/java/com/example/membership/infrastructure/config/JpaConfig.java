package com.example.membership.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for membership-service's own persistence package.
 * Required because java-messaging's {@code OutboxJpaConfig} declares its own
 * {@code @EnableJpaRepositories}, which suppresses Spring Boot's default
 * JPA repository auto-scanning. See TASK-BE-047.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.membership.infrastructure.persistence")
// membership-service의 일부 도메인 클래스(예: MembershipPlan)는 도메인 패키지에
// @Entity를 선언하므로 entity 스캔 대상은 두 패키지를 모두 포함해야 한다.
@EntityScan(basePackages = {
        "com.example.membership.domain",
        "com.example.membership.infrastructure.persistence"
})
public class JpaConfig {
}
