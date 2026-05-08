package com.wms.admin.integration.kafka;

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
 * Shared base for the 4 admin-service projection-consumer Kafka integration
 * tests (TASK-BE-047 deviation #1 closure).
 *
 * <p>Boots Postgres + Kafka on a single Docker network (mirrors
 * {@code NotificationServiceIntegrationBase} from TASK-BE-043). Each
 * subclass gets its own Spring context (no {@code @DirtiesContext} —
 * {@code application-test.yml}'s {@code admin-projection-it-${random.uuid}}
 * consumer group prevents cross-class offset leak per TASK-MONO-046-3
 * learning).
 *
 * <p>Notably we run with the standard {@code admin-service} configuration
 * (no {@code standalone} profile), so {@code @KafkaListener} containers,
 * {@code DefaultErrorHandler}, and the production
 * {@code AdminEventDedupePersistenceAdapter} are all live.
 */
@SpringBootTest(classes = com.wms.admin.AdminServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles({"test"})
@ContextConfiguration(initializers = ProjectionKafkaIntegrationBase.Initializer.class)
@ExtendWith(org.testcontainers.junit.jupiter.TestcontainersExtension.class)
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
public abstract class ProjectionKafkaIntegrationBase {

    protected static final Network NETWORK = Network.newNetwork();

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("postgres")
                    .withDatabaseName("admin_it")
                    .withUsername("admin_it")
                    .withPassword("admin_it");

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
                    "spring.flyway.enabled=true",
                    "spring.flyway.locations=classpath:db/migration",
                    "spring.jpa.hibernate.ddl-auto=validate",
                    "spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                    // Redis is required because the production AdminServiceConfig
                    // wires `redisIdempotencyStore` outside the standalone profile
                    // — see {@code @Profile("!standalone")} on the bean. Standalone
                    // profile would disable the projection consumers we need to
                    // exercise here (`@Profile("!standalone")` on
                    // {@code ProjectionKafkaConsumerConfig}), so we run real Redis
                    // for the IT.
                    "spring.data.redis.host=" + REDIS.getHost(),
                    "spring.data.redis.port=" + REDIS.getFirstMappedPort()
            ).applyTo(context.getEnvironment());
        }
    }
}
