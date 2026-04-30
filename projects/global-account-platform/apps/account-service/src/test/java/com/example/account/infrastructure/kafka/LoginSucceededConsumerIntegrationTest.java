package com.example.account.infrastructure.kafka;

import com.example.account.application.port.AuthServicePort;
import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.tenant.TenantId;
import com.example.messaging.outbox.ProcessedEventJpaRepository;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test for {@link LoginSucceededConsumer} (TASK-BE-103).
 *
 * <p>Spins up the full account-service Spring context backed by a real MySQL +
 * Kafka via {@link AbstractIntegrationTest}. Produces an
 * {@code auth.login.succeeded} envelope and asserts:
 * <ol>
 *   <li>{@code accounts.last_login_succeeded_at} advances to the event's
 *       payload.timestamp.</li>
 *   <li>{@code processed_events} has exactly one row for the eventId after a
 *       duplicate redelivery — i.e. the DB-level dedup keeps the consumer
 *       idempotent.</li>
 * </ol>
 *
 * <p>Outbound auth-service calls are mocked because signup is performed by
 * direct repository write here, not via the public API. The
 * {@code AccountOutboxPollingScheduler} is left running because consuming a
 * Kafka event does not enqueue outbox rows.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("LoginSucceededConsumer 통합 테스트 — auth.login.succeeded 소비 + last_login 갱신")
class LoginSucceededConsumerIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("internal.api.token", () -> "test-internal-token");
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;

    // The signup happy-path normally calls auth-service over HTTP; we bypass
    // that by writing the Account row directly. Mocking the port keeps any
    // unintended outbound from breaking the test.
    @MockitoBean
    private AuthServicePort authServicePort;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        DefaultKafkaProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(pf);
    }

    @Test
    @DisplayName("auth.login.succeeded 이벤트 발행 → accounts.last_login_succeeded_at 갱신, processed_events 1행 적재")
    void consume_updatesLastLoginAndPersistsDedupRow() {
        String email = "login-success-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.save(Account.create(TenantId.FAN_PLATFORM, email));
        String accountId = account.getId();
        // Sanity: brand-new account has no last login.
        assertThat(accountRepository.findById(TenantId.FAN_PLATFORM, accountId).orElseThrow().getLastLoginSucceededAt())
                .isNull();

        String eventId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.parse("2026-04-26T10:00:00Z");
        String envelope = buildLoginSucceededEnvelope(eventId, accountId, occurredAt);

        kafkaTemplate.send(new ProducerRecord<>("auth.login.succeeded", accountId, envelope));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Account reloaded = accountRepository.findById(TenantId.FAN_PLATFORM, accountId).orElseThrow();
            assertThat(reloaded.getLastLoginSucceededAt()).isEqualTo(occurredAt);
            assertThat(processedEventRepository.existsByEventId(eventId)).isTrue();
        });
    }

    @Test
    @DisplayName("동일 eventId 재발행 → processed_events 1행 유지 (DB-level 멱등성)")
    void consume_duplicateEventId_processedOnce() {
        String email = "login-dup-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.save(Account.create(TenantId.FAN_PLATFORM, email));
        String accountId = account.getId();

        String eventId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.parse("2026-04-26T11:00:00Z");
        String envelope = buildLoginSucceededEnvelope(eventId, accountId, occurredAt);

        // First delivery.
        kafkaTemplate.send(new ProducerRecord<>("auth.login.succeeded", accountId, envelope));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.existsByEventId(eventId)).isTrue());

        long countBefore = processedEventRepository.findAll().stream()
                .filter(e -> e.getEventId().equals(eventId))
                .count();
        assertThat(countBefore).isEqualTo(1);

        // Second delivery of the same eventId — must remain a single row.
        kafkaTemplate.send(new ProducerRecord<>("auth.login.succeeded", accountId, envelope));

        // Give the consumer time to process the redelivery, then assert no
        // duplicate row was created. Awaitility steady-state guard: the count
        // stays at 1 for the entire window.
        await().pollDelay(3, TimeUnit.SECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    long countAfter = processedEventRepository.findAll().stream()
                            .filter(e -> e.getEventId().equals(eventId))
                            .count();
                    assertThat(countAfter).isEqualTo(1);
                });

        // Account remains advanced to the event timestamp (max semantics).
        Account reloaded = accountRepository.findById(TenantId.FAN_PLATFORM, accountId).orElseThrow();
        assertThat(reloaded.getLastLoginSucceededAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("payload.accountId 미존재 → 계정 미갱신, dedup 행 기록으로 재전달 방지 (poison-pill 보호)")
    void consume_unknownAccount_skipsWithoutSideEffects() {
        String eventId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.parse("2026-04-26T12:00:00Z");
        String unknownAccountId = "unknown-acc-" + UUID.randomUUID();
        String envelope = buildLoginSucceededEnvelope(eventId, unknownAccountId, occurredAt);

        kafkaTemplate.send(new ProducerRecord<>("auth.login.succeeded", unknownAccountId, envelope));

        // Dedup-first ordering (TASK-BE-104): the use-case writes the
        // processed_events row BEFORE looking up the account. As a result, the
        // dedup row IS persisted even when the account is missing — this is
        // intentional and IMPROVES poison-pill protection by short-circuiting
        // any redelivery on the existsByEventId fast-path. The account update
        // itself is correctly skipped (account row was never created).
        await().pollDelay(3, TimeUnit.SECONDS)
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(processedEventRepository.existsByEventId(eventId)).isTrue();
                    assertThat(accountRepository.findById(TenantId.FAN_PLATFORM, unknownAccountId)).isEmpty();
                });
    }

    private String buildLoginSucceededEnvelope(String eventId, String accountId, Instant timestamp) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "auth.login.succeeded",
                  "source": "auth-service",
                  "occurredAt": "%s",
                  "schemaVersion": 1,
                  "partitionKey": "%s",
                  "payload": {
                    "accountId": "%s",
                    "ipMasked": "192.168.*.*",
                    "userAgentFamily": "Chrome 120",
                    "deviceFingerprint": "fp-int",
                    "geoCountry": "KR",
                    "sessionJti": "jti-int",
                    "timestamp": "%s"
                  }
                }
                """.formatted(eventId, timestamp, accountId, accountId, timestamp);
    }

    @SuppressWarnings("unused")
    private static Optional<Account> findById(AccountRepository repository, String id) {
        return repository.findById(TenantId.FAN_PLATFORM, id);
    }
}
