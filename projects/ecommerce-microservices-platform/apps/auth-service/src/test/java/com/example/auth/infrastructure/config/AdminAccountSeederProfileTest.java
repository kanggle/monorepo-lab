package com.example.auth.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [TASK-BE-127] prod 프로파일에서 AdminAccountSeeder Bean이 등록되지 않음을 검증.
 *
 * <p>AdminAccountSeeder에 {@code @Profile({"local", "standalone"})}이 선언되어 있으므로
 * prod 프로파일 기동 시 해당 Bean(및 seedAdminAccount ApplicationRunner)이 ApplicationContext에
 * 존재하지 않아야 한다.
 */
@SpringBootTest
@ActiveProfiles("prod")
@Tag("integration")
@Testcontainers
@DisplayName("AdminAccountSeeder — prod 프로파일 격리 테스트")
class AdminAccountSeederProfileTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("auth_db")
        .withUsername("auth_user")
        .withPassword("auth_pass");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("jwt.secret", () -> "prod-profile-test-secret-key-min-32chars!!");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.kafka.producer.properties.max.block.ms", () -> "1000");
        registry.add("spring.autoconfigure.exclude",
            () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
                  "org.springframework.boot.actuate.autoconfigure.metrics.KafkaMetricsAutoConfiguration");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("prod 프로파일에서 AdminAccountSeeder Bean이 등록되지 않는다")
    void adminAccountSeeder_notRegistered_inProdProfile() {
        assertThat(applicationContext.containsBean("adminAccountSeeder"))
            .as("AdminAccountSeeder must NOT be registered in the prod profile")
            .isFalse();
    }

    @Test
    @DisplayName("prod 프로파일에서 seedAdminAccount ApplicationRunner Bean이 등록되지 않는다")
    void seedAdminAccount_runner_notRegistered_inProdProfile() {
        assertThat(applicationContext.containsBean("seedAdminAccount"))
            .as("seedAdminAccount ApplicationRunner must NOT be registered in the prod profile")
            .isFalse();
    }
}
