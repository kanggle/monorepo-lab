package com.example.user.infrastructure.event;

import com.example.user.application.service.UserProfileService;
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
@DisplayName("AccountDeletedConsumer 단위 테스트 (ADR-MONO-037 P2 two-phase, flat wire TASK-BE-422)")
class AccountDeletedConsumerTest {

    @Mock
    private UserProfileService userProfileService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private AccountDeletedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AccountDeletedConsumer(userProfileService, objectMapper);
    }

    @Nested
    @DisplayName("onMessage / phase routing")
    class PhaseRouting {

        @Test
        @DisplayName("FLAT anonymized=false (유예 진입) → withdrawProfile 만 호출한다")
        void onMessage_graceEntry_callsWithdraw() {
            UUID accountId = UUID.randomUUID();
            // EXACT flat shape from iam-platform account-events.md § account.deleted
            // (top-level fields, NO nested payload, NO eventId).
            String json = """
                    {
                      "accountId": "%s",
                      "tenantId": "ecommerce",
                      "reasonCode": "USER_REQUEST",
                      "actorType": "user",
                      "actorId": null,
                      "deletedAt": "2026-06-15T10:00:00Z",
                      "gracePeriodEndsAt": "2026-07-15T10:00:00Z",
                      "anonymized": false
                    }
                    """.formatted(accountId);

            consumer.onMessage(json);

            then(userProfileService).should().withdrawProfile(accountId);
            then(userProfileService).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("FLAT anonymized=true (유예 종료) → anonymizeProfile 만 호출한다")
        void onMessage_postGrace_callsAnonymize() {
            UUID accountId = UUID.randomUUID();
            String json = """
                    {
                      "accountId": "%s",
                      "tenantId": "ecommerce",
                      "reasonCode": "USER_REQUEST",
                      "actorType": "user",
                      "actorId": null,
                      "deletedAt": "2026-06-15T10:00:00Z",
                      "gracePeriodEndsAt": "2026-07-15T10:00:00Z",
                      "anonymized": true
                    }
                    """.formatted(accountId);

            consumer.onMessage(json);

            then(userProfileService).should().anonymizeProfile(accountId);
            then(userProfileService).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("잘못된 JSON이면 IllegalArgumentException이 발생한다")
        void onMessage_invalidJson_throwsException() {
            assertThatThrownBy(() -> consumer.onMessage("{ invalid json }"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Failed to deserialize account.deleted event");
        }
    }

    @Nested
    @DisplayName("handle (fail-soft)")
    class Handle {

        @Test
        @DisplayName("accountId가 null이면 아무 반응도 하지 않는다")
        void handle_nullAccountId_skips() {
            AccountDeletedEvent event = new AccountDeletedEvent(
                    null, "ecommerce", "USER_REQUEST", "user", null,
                    Instant.now(), Instant.now(), false);

            consumer.handle(event);

            then(userProfileService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("anonymized 가 null 이면 유예 진입(withdraw)으로 취급한다")
        void handle_nullAnonymized_treatedAsGraceEntry() {
            UUID accountId = UUID.randomUUID();
            AccountDeletedEvent event = new AccountDeletedEvent(
                    accountId, "ecommerce", "USER_REQUEST", "user", null,
                    Instant.now(), Instant.now(), null);

            consumer.handle(event);

            then(userProfileService).should().withdrawProfile(accountId);
        }
    }
}
