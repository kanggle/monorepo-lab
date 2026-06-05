package com.wms.outbound.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
 * Shared infrastructure for outbound-service {@code @SpringBootTest} integration
 * tests (TASK-BE-049 onward). Boots Postgres + Kafka + Redis + a per-class
 * WireMock server, then wires the WireMock URL into {@code outbound.tms.base-url}
 * so the real {@link com.wms.outbound.adapter.out.tms.TmsClientAdapter} talks to
 * the fake TMS instance.
 *
 * <p>Tagged {@code integration} so it runs only via the {@code integrationTest}
 * Gradle task. Mirrors the inventory / inbound bases — same Postgres / Kafka /
 * Redis topology + the additional TMS-dedicated WireMock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration")
@ContextConfiguration(initializers = OutboundServiceIntegrationBase.Initializer.class)
@ExtendWith(org.testcontainers.junit.jupiter.TestcontainersExtension.class)
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
public abstract class OutboundServiceIntegrationBase {

    protected static final Network NETWORK = Network.newNetwork();

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("postgres")
                    .withDatabaseName("outbound_it")
                    .withUsername("outbound_it")
                    .withPassword("outbound_it");

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

    /**
     * Class-level WireMock — the TMS sandbox endpoint. Started before the
     * Spring context so the {@link Initializer} can inject its URL.
     */
    protected static final WireMockServer WIREMOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIREMOCK.start();
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
    }

    @AfterAll
    static void resetWireMock() {
        WIREMOCK.resetAll();
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
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json",
                    "wms.oauth2.allowed-issuers=http://localhost:8081,iam",
                    "wms.oauth2.required-tenant-id=wms",
                    // TASK-BE-049: point the TMS adapter at WireMock.
                    "outbound.tms.base-url=" + WIREMOCK.baseUrl() + "/tms",
                    "outbound.tms.api-key=test-api-key",
                    "outbound.tms.connect-timeout-ms=2000",
                    "outbound.tms.read-timeout-ms=2000",
                    "outbound.tms.max-connections=10",
                    // Fast retry for tests — production keeps 1s/2s/4s.
                    "resilience4j.retry.instances.tms-client.waitDuration=50ms",
                    "resilience4j.retry.instances.tms-client.exponentialBackoffMultiplier=2",
                    "resilience4j.retry.instances.tms-client.randomizedWaitFactor=0",
                    // Lower minimumNumberOfCalls so circuit can open quickly in tests.
                    "resilience4j.circuitbreaker.instances.tms-client.minimumNumberOfCalls=2",
                    "resilience4j.circuitbreaker.instances.tms-client.slidingWindowSize=4",
                    "resilience4j.circuitbreaker.instances.tms-client.waitDurationInOpenState=2s"
            ).applyTo(context.getEnvironment());
        }
    }
}
