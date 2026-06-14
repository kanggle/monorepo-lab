package com.example.user.infrastructure.event;

import com.example.user.application.service.UserSignedUpHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSignedUpConsumer 단위 테스트")
class UserSignedUpConsumerTest {

    @Mock
    private UserSignedUpHandler userSignedUpHandler;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private UserSignedUpConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UserSignedUpConsumer(userSignedUpHandler, objectMapper);
    }

    @Nested
    @DisplayName("onMessage")
    class OnMessage {

        @Test
        @DisplayName("유효한 JSON 메시지를 수신하면 핸들러를 호출한다")
        void onMessage_validPayload_callsHandler() {
            UUID userId = UUID.randomUUID();
            String json = """
                    {
                      "event_id": "%s",
                      "event_type": "UserSignedUp",
                      "occurred_at": 1711324800.000000000,
                      "source": "auth-service",
                      "payload": {
                        "userId": "%s",
                        "email": "test@example.com",
                        "name": "홍길동"
                      }
                    }
                    """.formatted(UUID.randomUUID(), userId);

            consumer.onMessage(json);

            then(userSignedUpHandler).should().handle(userId, "test@example.com", "홍길동");
        }

        @Test
        @DisplayName("잘못된 JSON이면 IllegalArgumentException이 발생한다")
        void onMessage_invalidJson_throwsException() {
            String invalidJson = "{ invalid json }";

            assertThatThrownBy(() -> consumer.onMessage(invalidJson))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Failed to deserialize UserSignedUp event");
        }
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        @Test
        @DisplayName("payload가 null이면 핸들러를 호출하지 않는다")
        void handle_nullPayload_skips() {
            UserSignedUpEvent event = new UserSignedUpEvent(
                    UUID.randomUUID(), "UserSignedUp", Instant.now(), "auth-service", "ecommerce", null);

            consumer.handle(event);

            then(userSignedUpHandler).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("userId가 null이면 핸들러를 호출하지 않는다")
        void handle_nullUserId_skips() {
            UserSignedUpEvent event = new UserSignedUpEvent(
                    UUID.randomUUID(), "UserSignedUp", Instant.now(), "auth-service", "ecommerce",
                    new UserSignedUpEvent.Payload(null, "test@example.com", "홍길동"));

            consumer.handle(event);

            then(userSignedUpHandler).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("email이 null이면 핸들러를 호출하지 않는다")
        void handle_nullEmail_skips() {
            UserSignedUpEvent event = new UserSignedUpEvent(
                    UUID.randomUUID(), "UserSignedUp", Instant.now(), "auth-service", "ecommerce",
                    new UserSignedUpEvent.Payload(UUID.randomUUID(), null, "홍길동"));

            consumer.handle(event);

            then(userSignedUpHandler).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("name이 null이어도 핸들러를 호출한다")
        void handle_nullName_callsHandler() {
            UUID userId = UUID.randomUUID();
            UserSignedUpEvent event = new UserSignedUpEvent(
                    UUID.randomUUID(), "UserSignedUp", Instant.now(), "auth-service", "ecommerce",
                    new UserSignedUpEvent.Payload(userId, "test@example.com", null));

            consumer.handle(event);

            then(userSignedUpHandler).should().handle(userId, "test@example.com", null);
        }
    }
}
