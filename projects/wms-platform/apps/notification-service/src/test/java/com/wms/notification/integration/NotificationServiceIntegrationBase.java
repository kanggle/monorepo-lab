package com.wms.notification.integration;

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
 * Shared base for {@link SpringBootTest} integration tests in
 * notification-service. Boots Postgres + Kafka on a single Docker network.
 *
 * <p>Mirrors {@code InventoryServiceIntegrationBase} (TASK-BE-007 lineage).
 * Adds: per-class consumer group via the {@code application-test.yml}
 * {@code ${random.uuid}} interpolation (TASK-MONO-046-3 SCM/security
 * learning) so context refresh between classes doesn't leak offsets.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"integration", "test"})
@ContextConfiguration(initializers = NotificationServiceIntegrationBase.Initializer.class)
@ExtendWith(org.testcontainers.junit.jupiter.TestcontainersExtension.class)
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
public abstract class NotificationServiceIntegrationBase {

    protected static final Network NETWORK = Network.newNetwork();

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("postgres")
                    .withDatabaseName("notification_it")
                    .withUsername("notification_it")
                    .withPassword("notification_it");

    @SuppressWarnings("resource")
    protected static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("kafka")
                    .withStartupTimeout(Duration.ofMinutes(2));

    static {
        POSTGRES.start();
        KAFKA.start();
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
                    // Slack webhook intentionally unset → adapter throws
                    // ChannelNotConfiguredException → delivery FAILED — exactly the
                    // edge case #1 fail-closed behaviour we want to exercise.
                    "wms.notification.channels.slack.wms-alerts.webhook-url=",
                    "wms.notification.channels.slack.wms-shipping.webhook-url="
            ).applyTo(context.getEnvironment());
        }
    }
}
