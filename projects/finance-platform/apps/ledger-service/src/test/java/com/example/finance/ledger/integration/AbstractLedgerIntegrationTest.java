package com.example.finance.ledger.integration;

import com.example.finance.ledger.infrastructure.outbox.LedgerOutboxJpaRepository;
import com.example.finance.ledger.infrastructure.persistence.jpa.JournalEntryJpaRepository;
import com.example.finance.ledger.infrastructure.persistence.jpa.JournalLineJpaRepository;
import com.example.finance.ledger.infrastructure.persistence.jpa.LedgerAccountJpaRepository;
import com.example.finance.ledger.infrastructure.persistence.jpa.ProcessedEventJpaRepository;
import com.example.testsupport.integration.DockerAvailableCondition;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Base for ledger-service integration tests. Shared MySQL (H2 forbidden) + real
 * Kafka (KRaft) + MockWebServer JWKS, started once per JVM. Real RS256 JWTs are
 * minted and the matching public JWK served from the MockWebServer so the full
 * security chain (decode validator + filter) runs end-to-end.
 *
 * <p>Local Windows Docker availability is host-dependent (project memory
 * {@code project_testcontainers_docker_desktop_blocker}); the monorepo
 * "Integration (finance-platform, Testcontainers)" CI job runs these on Linux.
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractLedgerIntegrationTest {

    protected static final String ISSUER = "http://test-issuer";
    protected static final String KID = "ledger-test-key";

    protected static final String TOPIC_COMPLETED = "finance.transaction.completed.v1";
    protected static final String TOPIC_REVERSED = "finance.transaction.reversed.v1";

    /** GL/AP-feed topics emitted by the ledger outbox relay (3rd increment). */
    protected static final String TOPIC_ENTRY_POSTED = "finance.ledger.entry.posted.v1";
    protected static final String TOPIC_PERIOD_CLOSED = "finance.ledger.period.closed.v1";

    /** Reconciliation feed topics emitted by the ledger outbox relay (4th increment). */
    protected static final String TOPIC_RECONCILIATION_COMPLETED =
            "finance.ledger.reconciliation.completed.v1";
    protected static final String TOPIC_DISCREPANCY_DETECTED =
            "finance.ledger.reconciliation.discrepancy.detected.v1";

    @SuppressWarnings("resource")
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("finance_ledger_db")
                    .withUsername("finance")
                    .withPassword("finance")
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
                    new NewTopic(TOPIC_COMPLETED, 1, (short) 1),
                    new NewTopic(TOPIC_REVERSED, 1, (short) 1),
                    new NewTopic(TOPIC_ENTRY_POSTED, 1, (short) 1),
                    new NewTopic(TOPIC_PERIOD_CLOSED, 1, (short) 1),
                    new NewTopic(TOPIC_RECONCILIATION_COMPLETED, 1, (short) 1),
                    new NewTopic(TOPIC_DISCREPANCY_DETECTED, 1, (short) 1)
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
        // (3rd incr) the integration suite exercises the real Kafka outbox→relay→
        // publish round-trip, so the relay scheduler MUST run here.
        registry.add("ledger.outbox.polling.enabled", () -> "true");
        registry.add("ledger.outbox.initial-delay-ms", () -> "500");
        registry.add("ledger.outbox.polling-interval-ms", () -> "300");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> JWKS.url("/oauth2/jwks").toString());
        registry.add("financeplatform.oauth2.allowed-issuers", () -> ISSUER);
    }

    @LocalServerPort
    protected int port;

    @Autowired protected LedgerAccountJpaRepository ledgerAccountJpa;
    @Autowired protected JournalEntryJpaRepository journalEntryJpa;
    @Autowired protected JournalLineJpaRepository journalLineJpa;
    @Autowired protected ProcessedEventJpaRepository processedEventJpa;
    @Autowired protected LedgerOutboxJpaRepository ledgerOutboxJpa;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected ObjectMapper objectMapper;

    /**
     * Cross-class test isolation. The MySQL container is static (shared by every
     * ledger IT class in the JVM). A test that closes an accounting period covering
     * "now" leaves it in {@code accounting_period}, where it would poison a sibling
     * class — postings get rejected with {@code LEDGER_PERIOD_CLOSED}, and a later
     * {@code open} of an overlapping window 422s.
     *
     * <p>The 4th-increment reconciliation matcher fetches "the unmatched internal
     * lines on {@code CASH_CLEARING}", so leftover {@code journal_line} /
     * {@code reconciliation_match} rows from a sibling class would change the
     * discrepancy count it asserts. So this cleanup now wipes ALL transactional
     * tables (NOT {@code ledger_account} — the seeded chart of accounts; wallet
     * codes are re-created lazily) so every test starts from a deterministic ledger.
     * This is safe for the existing IT classes — each posts its own data within its
     * method, after this {@code @BeforeEach}.
     *
     * <p>FK-safe order: child rows before parents — reconciliation match/line/
     * discrepancy/statement, then journal lines before entries, then the standalone
     * audit / dedupe / period-snapshot / period / outbox tables.
     */
    @BeforeEach
    void cleanLedgerState() {
        jdbcTemplate.execute("DELETE FROM reconciliation_match");
        jdbcTemplate.execute("DELETE FROM reconciliation_statement_line");
        jdbcTemplate.execute("DELETE FROM reconciliation_discrepancy");
        jdbcTemplate.execute("DELETE FROM reconciliation_statement");
        jdbcTemplate.execute("DELETE FROM journal_line");
        jdbcTemplate.execute("DELETE FROM journal_entry");
        jdbcTemplate.execute("DELETE FROM audit_log");
        jdbcTemplate.execute("DELETE FROM processed_events");
        jdbcTemplate.execute("DELETE FROM period_balance_snapshot");
        jdbcTemplate.execute("DELETE FROM accounting_period");
        jdbcTemplate.execute("DELETE FROM ledger_outbox");
    }

    // ------------------------------------------------------------------------
    // JWT minting
    // ------------------------------------------------------------------------

    /** Mints an RS256 token with tenant_id=finance + finance.read scope (happy path). */
    protected String financeReadToken() {
        return token(c -> c.claim("tenant_id", "finance").claim("scope", "finance.read"));
    }

    /** Mints a cross-tenant token (tenant_id != finance, no entitled_domains). */
    protected String crossTenantToken() {
        return token(c -> c.claim("tenant_id", "wms").claim("scope", "finance.read"));
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

    // ------------------------------------------------------------------------
    // Kafka consume helpers (GL/AP-feed round-trip)
    // ------------------------------------------------------------------------

    /**
     * Drain a topic from the beginning (fresh group + earliest) and return the
     * first record whose parsed envelope matches {@code matcher}, polling up to
     * {@code timeout}. Returns the parsed envelope JSON, or fails if none matches.
     */
    protected JsonNode awaitEnvelope(String topic,
                                     java.util.function.Predicate<JsonNode> matcher,
                                     Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        long deadline = System.currentTimeMillis() + timeout.toMillis();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    JsonNode env;
                    try {
                        env = objectMapper.readTree(record.value());
                    } catch (Exception e) {
                        continue;
                    }
                    if (matcher.test(env)) {
                        return env;
                    }
                }
            }
        }
        throw new AssertionError("No matching record on " + topic + " within " + timeout);
    }

    /** Builds a finance.transaction.completed.v1 envelope (BaseEventPublisher shape). */
    protected String completedEnvelope(String eventId, String transactionId, String accountId,
                                       String type, long amountMinor, String currency,
                                       String counterpartyAccountId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transactionId);
        payload.put("accountId", accountId);
        payload.put("type", type);
        payload.put("money", money(amountMinor, currency));
        if (counterpartyAccountId != null) {
            payload.put("counterpartyAccountId", counterpartyAccountId);
        }
        payload.put("status", "COMPLETED");
        return envelope(eventId, "finance.transaction.completed", "transaction",
                transactionId, payload);
    }

    /** Builds a finance.transaction.reversed.v1 envelope. */
    protected String reversedEnvelope(String eventId, String transactionId,
                                      String reversalOfTransactionId, String accountId,
                                      long amountMinor, String currency) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transactionId);
        payload.put("reversalOfTransactionId", reversalOfTransactionId);
        payload.put("accountId", accountId);
        payload.put("money", money(amountMinor, currency));
        return envelope(eventId, "finance.transaction.reversed", "transaction",
                transactionId, payload);
    }

    private Map<String, Object> money(long amountMinor, String currency) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", Long.toString(amountMinor));
        m.put("currency", currency);
        return m;
    }

    private String envelope(String eventId, String eventType, String aggregateType,
                            String aggregateId, Map<String, Object> payload) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", eventId);
        env.put("eventType", eventType);
        env.put("occurredAt", Instant.now().toString());
        env.put("tenantId", "finance");
        env.put("source", "finance-platform-account-service");
        env.put("aggregateType", aggregateType);
        env.put("aggregateId", aggregateId);
        env.put("payload", payload);
        try {
            return objectMapper.writeValueAsString(env);
        } catch (Exception e) {
            throw new IllegalStateException("envelope serialise failed", e);
        }
    }

    protected static String newId() {
        return UUID.randomUUID().toString();
    }
}
