package com.example.notification.adapter.in.event;

import com.example.notification.application.command.SendNotificationCommand;
import com.example.notification.application.port.in.SendNotificationUseCase;
import com.example.notification.domain.model.TemplateType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountCreatedEventConsumer 단위 테스트 (ADR-MONO-037 P1 welcome, flat wire TASK-BE-422)")
class AccountCreatedEventConsumerTest {

    @Mock
    private SendNotificationUseCase notificationSendService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AccountCreatedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AccountCreatedEventConsumer(notificationSendService, objectMapper);
    }

    @Nested
    @DisplayName("onMessage")
    class OnMessage {

        @Test
        @DisplayName("FLAT account.created 수신 시 WELCOME 알림을 PII 개인화 없이 발송한다")
        void onMessage_flatWire_sendsWelcomeWithoutPii() throws Exception {
            String accountId = "550e8400-e29b-41d4-a716-446655440001";
            // EXACT flat shape from iam-platform account-events.md § account.created
            // (top-level fields, NO nested payload, NO eventId).
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

            ArgumentCaptor<SendNotificationCommand> captor = ArgumentCaptor.forClass(SendNotificationCommand.class);
            then(notificationSendService).should().sendNotification(captor.capture());
            SendNotificationCommand command = captor.getValue();
            assertThat(command.userId()).isEqualTo(accountId);
            // No eventId on the flat wire → stable accountId-derived dedup key.
            assertThat(command.eventId()).isEqualTo("account.created:" + accountId);
            assertThat(command.tenantId()).isEqualTo("ecommerce");
            assertThat(command.templateType()).isEqualTo(TemplateType.WELCOME);
            // No PII personalization — account.created is emailHash-only.
            assertThat(command.variables().get("name")).isEmpty();
            assertThat(command.variables().get("email")).isEmpty();
        }

        @Test
        @DisplayName("snake_case alias 도 수신하면 WELCOME 을 발송한다 (forward-compat)")
        void onMessage_snakeCase_sendsWelcome() throws Exception {
            String accountId = "550e8400-e29b-41d4-a716-446655440001";
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

            ArgumentCaptor<SendNotificationCommand> captor = ArgumentCaptor.forClass(SendNotificationCommand.class);
            then(notificationSendService).should().sendNotification(captor.capture());
            SendNotificationCommand command = captor.getValue();
            assertThat(command.userId()).isEqualTo(accountId);
            assertThat(command.tenantId()).isEqualTo("ecommerce");
        }
    }

    @Nested
    @DisplayName("handle (fail-soft)")
    class Handle {

        @Test
        @DisplayName("accountId가 null이면 알림을 발송하지 않는다")
        void handle_nullAccountId_skips() {
            AccountCreatedEvent event = new AccountCreatedEvent(
                    null, "ecommerce", "hash", "ACTIVE", "ko-KR", "2026-06-15T10:00:00Z");

            consumer.handle(event);

            then(notificationSendService).shouldHaveNoInteractions();
        }
    }
}
