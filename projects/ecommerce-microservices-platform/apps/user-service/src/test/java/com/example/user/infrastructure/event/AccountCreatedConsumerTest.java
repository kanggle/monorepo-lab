package com.example.user.infrastructure.event;

import com.example.user.application.service.AccountCreatedHandler;
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
@DisplayName("AccountCreatedConsumer 단위 테스트 (ADR-MONO-037 P1, flat wire TASK-BE-422)")
class AccountCreatedConsumerTest {

    @Mock
    private AccountCreatedHandler accountCreatedHandler;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private AccountCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AccountCreatedConsumer(accountCreatedHandler, objectMapper);
    }

    @Nested
    @DisplayName("onMessage")
    class OnMessage {

        @Test
        @DisplayName("IAM account.created (FLAT wire, emailHash-only) 를 수신하면 accountId 로 핸들러를 호출한다")
        void onMessage_flatWire_callsHandlerWithAccountId() {
            UUID accountId = UUID.randomUUID();
            // EXACT flat shape from iam-platform account-events.md § account.created
            // (top-level fields, NO nested payload, NO eventId/envelope).
            String json = """
                    {
                      "accountId": "%s",
                      "tenantId": "ecommerce",
                      "emailHash": "a1b2c3d4e5",
                      "status": "ACTIVE",
                      "locale": "ko-KR",
                      "createdAt": "2026-06-15T10:00:00Z"
                    }
                    """.formatted(accountId);

            consumer.onMessage(json);

            then(accountCreatedHandler).should().handle(accountId);
        }

        @Test
        @DisplayName("snake_case alias 도 수신하면 핸들러를 호출한다 (forward-compat)")
        void onMessage_snakeCase_callsHandler() {
            UUID accountId = UUID.randomUUID();
            String json = """
                    {
                      "account_id": "%s",
                      "tenant_id": "ecommerce",
                      "email_hash": "a1b2c3d4e5",
                      "status": "ACTIVE",
                      "locale": "ko-KR",
                      "created_at": "2026-06-15T10:00:00Z"
                    }
                    """.formatted(accountId);

            consumer.onMessage(json);

            then(accountCreatedHandler).should().handle(accountId);
        }

        @Test
        @DisplayName("잘못된 JSON이면 IllegalArgumentException이 발생한다")
        void onMessage_invalidJson_throwsException() {
            assertThatThrownBy(() -> consumer.onMessage("{ invalid json }"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Failed to deserialize account.created event");
        }
    }

    @Nested
    @DisplayName("handle (fail-soft)")
    class Handle {

        @Test
        @DisplayName("accountId가 null이면 핸들러를 호출하지 않는다")
        void handle_nullAccountId_skips() {
            AccountCreatedEvent event = new AccountCreatedEvent(
                    null, "ecommerce", "hash", "ACTIVE", "ko-KR", Instant.now());

            consumer.handle(event);

            then(accountCreatedHandler).shouldHaveNoInteractions();
        }
    }
}
