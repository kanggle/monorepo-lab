package com.example.order.infrastructure.event;

import com.example.order.application.service.UserWithdrawalOrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserWithdrawnEventConsumer 단위 테스트")
class UserWithdrawnEventConsumerTest {

    @InjectMocks
    private UserWithdrawnEventConsumer consumer;

    @Mock
    private UserWithdrawalOrderService userWithdrawalOrderService;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    @Mock
    private ObjectMapper objectMapper;

    private UserWithdrawnEvent event(String userId) {
        return new UserWithdrawnEvent(
                UUID.randomUUID().toString(),
                "UserWithdrawn",
                "2026-03-24T00:00:00Z",
                "user-service",
                new UserWithdrawnEvent.UserWithdrawnPayload(userId, "2026-03-24T00:00:00Z")
        );
    }

    @Test
    @DisplayName("정상 이벤트 수신 시 cancelOrdersForWithdrawnUser를 호출한다")
    void handle_validEvent_callsService() {
        consumer.handle(event("user-1"));

        verify(userWithdrawalOrderService).cancelOrdersForWithdrawnUser("user-1");
    }

    @Test
    @DisplayName("payload가 null이면 서비스를 호출하지 않는다")
    void handle_nullPayload_doesNotCallService() {
        UserWithdrawnEvent event = new UserWithdrawnEvent(
                UUID.randomUUID().toString(), "UserWithdrawn",
                "2026-03-24T00:00:00Z", "user-service", null
        );

        consumer.handle(event);

        verify(userWithdrawalOrderService, never()).cancelOrdersForWithdrawnUser(any());
    }

    @Test
    @DisplayName("userId가 null이면 서비스를 호출하지 않는다")
    void handle_nullUserId_doesNotCallService() {
        consumer.handle(event(null));

        verify(userWithdrawalOrderService, never()).cancelOrdersForWithdrawnUser(any());
    }

    @Test
    @DisplayName("userId가 blank이면 서비스를 호출하지 않는다")
    void handle_blankUserId_doesNotCallService() {
        consumer.handle(event("  "));

        verify(userWithdrawalOrderService, never()).cancelOrdersForWithdrawnUser(any());
    }

    @Test
    @DisplayName("서비스에서 예외가 발생하면 외부로 전파된다 (DLQ 라우팅)")
    void handle_serviceThrows_propagatesException() {
        doThrow(new RuntimeException("DB error"))
                .when(userWithdrawalOrderService).cancelOrdersForWithdrawnUser("user-1");

        assertThatThrownBy(() -> consumer.handle(event("user-1")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("중복 이벤트 수신 시 서비스를 호출하지 않는다")
    void handle_duplicateEvent_doesNotCallService() {
        UserWithdrawnEvent event = event("user-1");
        when(eventDeduplicationChecker.isDuplicate(event.eventId(), "UserWithdrawn")).thenReturn(true);

        consumer.handle(event);

        verify(userWithdrawalOrderService, never()).cancelOrdersForWithdrawnUser(any());
    }

    @Test
    @DisplayName("역직렬화 실패 시 JsonProcessingException이 래핑 없이 직접 전파된다 (DLQ 라우팅)")
    void onMessage_deserializationFails_throwsJsonProcessingException() throws Exception {
        when(objectMapper.readValue(anyString(), eq(UserWithdrawnEvent.class)))
                .thenThrow(new com.fasterxml.jackson.databind.JsonMappingException(null, "invalid"));

        assertThatThrownBy(() -> consumer.onMessage("invalid-json"))
                .isInstanceOf(JsonProcessingException.class);
    }
}
