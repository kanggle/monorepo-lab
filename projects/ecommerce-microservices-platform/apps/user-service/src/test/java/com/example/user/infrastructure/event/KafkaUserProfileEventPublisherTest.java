package com.example.user.infrastructure.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.user.application.event.UserProfileUpdatedSpringEvent;
import com.example.user.infrastructure.metrics.UserMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaUserProfileEventPublisher 단위 테스트")
class KafkaUserProfileEventPublisherTest {

    private KafkaUserProfileEventPublisher publisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private UserMetrics userMetrics;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        publisher = new KafkaUserProfileEventPublisher(kafkaTemplate, objectMapper, userMetrics);
        logger = (Logger) LoggerFactory.getLogger(KafkaUserProfileEventPublisher.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    private UserProfileUpdatedSpringEvent sampleSpringEvent() {
        return new UserProfileUpdatedSpringEvent(
                UUID.randomUUID(), "닉네임", "010-1234-5678", "https://img.example.com/a.png", Instant.now(), "ecommerce");
    }

    @Test
    @DisplayName("Kafka 전송 시 직렬화 실패 시 메트릭이 증가하고 ERROR 로그가 기록된다")
    void handleProfileUpdated_serializationFailure_incrementsMetricAndLogsError() {
        ObjectMapper brokenMapper = new ObjectMapper(); // JavaTimeModule 없으면 Instant 직렬화 실패
        KafkaUserProfileEventPublisher brokenPublisher =
                new KafkaUserProfileEventPublisher(kafkaTemplate, brokenMapper, userMetrics);

        brokenPublisher.handleProfileUpdated(sampleSpringEvent());

        then(userMetrics).should().incrementEventPublishFailure("UserProfileUpdated");
        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.ERROR)
                .anyMatch(e -> e.getFormattedMessage().contains("Event serialization failed"));
    }

    @Test
    @DisplayName("Kafka 비동기 전송 실패 시 메트릭이 증가하고 ERROR 로그가 기록된다")
    void handleProfileUpdated_kafkaAsyncFailure_incrementsMetricAndLogsError() {
        CompletableFuture future = new CompletableFuture();
        given(kafkaTemplate.send(eq("user.user.profile-updated"), any(), any())).willReturn(future);

        publisher.handleProfileUpdated(sampleSpringEvent());

        future.completeExceptionally(new RuntimeException("Kafka broker unavailable"));

        then(userMetrics).should().incrementEventPublishFailure("UserProfileUpdated");
        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.ERROR)
                .anyMatch(e -> e.getFormattedMessage().contains("Event publishing failed"));
    }
}
