package com.wms.master.integration;

import com.redis.testcontainers.RedisContainer;
import com.wms.master.adapter.out.messaging.OutboxMetrics;
import com.wms.master.integration.support.JwtTestHelper;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Shared test infrastructure for {@link SpringBootTest} integration tests.
 * Boots Postgres + Kafka + Redis on a single Docker network, then wires the
 * application context to them via an {@link ApplicationContextInitializer}
 * (chosen over {@code @DynamicPropertySource} so we can also wire the JWKS
 * URI from the {@link JwtTestHelper} lifecycle).
 *
 * <p>Declared with {@code @Testcontainers(disabledWithoutDocker = true)} —
 * runs on Linux/CI, skipped cleanly on Windows-with-Docker-Desktop-4.x where
 * Testcontainers auto-detection fails.
 *
 * <p>{@code @Tag("integration")} excludes these from the {@code unitTest}
 * gradle task so the fast feedback loop stays Docker-free.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@ContextConfiguration(initializers = MasterServiceIntegrationBase.Initializer.class)
@ExtendWith(org.testcontainers.junit.jupiter.TestcontainersExtension.class)
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
public abstract class MasterServiceIntegrationBase {

    protected static final Network NETWORK = Network.newNetwork();

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("postgres")
                    .withDatabaseName("master_it")
                    .withUsername("master_it")
                    .withPassword("master_it");

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

    protected static final JwtTestHelper JWT = startJwt();

    // Holds a direct strong reference to the OutboxMetrics bean so that the
    // Gauge's StrongReferenceGaugeFunction is redundantly anchored here too,
    // and the bean is guaranteed to be initialised before any test method runs.
    @Autowired
    @SuppressWarnings("unused")
    private OutboxMetrics outboxMetrics;

    static {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
    }

    /**
     * Wires container endpoints into the application context, plus the
     * JWKS URI from the per-JVM {@link JwtTestHelper}. Preferred over
     * {@code @DynamicPropertySource} because the helper itself is not a
     * Testcontainers container.
     */
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
                    "spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                    "spring.data.redis.host=" + REDIS.getHost(),
                    "spring.data.redis.port=" + REDIS.getFirstMappedPort(),
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + JWT.jwkSetUri(),
                    // Speed up the polling scheduler in tests
                    "outbox.polling.interval-ms=300",
                    "outbox.polling.batch-size=100",
                    // Keep idempotency TTL short in tests to avoid cross-test
                    // pollution if the same key is reused (should not happen
                    // but defensive)
                    "master.idempotency.ttl-seconds=120"
            ).applyTo(context.getEnvironment());
        }
    }

    private static JwtTestHelper startJwt() {
        try {
            return JwtTestHelper.start();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start JwtTestHelper", e);
        }
    }
}
