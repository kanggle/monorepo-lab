package com.wms.inventory.integration;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared infrastructure for {@link SpringBootTest} integration tests in
 * inventory-service. Boots Postgres + Kafka + Redis on a single Docker
 * network and wires their endpoints into the Spring context.
 *
 * <p>{@code @Testcontainers(disabledWithoutDocker = true)} so the suite runs
 * cleanly on Linux/CI and is auto-skipped on hosts without Docker (e.g.,
 * Windows-with-Docker-Desktop edge cases).
 *
 * <p>{@code @Tag("integration")} excludes these from the default {@code test}
 * task — they're invoked via the {@code integrationTest} task only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration")
@ContextConfiguration(initializers = InventoryServiceIntegrationBase.Initializer.class)
@ExtendWith(org.testcontainers.junit.jupiter.TestcontainersExtension.class)
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
public abstract class InventoryServiceIntegrationBase {

    protected static final Network NETWORK = Network.newNetwork();

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("postgres")
                    .withDatabaseName("inventory_it")
                    .withUsername("inventory_it")
                    .withPassword("inventory_it");

    @SuppressWarnings("resource")
    protected static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("kafka")
                    .withStartupTimeout(Duration.ofMinutes(2));

    @SuppressWarnings("resource")
    protected static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("redis");

    static {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
    }

    public static class Initializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                    "spring.datasource.username=" + POSTGRES.getUsername(),
                    "spring.datasource.password=" + POSTGRES.getPassword(),
                    "spring.jpa.hibernate.ddl-auto=validate",
                    "spring.flyway.enabled=true",
                    "spring.flyway.locations=classpath:db/migration",
                    "spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                    "spring.data.redis.host=" + REDIS.getHost(),
                    "spring.data.redis.port=" + REDIS.getFirstMappedPort(),
                    // No JWT in bootstrap — REST endpoints come in TASK-BE-022
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
            ).applyTo(context.getEnvironment());
        }
    }
}
