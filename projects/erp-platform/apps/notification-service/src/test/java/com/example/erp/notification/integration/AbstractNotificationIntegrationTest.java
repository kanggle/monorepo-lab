package com.example.erp.notification.integration;

import com.example.erp.notification.infrastructure.persistence.jpa.NotificationDeliveryJpaRepository;
import com.example.erp.notification.infrastructure.persistence.jpa.NotificationJpaRepository;
import com.example.erp.notification.infrastructure.persistence.jpa.ProcessedEventJpaRepository;
import com.example.testsupport.integration.DockerAvailableCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Base for notification-service integration tests. Shared MySQL (H2 forbidden) +
 * Kafka KRaft + MockWebServer JWKS, started once per JVM. Real RS256 JWTs are
 * minted and the matching public JWK served so the full security chain runs
 * end-to-end.
 *
 * <p>Local Windows Docker availability is host-dependent (honest gap, project
 * memory {@code project_testcontainers_docker_desktop_blocker}); the monorepo
 * "Integration (erp-platform, Testcontainers)" CI job runs these on Linux.
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractNotificationIntegrationTest {

    protected static final String ISSUER = "http://test-issuer";
    protected static final String KID = "ntf-test-key";

    protected static final String TOPIC_SUBMITTED = "erp.approval.submitted.v1";
    protected static final String TOPIC_APPROVED = "erp.approval.approved.v1";
    protected static final String TOPIC_REJECTED = "erp.approval.rejected.v1";
    protected static final String TOPIC_WITHDRAWN = "erp.approval.withdrawn.v1";

    @SuppressWarnings("resource")
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("erp_db")
                    .withUsername("erp")
                    .withPassword("erp")
                    .withStartupTimeout(Duration.ofMinutes(3));

    protected static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withStartupTimeout(Duration.ofMinutes(3));

    @SuppressWarnings("resource")
    protected static final MockWebServer JWKS = new MockWebServer();

    private static final RSAKey RSA_KEY = generateRsaKey();

    static {
        MYSQL.start();
        KAFKA.start();
        preCreateTopics();
        String jwksBody = "{\"keys\":[" + RSA_KEY.toPublicJWK().toJSONString() + "]}";
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

    private static RSAKey generateRsaKey() {
        try {
            return new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048)
                    .keyID(KID)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
        } catch (Exception e) {
            throw new IllegalStateException("RSA key generation failed", e);
        }
    }

    private static void preCreateTopics() {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(List.of(
                    new NewTopic(TOPIC_SUBMITTED, 1, (short) 1),
                    new NewTopic(TOPIC_APPROVED, 1, (short) 1),
                    new NewTopic(TOPIC_REJECTED, 1, (short) 1),
                    new NewTopic(TOPIC_WITHDRAWN, 1, (short) 1)
            )).all().get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() != null
                    && e.getCause().getClass().getSimpleName().equals("TopicExistsException")) {
                return;
            }
            throw new IllegalStateException("Failed to pre-create Kafka topics", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to pre-create Kafka topics", e);
        }
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.listener.auto-startup", () -> "true");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> JWKS.url("/oauth2/jwks").toString());
        registry.add("erpplatform.oauth2.allowed-issuers", () -> ISSUER);
    }

    @LocalServerPort
    protected int port;

    @Autowired protected NotificationJpaRepository notificationJpa;
    @Autowired protected NotificationDeliveryJpaRepository deliveryJpa;
    @Autowired protected ProcessedEventJpaRepository processedEventJpa;
    @Autowired protected ObjectMapper objectMapper;

    // ------------------------------------------------------------------------
    // JWT minting
    // ------------------------------------------------------------------------

    /** Token for a given recipient (sub) with tenant_id=erp + erp.read scope. */
    protected String erpTokenForRecipient(String sub) {
        return token(c -> c.subject(sub).claim("tenant_id", "erp").claim("scope", "erp.read"));
    }

    protected String token(java.util.function.Consumer<JWTClaimsSet.Builder> customizer) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("user-1")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(600)));
        customizer.accept(builder);
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                    builder.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) RSA_KEY.toPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("JWT signing failed", e);
        }
    }

    // ------------------------------------------------------------------------
    // Kafka publish helpers
    // ------------------------------------------------------------------------

    protected void publish(String topic, String key, String value) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get(15, TimeUnit.SECONDS);
            producer.flush();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish to " + topic, e);
        }
    }

    /** Builds an approval event envelope (erp-approval-events.md § Envelope). */
    protected String approvalEnvelope(String eventId, String eventType, String approvalRequestId,
                                      String approverId, String submitterId,
                                      String finalizedAt, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approvalRequestId", approvalRequestId);
        payload.put("subjectType", "DEPARTMENT");
        payload.put("subjectId", "dept-1");
        payload.put("approverId", approverId);
        payload.put("submitterId", submitterId);
        payload.put("tenantId", "erp");
        payload.put("occurredAt", Instant.now().toString());
        payload.put("actor", submitterId);
        if (finalizedAt != null) payload.put("finalizedAt", finalizedAt);
        if (reason != null) payload.put("reason", reason);
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", eventId);
        env.put("eventType", eventType);
        env.put("occurredAt", Instant.now().toString());
        env.put("tenantId", "erp");
        env.put("source", "erp-platform-approval-service");
        env.put("aggregateType", "ApprovalRequest");
        env.put("aggregateId", approvalRequestId);
        env.put("payload", payload);
        try {
            return objectMapper.writeValueAsString(env);
        } catch (Exception e) {
            throw new IllegalStateException("approval envelope serialise failed", e);
        }
    }

    protected static String newId() {
        return UUID.randomUUID().toString();
    }
}
