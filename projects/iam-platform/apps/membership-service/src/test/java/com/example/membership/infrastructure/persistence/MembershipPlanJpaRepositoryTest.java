package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.plan.MembershipPlan;
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
@DisplayName("MembershipPlanJpaRepository#findByPlanLevel")
class MembershipPlanJpaRepositoryTest {

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
    private MembershipPlanJpaRepository repo;

    @Test
    @DisplayName("FAN_CLUB 레벨 조회 — priceKrw=9900, durationDays=30, active=true")
    void findByPlanLevel_fanClub_returnsCorrectPlan() {
        Optional<MembershipPlan> result = repo.findByPlanLevel(PlanLevel.FAN_CLUB);

        assertThat(result).isPresent();
        MembershipPlan plan = result.get();
        assertThat(plan.getPlanLevel()).isEqualTo(PlanLevel.FAN_CLUB);
        assertThat(plan.getPriceKrw()).isEqualTo(9900);
        assertThat(plan.getDurationDays()).isEqualTo(30);
        assertThat(plan.isActive()).isTrue();
    }

    @Test
    @DisplayName("FREE 레벨 조회 — priceKrw=0, durationDays=0")
    void findByPlanLevel_free_returnsCorrectPlan() {
        Optional<MembershipPlan> result = repo.findByPlanLevel(PlanLevel.FREE);

        assertThat(result).isPresent();
        MembershipPlan plan = result.get();
        assertThat(plan.getPlanLevel()).isEqualTo(PlanLevel.FREE);
        assertThat(plan.getPriceKrw()).isEqualTo(0);
        assertThat(plan.getDurationDays()).isEqualTo(0);
    }
}
