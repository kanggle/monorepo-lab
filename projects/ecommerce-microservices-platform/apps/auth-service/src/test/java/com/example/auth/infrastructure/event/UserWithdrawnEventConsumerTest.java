package com.example.auth.infrastructure.event;

import com.example.auth.application.service.UserWithdrawalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserWithdrawnEventConsumer 단위 테스트")
class UserWithdrawnEventConsumerTest {

    @InjectMocks
    private UserWithdrawnEventConsumer consumer;

    @Mock
    private UserWithdrawalService userWithdrawalService;

    @Spy
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("정상 이벤트 수신 시 서비스를 호출한다")
    void onMessage_validEvent_callsService() {
        String userId = UUID.randomUUID().toString();
        String payload = """
                {
                  "eventId": "evt-1",
                  "eventType": "UserWithdrawn",
                  "occurredAt": "2026-03-24T00:00:00Z",
                  "source": "user-service",
                  "payload": { "userId": "%s", "withdrawnAt": "2026-03-24T00:00:00Z" }
                }
                """.formatted(userId);

        consumer.onMessage(payload);

        then(userWithdrawalService).should().handleUserWithdrawal(userId);
    }

    @Test
    @DisplayName("payload가 null인 이벤트는 무시한다")
    void handle_nullPayload_skips() {
        var event = new UserWithdrawnEvent("evt-1", "UserWithdrawn", "2026-03-24T00:00:00Z", "user-service", null);

        consumer.handle(event);

        then(userWithdrawalService).should(never()).handleUserWithdrawal(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("userId가 빈 문자열인 이벤트는 무시한다")
    void handle_blankUserId_skips() {
        var event = new UserWithdrawnEvent("evt-1", "UserWithdrawn", "2026-03-24T00:00:00Z", "user-service",
                new UserWithdrawnEvent.UserWithdrawnPayload("", "2026-03-24T00:00:00Z"));

        consumer.handle(event);

        then(userWithdrawalService).should(never()).handleUserWithdrawal(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("역직렬화 실패 시 RuntimeException을 던진다")
    void onMessage_invalidJson_throwsRuntimeException() {
        String invalidJson = "not a json";

        assertThatThrownBy(() -> consumer.onMessage(invalidJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to deserialize");
    }
}
