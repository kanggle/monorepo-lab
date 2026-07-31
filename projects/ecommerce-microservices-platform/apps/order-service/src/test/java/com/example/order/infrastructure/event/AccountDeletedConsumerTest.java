package com.example.order.infrastructure.event;

import com.example.messaging.dedupe.EventDedupePort;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountDeletedConsumer 단위 테스트 (order-service, ADR-MONO-037 P3-B, flat wire TASK-BE-422, ADR-MONO-058 D7)")
class AccountDeletedConsumerTest {

    @Mock
    private OrderPiiAnonymizationService orderPiiAnonymizationService;

    @Mock
    private EventDedupePort eventDedupePort;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private AccountDeletedConsumer consumer;

    /** Mirrors the consumer's own accountId+phase composite → deterministic UUID derivation. */
    private static UUID dedupId(UUID accountId, String phase) {
        String dedupKey = accountId + ":" + phase;
        return UUID.nameUUIDFromBytes(dedupKey.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setUp() {
        consumer = new AccountDeletedConsumer(
                orderPiiAnonymizationService, eventDedupePort, objectMapper);
        // default: not a duplicate — runs the work (lenient — some fail-soft tests skip the
        // dedup path entirely, e.g. a null accountId never reaches eventDedupePort at all).
        lenient().when(eventDedupePort.process(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("AccountDeleted"), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(EventDedupeTestSupport::runWork);
    }

    // EXACT flat shape from iam-platform account-events.md § account.deleted
    // (top-level fields, NO nested payload, NO eventId).
    private String json(UUID accountId, Boolean anonymized) {
        return """
                {
                  "accountId": "%s",
                  "tenantId": "ecommerce",
                  "reasonCode": "USER_REQUEST",
                  "actorType": "user",
                  "actorId": null,
                  "deletedAt": "2026-06-15T10:00:00Z",
                  "gracePeriodEndsAt": "2026-07-15T10:00:00Z",
                  "anonymized": %s
                }
                """.formatted(accountId, anonymized);
    }

    @Nested
    @DisplayName("onMessage / phase routing")
    class PhaseRouting {

        @Test
        @DisplayName("FLAT anonymized=true (유예 종료) → 주문 PII 익명화를 호출한다")
        void onMessage_postGrace_callsAnonymize() {
            UUID accountId = UUID.randomUUID();

            consumer.onMessage(json(accountId, true));

            then(orderPiiAnonymizationService).should().anonymizeOrdersForAccount(accountId.toString());
            then(orderPiiAnonymizationService).shouldHaveNoMoreInteractions();
            // dedup keyed off the accountId+phase composite (no eventId on the flat wire),
            // deterministically hashed to a UUID for the shared EventDedupePort.
            then(eventDedupePort).should().process(
                    org.mockito.ArgumentMatchers.eq(dedupId(accountId, "anon")),
                    org.mockito.ArgumentMatchers.eq("AccountDeleted"),
                    org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("FLAT anonymized=false (유예 진입) → 주문 PII 동작 없음 (UserWithdrawn 반응이 취소 담당)")
        void onMessage_graceEntry_noOrderPiiAction() {
            UUID accountId = UUID.randomUUID();

            consumer.onMessage(json(accountId, false));

            then(orderPiiAnonymizationService).shouldHaveNoInteractions();
            // grace phase dedups under its own composite key.
            then(eventDedupePort).should().process(
                    org.mockito.ArgumentMatchers.eq(dedupId(accountId, "grace")),
                    org.mockito.ArgumentMatchers.eq("AccountDeleted"),
                    org.mockito.ArgumentMatchers.any());
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
        @DisplayName("중복 이벤트(accountId+phase 복합키)면 아무 동작도 하지 않는다")
        void handle_duplicate_skips() {
            UUID accountId = UUID.randomUUID();
            given(eventDedupePort.process(org.mockito.ArgumentMatchers.eq(dedupId(accountId, "anon")),
                    org.mockito.ArgumentMatchers.eq("AccountDeleted"), org.mockito.ArgumentMatchers.any()))
                    .willReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);
            AccountDeletedEvent event = new AccountDeletedEvent(
                    accountId, "ecommerce", "USER_REQUEST", "user", null,
                    Instant.now(), Instant.now(), true);

            consumer.handle(event);

            then(orderPiiAnonymizationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("anon 단계 중복이어도 grace 단계는 독립적으로 처리된다 (복합키 분리)")
        void handle_anonDuplicate_graceStillIndependent() {
            UUID accountId = UUID.randomUUID();
            // anon already processed, grace not yet (grace already defaults to APPLIED via setUp()).
            lenient().when(eventDedupePort.process(org.mockito.ArgumentMatchers.eq(dedupId(accountId, "anon")),
                            org.mockito.ArgumentMatchers.eq("AccountDeleted"), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);
            AccountDeletedEvent graceEvent = new AccountDeletedEvent(
                    accountId, "ecommerce", "USER_REQUEST", "user", null,
                    Instant.now(), Instant.now(), false);

            consumer.handle(graceEvent);

            // grace branch is a no-op for order-PII but must NOT be treated as a duplicate of anon.
            then(eventDedupePort).should().process(
                    org.mockito.ArgumentMatchers.eq(dedupId(accountId, "grace")),
                    org.mockito.ArgumentMatchers.eq("AccountDeleted"),
                    org.mockito.ArgumentMatchers.any());
            then(orderPiiAnonymizationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("accountId가 null이면 익명화를 호출하지 않는다 (fail-soft)")
        void handle_nullAccountId_skips() {
            AccountDeletedEvent event = new AccountDeletedEvent(
                    null, "ecommerce", "USER_REQUEST", "user", null,
                    Instant.now(), Instant.now(), true);

            consumer.handle(event);

            then(orderPiiAnonymizationService).shouldHaveNoInteractions();
            then(eventDedupePort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("anonymized가 null이면 유예 진입으로 취급 — 주문 PII 동작 없음")
        void handle_nullAnonymized_treatedAsGraceEntry() {
            UUID accountId = UUID.randomUUID();
            AccountDeletedEvent event = new AccountDeletedEvent(
                    accountId, "ecommerce", "USER_REQUEST", "user", null,
                    Instant.now(), Instant.now(), null);

            consumer.handle(event);

            then(orderPiiAnonymizationService).shouldHaveNoInteractions();
            then(eventDedupePort).should().process(
                    org.mockito.ArgumentMatchers.eq(dedupId(accountId, "grace")),
                    org.mockito.ArgumentMatchers.eq("AccountDeleted"),
                    org.mockito.ArgumentMatchers.any());
        }
    }
}
