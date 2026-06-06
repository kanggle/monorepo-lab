package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.access.ContentAccessPolicy;
import com.example.membership.domain.plan.PlanLevel;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("ContentAccessPolicyJpaRepository#findByVisibilityKey")
class ContentAccessPolicyJpaRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("membership_db")
            .withUsername("test")
            .withPassword("test")
            .withCommand("mysqld", "--log-bin-trust-function-creators=1")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private ContentAccessPolicyJpaRepository repo;

    @Test
    @DisplayName("MEMBERS_ONLY 키 조회 — requiredPlanLevel=FAN_CLUB (Flyway 시드 데이터)")
    void findByVisibilityKey_membersOnly_returnsFanClubPolicy() {
        Optional<ContentAccessPolicy> result = repo.findByVisibilityKey("MEMBERS_ONLY");

        assertThat(result).isPresent();
        ContentAccessPolicy policy = result.get();
        assertThat(policy.getVisibilityKey()).isEqualTo("MEMBERS_ONLY");
        assertThat(policy.getRequiredPlanLevel()).isEqualTo(PlanLevel.FAN_CLUB);
    }

    @Test
    @DisplayName("존재하지 않는 키 조회 → empty")
    void findByVisibilityKey_unknown_returnsEmpty() {
        Optional<ContentAccessPolicy> result = repo.findByVisibilityKey("non-existent-key");

        assertThat(result).isEmpty();
    }
}
