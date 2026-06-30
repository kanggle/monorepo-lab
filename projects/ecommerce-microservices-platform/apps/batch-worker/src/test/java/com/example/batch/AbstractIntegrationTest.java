package com.example.batch;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers base for batch-worker integration tests.
 *
 * <p><b>Singleton-container pattern (TASK-MONO-319).</b> The containers are started once in
 * a static initializer and deliberately never stopped — NOT via {@code @Testcontainers} /
 * {@code @Container} managed lifecycle. Several IT classes extend this base; under the
 * managed lifecycle the shared static container is torn down after the first subclass while
 * Spring keeps the cached ApplicationContext (built from this identical config) alive, so the
 * next subclass reuses a context whose datasource points at a now-dead container →
 * "Failed to obtain JDBC Connection" / "Could not open JPA EntityManager". The singleton
 * pattern keeps one live Postgres + Kafka for the whole JVM; Ryuk reaps them at exit.
 */
@SpringBootTest
@Tag("integration")
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("batch_db")
            .withUsername("batch_user")
            .withPassword("batch_pass");

    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    static {
        postgres.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
