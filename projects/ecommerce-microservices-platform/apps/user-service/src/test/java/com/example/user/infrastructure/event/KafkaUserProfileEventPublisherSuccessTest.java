package com.example.user.infrastructure.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.user.application.event.UserProfileUpdatedSpringEvent;
import com.example.user.application.event.UserWithdrawnSpringEvent;
import com.example.user.infrastructure.metrics.UserMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
@DisplayName("KafkaUserProfileEventPublisher 성공 경로 테스트")
class KafkaUserProfileEventPublisherSuccessTest {

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

    @Test
    @DisplayName("프로필 업데이트 이벤트가 올바른 토픽과 키로 전송된다")
    void handleProfileUpdated_success_sendsToCorrectTopicWithUserIdKey() {
        UUID userId = UUID.randomUUID();
        var springEvent = new UserProfileUpdatedSpringEvent(
                userId, "닉네임", "010-1234-5678", "https://img.example.com/a.png", Instant.now());
        given(kafkaTemplate.send(any(), any(), any())).willReturn(CompletableFuture.completedFuture(null));

        publisher.handleProfileUpdated(springEvent);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        then(kafkaTemplate).should().send(topicCaptor.capture(), keyCaptor.capture(), messageCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("user.user-profile.updated");
        assertThat(keyCaptor.getValue()).isEqualTo(userId.toString());
        assertThat(messageCaptor.getValue()).contains("UserProfileUpdated");
    }

    @Test
    @DisplayName("프로필 업데이트 성공 시 INFO 로그가 기록된다")
    void handleProfileUpdated_success_logsInfo() {
        UUID userId = UUID.randomUUID();
        var springEvent = new UserProfileUpdatedSpringEvent(
                userId, "닉네임", "010-1234-5678", null, Instant.now());
        given(kafkaTemplate.send(any(), any(), any())).willReturn(CompletableFuture.completedFuture(null));

        publisher.handleProfileUpdated(springEvent);

        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.INFO)
                .anyMatch(e -> e.getFormattedMessage().contains("Published UserProfileUpdated"));
    }

    @Test
    @DisplayName("탈퇴 이벤트가 올바른 토픽으로 전송된다")
    void handleUserWithdrawn_success_sendsToCorrectTopic() {
        UUID userId = UUID.randomUUID();
        var springEvent = new UserWithdrawnSpringEvent(userId, Instant.now());
        given(kafkaTemplate.send(any(), any(), any())).willReturn(CompletableFuture.completedFuture(null));

        publisher.handleUserWithdrawn(springEvent);

        then(kafkaTemplate).should().send(eq("user.user-withdrawn"), eq(userId.toString()), any());
    }

    @Test
    @DisplayName("탈퇴 이벤트 전송 시 메시지에 UserWithdrawn 타입이 포함된다")
    void handleUserWithdrawn_success_messageContainsEventType() {
        UUID userId = UUID.randomUUID();
        var springEvent = new UserWithdrawnSpringEvent(userId, Instant.now());
        given(kafkaTemplate.send(any(), any(), any())).willReturn(CompletableFuture.completedFuture(null));

        publisher.handleUserWithdrawn(springEvent);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        then(kafkaTemplate).should().send(any(), any(), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("UserWithdrawn");
        assertThat(messageCaptor.getValue()).contains(userId.toString());
    }

    @Test
    @DisplayName("탈퇴 이벤트 직렬화 실패 시 메트릭이 증가한다")
    void handleUserWithdrawn_serializationFailure_incrementsMetric() {
        ObjectMapper brokenMapper = new ObjectMapper();
        KafkaUserProfileEventPublisher brokenPublisher =
                new KafkaUserProfileEventPublisher(kafkaTemplate, brokenMapper, userMetrics);

        brokenPublisher.handleUserWithdrawn(new UserWithdrawnSpringEvent(UUID.randomUUID(), Instant.now()));

        then(userMetrics).should().incrementEventPublishFailure("UserWithdrawn");
    }
}
