package com.example.security.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test verifying that login_history table is append-only.
 * UPDATE and DELETE are rejected by MySQL triggers.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class LoginHistoryImmutabilityIntegrationTest extends AbstractIntegrationTest {

    // MySQL + Kafka inherited from AbstractIntegrationTest (TASK-BE-076/078/080).
    // Kafka image version is pinned to cp-kafka:7.6.0 there; no local override.
    // Redis remains service-specific.
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // MySQL + Kafka registered by AbstractIntegrationTest.
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("UPDATE on login_history is rejected by trigger")
    void updateIsRejected() {
        // Insert a test row (event_id must be UUID-shaped, VARCHAR(36))
        jdbcTemplate.update(
                "INSERT INTO login_history (event_id, account_id, outcome, occurred_at) VALUES (?, ?, ?, ?)",
                java.util.UUID.randomUUID().toString(),
                "acc-immutability-001",
                "SUCCESS",
                Instant.now()
        );

        // Attempt UPDATE should be rejected by trigger
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "UPDATE login_history SET outcome = 'FAILURE' WHERE account_id = ?",
                        "acc-immutability-001"
                )
        ).hasMessageContaining("UPDATE not allowed on login_history");
    }

    @Test
    @DisplayName("DELETE on login_history is rejected by trigger")
    void deleteIsRejected() {
        // Insert a test row (event_id must be UUID-shaped, VARCHAR(36))
        jdbcTemplate.update(
                "INSERT INTO login_history (event_id, account_id, outcome, occurred_at) VALUES (?, ?, ?, ?)",
                java.util.UUID.randomUUID().toString(),
                "acc-immutability-002",
                "SUCCESS",
                Instant.now()
        );

        // Attempt DELETE should be rejected by trigger
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "DELETE FROM login_history WHERE account_id = ?",
                        "acc-immutability-002"
                )
        ).hasMessageContaining("DELETE not allowed on login_history");
    }
}
