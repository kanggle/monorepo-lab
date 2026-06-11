package com.example.fanplatform.notification.integration;

import com.example.fanplatform.notification.testsupport.JwksMockServer;
import com.example.fanplatform.notification.testsupport.JwtTestHelper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared base for notification-service integration tests. Spins up Postgres +
 * Kafka Testcontainers + a WireMock JWKS server. NO Redis (no cache case).
 *
 * <p>Containers + JWKS are started in a static initializer (NOT @BeforeAll) so
 * they are running BEFORE the Spring context loads — {@code @DynamicPropertySource}
 * is evaluated during context refresh, which happens before {@code @BeforeAll}, so
 * a {@code @BeforeAll} start would be too late ("Mapped port can only be obtained
 * after the container is started"). Mirrors the proven CI-green membership /
 * erp read-model base. {@code @Testcontainers(disabledWithoutDocker = true)} still
 * skips cleanly on a Docker-less host.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
public abstract class NotificationServiceIntegrationBase {

    protected static final String TOPIC_ACTIVATED = "fan.membership.activated.v1";
    protected static final String TOPIC_CANCELED = "fan.membership.canceled.v1";
    protected static final String TOPIC_EXPIRED = "fan.membership.expired.v1";

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("fanplatform_notification")
            .withUsername("test")
            .withPassword("test");

    protected static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    protected static final JwtTestHelper jwt;
    protected static final JwksMockServer jwks;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected KafkaListenerEndpointRegistry listenerRegistry;

    private KafkaTemplate<String, String> producer;

    static {
        POSTGRES.start();
        KAFKA.start();
        try {
            jwt = new JwtTestHelper();
            jwks = new JwksMockServer(jwt);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void wireProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", jwks::hostJwksUrl);
        registry.add("fanplatform.oauth2.allowed-issuers",
                () -> JwtTestHelper.SAS_ISSUER + "," + JwtTestHelper.LEGACY_ISSUER);
        registry.add("fanplatform.oauth2.required-tenant-id", () -> "fan-platform");
    }

    /**
     * TRUNCATE via JDBC (auto-commits on its own connection — no JPA transaction
     * required) so the shared singleton containers do not leak state across test
     * classes.
     */
    protected void truncateAll() {
        jdbcTemplate.execute("TRUNCATE TABLE notifications, processed_events RESTART IDENTITY CASCADE");
    }

    protected synchronized KafkaTemplate<String, String> producer() {
        if (producer == null) {
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.ACKS_CONFIG, "all");
            producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }
        return producer;
    }

    /** Wait for the listener containers to be assigned partitions before producing. */
    protected void awaitListenersAssigned() {
        for (MessageListenerContainer c : listenerRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(c, 1);
        }
    }

    // ----- envelope builders (canonical fan-membership envelope) ---------------

    protected static String activatedEnvelope(String eventId, String membershipId,
                                              String accountId, String tier) {
        return ("""
                {"eventId":"%s","eventType":"fan.membership.activated",
                 "source":"fan-platform-membership-service","occurredAt":"2026-06-11T00:00:00Z",
                 "schemaVersion":1,"partitionKey":"%s",
                 "payload":{"membershipId":"%s","tenantId":"fan-platform","accountId":"%s",
                   "tier":"%s","planMonths":1,"validFrom":"2026-06-11T00:00:00Z",
                   "validTo":"2026-07-11T00:00:00Z","occurredAt":"2026-06-11T00:00:00Z"}}
                """).formatted(eventId, membershipId, membershipId, accountId, tier);
    }

    protected static String canceledEnvelope(String eventId, String membershipId,
                                             String accountId, String tier) {
        return ("""
                {"eventId":"%s","eventType":"fan.membership.canceled",
                 "source":"fan-platform-membership-service","occurredAt":"2026-06-11T12:00:00Z",
                 "schemaVersion":1,"partitionKey":"%s",
                 "payload":{"membershipId":"%s","tenantId":"fan-platform","accountId":"%s",
                   "tier":"%s","reason":"user requested","canceledAt":"2026-06-11T12:00:00Z",
                   "occurredAt":"2026-06-11T12:00:00Z"}}
                """).formatted(eventId, membershipId, membershipId, accountId, tier);
    }

    protected static String expiredEnvelope(String eventId, String membershipId,
                                            String accountId, String tier) {
        return ("""
                {"eventId":"%s","eventType":"fan.membership.expired",
                 "source":"fan-platform-membership-service","occurredAt":"2026-07-11T00:00:01Z",
                 "schemaVersion":1,"partitionKey":"%s",
                 "payload":{"membershipId":"%s","tenantId":"fan-platform","accountId":"%s",
                   "tier":"%s","validTo":"2026-07-11T00:00:00Z","occurredAt":"2026-07-11T00:00:01Z"}}
                """).formatted(eventId, membershipId, membershipId, accountId, tier);
    }
}
