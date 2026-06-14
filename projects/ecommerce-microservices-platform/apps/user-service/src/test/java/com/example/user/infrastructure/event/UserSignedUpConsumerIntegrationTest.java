package com.example.user.infrastructure.event;

import com.example.user.domain.model.ProfileStatus;
import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Tag("integration")
@Testcontainers
@DisplayName("UserSignedUp 이벤트 소비 통합 테스트")
class UserSignedUpConsumerIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db")
            .withUsername("user_user")
            .withPassword("user_pass");

    @SuppressWarnings("resource")
    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("UserSignedUp 이벤트를 소비하면 UserProfile이 생성된다")
    void consumeUserSignedUp_createsProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "kafka-test@example.com";
        String name = "카프카테스트";

        UserSignedUpEvent event = new UserSignedUpEvent(
                UUID.randomUUID(),
                "UserSignedUp",
                Instant.now(),
                "auth-service",
                "ecommerce",
                new UserSignedUpEvent.Payload(userId, email, name)
        );

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send("auth.user.signed-up", userId.toString(), json);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<UserProfile> profile = userProfileRepository.findByUserId(userId);
            assertThat(profile).isPresent();
            assertThat(profile.get().getEmail().value()).isEqualTo(email);
            assertThat(profile.get().getName()).isEqualTo(name);
            assertThat(profile.get().getStatus()).isEqualTo(ProfileStatus.ACTIVE);
        });
    }

    @Test
    @DisplayName("중복 UserSignedUp 이벤트를 소비해도 프로필이 1개만 존재한다 (멱등성)")
    void consumeDuplicateUserSignedUp_idempotent() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "dup-test@example.com";
        String name = "중복테스트";

        UserSignedUpEvent event = new UserSignedUpEvent(
                UUID.randomUUID(),
                "UserSignedUp",
                Instant.now(),
                "auth-service",
                "ecommerce",
                new UserSignedUpEvent.Payload(userId, email, name)
        );

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send("auth.user.signed-up", userId.toString(), json);
        kafkaTemplate.send("auth.user.signed-up", userId.toString(), json);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<UserProfile> profile = userProfileRepository.findByUserId(userId);
            assertThat(profile).isPresent();
        });

        Thread.sleep(2000);
        assertThat(userProfileRepository.existsByUserId(userId)).isTrue();
    }
}
