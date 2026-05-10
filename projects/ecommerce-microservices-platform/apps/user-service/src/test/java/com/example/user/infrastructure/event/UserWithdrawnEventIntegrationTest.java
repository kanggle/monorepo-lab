package com.example.user.infrastructure.event;

import com.example.user.application.service.UserProfileService;
import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the canonical {@code user.user.withdrawn} topic name (TASK-BE-134).
 *
 * <p>Prior to TASK-BE-134 the publisher emitted to {@code user.user-withdrawn}
 * (hyphen separator) while every consumer subscribed to {@code user.user.withdrawn}
 * (dot) — events were silently lost in production. This test pins the canonical
 * dot-separated name so a regression cannot reintroduce the silent loss.
 */
@SpringBootTest
@Tag("integration")
@Testcontainers
@DisplayName("UserWithdrawn 이벤트 발행 통합 테스트 (BE-134 회귀 가드)")
class UserWithdrawnEventIntegrationTest {

    private static final String CANONICAL_TOPIC = "user.user.withdrawn";

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db")
            .withUsername("user_user")
            .withPassword("user_pass");

    @SuppressWarnings("resource")
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("withdrawProfile 호출 시 UserWithdrawn 이벤트가 user.user.withdrawn 토픽에 발행된다")
    void withdrawProfile_publishesUserWithdrawnEventOnCanonicalTopic() throws Exception {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "withdrawn-test@example.com", "탈퇴테스트");
        userProfileRepository.save(profile);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()
        ))) {
            consumer.subscribe(List.of(CANONICAL_TOPIC));

            userProfileService.withdrawProfile(userId);

            boolean found = false;
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.key().equals(userId.toString())) {
                        var event = objectMapper.readTree(record.value());
                        assertThat(event.get("event_type").asText()).isEqualTo("UserWithdrawn");
                        assertThat(event.get("source").asText()).isEqualTo("user-service");
                        assertThat(event.get("payload").get("userId").asText()).isEqualTo(userId.toString());
                        assertThat(event.get("payload").get("withdrawnAt")).isNotNull();
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
            assertThat(found)
                    .as("UserWithdrawn event must reach the canonical %s topic (regression guard for BE-134 silent-loss bug)", CANONICAL_TOPIC)
                    .isTrue();
        }
    }
}
