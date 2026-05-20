package com.example.erp.masterdata.integration;

import com.example.testsupport.integration.DockerAvailableCondition;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Base for masterdata-service integration tests. Shared MySQL (H2 forbidden)
 * + Kafka (outbox relay) containers, started once per JVM. JWKS stubbed via a
 * MockWebServer.
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractMasterdataIntegrationTest {

    protected static final String TENANT_ERP = "erp";

    @SuppressWarnings("resource")
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("erp_db")
                    .withUsername("erp")
                    .withPassword("erp")
                    .withStartupTimeout(Duration.ofMinutes(3));

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withStartupTimeout(Duration.ofMinutes(3));

    @SuppressWarnings("resource")
    protected static final MockWebServer JWKS = new MockWebServer();

    private static volatile String jwksBody = "{\"keys\":[]}";

    protected static void publishJwks(String jwksJson) {
        jwksBody = jwksJson;
    }

    static {
        MYSQL.start();
        KAFKA.start();
        JWKS.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(jwksBody);
            }
        });
        try {
            JWKS.start();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("JWKS MockWebServer start failed", e);
        }
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
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> JWKS.url("/oauth2/jwks").toString());
        registry.add("erpplatform.oauth2.allowed-issuers", () -> "http://test-issuer");
    }

    @Autowired(required = false)
    protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
}
