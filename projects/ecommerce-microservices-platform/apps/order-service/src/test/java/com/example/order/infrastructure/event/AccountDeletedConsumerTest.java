package com.example.order.infrastructure.event;

import com.example.order.application.service.OrderPiiAnonymizationService;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountDeletedConsumer 단위 테스트 (order-service, ADR-MONO-037 P3-B)")
class AccountDeletedConsumerTest {

    @Mock
    private OrderPiiAnonymizationService orderPiiAnonymizationService;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private AccountDeletedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AccountDeletedConsumer(
                orderPiiAnonymizationService, eventDeduplicationChecker, objectMapper);
        // default: not a duplicate (lenient — some fail-soft tests skip the dedup path entirely)
        lenient().when(eventDeduplicationChecker.isDuplicate(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("AccountDeleted"))).thenReturn(false);
    }

    private String json(UUID accountId, Boolean anonymized) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "account.deleted",
                  "occurredAt": "2026-06-15T10:00:00Z",
                  "source": "account-service",
                  "payload": {
                    "accountId": "%s",
                    "tenantId": "ecommerce",
                    "reasonCode": "USER_REQUEST",
                    "actorType": "user",
                    "deletedAt": "2026-06-15T10:00:00Z",
                    "gracePeriodEndsAt": "2026-07-15T10:00:00Z",
                    "anonymized": %s
                  }
                }
                """.formatted(UUID.randomUUID(), accountId, anonymized);
    }

    @Nested
    @DisplayName("onMessage / phase routing")
    class PhaseRouting {

        @Test
        @DisplayName("anonymized=true (유예 종료) → 주문 PII 익명화를 호출한다")
        void onMessage_postGrace_callsAnonymize() {
            UUID accountId = UUID.randomUUID();

            consumer.onMessage(json(accountId, true));

            then(orderPiiAnonymizationService).should().anonymizeOrdersForAccount(accountId.toString());
            then(orderPiiAnonymizationService).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("anonymized=false (유예 진입) → 주문 PII 동작 없음 (UserWithdrawn 반응이 취소 담당)")
        void onMessage_graceEntry_noOrderPiiAction() {
            consumer.onMessage(json(UUID.randomUUID(), false));

            then(orderPiiAnonymizationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("잘못된 JSON이면 IllegalArgumentException이 발생한다 (DLQ 라우팅)")
        void onMessage_invalidJson_throwsException() {
            assertThatThrownBy(() -> consumer.onMessage("{ invalid json }"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Failed to deserialize account.deleted event");
        }
    }

    @Nested
    @DisplayName("handle (idempotency + fail-soft)")
    class Handle {

        @Test
        @DisplayName("중복 이벤트면 아무 동작도 하지 않는다")
        void handle_duplicate_skips() {
            UUID accountId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            given(eventDeduplicationChecker.isDuplicate(eventId.toString(), "AccountDeleted")).willReturn(true);
            AccountDeletedEvent event = new AccountDeletedEvent(
                    eventId, "account.deleted", Instant.now(), "account-service", "ecommerce",
                    new AccountDeletedEvent.Payload(accountId, "ecommerce", "USER_REQUEST", "user", null,
                            Instant.now(), Instant.now(), true));

            consumer.handle(event);

            then(orderPiiAnonymizationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("payload가 null이면 익명화를 호출하지 않는다 (fail-soft)")
        void handle_nullPayload_skips() {
            AccountDeletedEvent event = new AccountDeletedEvent(
                    UUID.randomUUID(), "account.deleted", Instant.now(), "account-service", "ecommerce", null);

            consumer.handle(event);

            then(orderPiiAnonymizationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("accountId가 null이면 익명화를 호출하지 않는다 (fail-soft)")
        void handle_nullAccountId_skips() {
            AccountDeletedEvent event = new AccountDeletedEvent(
                    UUID.randomUUID(), "account.deleted", Instant.now(), "account-service", "ecommerce",
                    new AccountDeletedEvent.Payload(null, "ecommerce", "USER_REQUEST", "user", null,
                            Instant.now(), Instant.now(), true));

            consumer.handle(event);

            then(orderPiiAnonymizationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("anonymized가 null이면 유예 진입으로 취급 — 주문 PII 동작 없음")
        void handle_nullAnonymized_treatedAsGraceEntry() {
            UUID accountId = UUID.randomUUID();
            AccountDeletedEvent event = new AccountDeletedEvent(
                    UUID.randomUUID(), "account.deleted", Instant.now(), "account-service", "ecommerce",
                    new AccountDeletedEvent.Payload(accountId, "ecommerce", "USER_REQUEST", "user", null,
                            Instant.now(), Instant.now(), null));

            consumer.handle(event);

            then(orderPiiAnonymizationService).shouldHaveNoInteractions();
        }
    }
}
