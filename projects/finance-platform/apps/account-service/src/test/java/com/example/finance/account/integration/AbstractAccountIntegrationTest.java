package com.example.finance.account.integration;

import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Base class for account-service integration tests.
 *
 * <p>Shared <b>MySQL</b> (production-parity engine — H2 is FORBIDDEN) + Kafka
 * (libs/java-messaging outbox relay) + Redis (idempotency primary) containers,
 * started once per JVM. JWKS is stubbed per-class with an OkHttp
 * {@code MockWebServer} in the subclasses that exercise the HTTP/JWT layer.
 *
 * <p>Most ITs drive the application layer directly (OAuth2 JWT validation is
 * bypassed at that level); the cross-tenant IT goes through the HTTP layer
 * with a signed token to assert the 403 fail-closed path.
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractAccountIntegrationTest {

    protected static final String TENANT_FINANCE = "finance";

    @SuppressWarnings("resource")
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("finance_db")
                    .withUsername("finance")
                    .withPassword("finance")
                    .withStartupTimeout(Duration.ofMinutes(3));

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    @SuppressWarnings("resource")
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        MYSQL.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        // No JWKS discovery — application-layer ITs bypass the resource server.
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:9/oauth2/jwks");
        registry.add("financeplatform.oauth2.allowed-issuers", () -> "http://test-issuer");
        // Deterministic sanction list for the F4 IT.
        registry.add("financeplatform.account.compliance.sanctioned-owner-refs",
                () -> "SANCTIONED-OWNER");
    }

    @Autowired(required = false)
    protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
}
