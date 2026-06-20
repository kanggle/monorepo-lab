package com.example.product.infrastructure.event;

import com.example.product.application.service.RegisterSellerService;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Unit tests for the reverse {@code account.status.changed → seller SUSPENDED} projection
 * (ADR-MONO-042 D4-C, TASK-BE-421). Mirrors {@code user-service} AccountDeletedConsumerTest:
 * a real tolerant {@link ObjectMapper} (envelope deserialization is part of the contract) +
 * a mocked {@link RegisterSellerService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountStatusChangedSellerConsumer 단위 테스트 (ADR-MONO-042 D4-C 역방향 투영)")
class AccountStatusChangedSellerConsumerTest {

    @Mock
    private RegisterSellerService registerSellerService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private AccountStatusChangedSellerConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AccountStatusChangedSellerConsumer(registerSellerService, objectMapper);
    }

    // FLAT wire shape — matches the IAM producer (AccountEventPublisher.saveEvent serializes the
    // payload map directly, no envelope) and account-events.md § account.status.changed. A nested
    // "payload" wrapper here would mask the BE-388-class deserialization trap.
    private String flatWire(String accountId, String tenantId, String currentStatus) {
        return """
                {
                  "accountId": "%s",
                  "tenantId": "%s",
                  "previousStatus": "ACTIVE",
                  "currentStatus": "%s",
                  "reasonCode": "ADMIN_LOCK",
                  "actorType": "operator",
                  "actorId": "op-1",
                  "occurredAt": "2026-06-20T10:00:00Z"
                }
                """.formatted(accountId, tenantId, currentStatus);
    }

    @Nested
    @DisplayName("onMessage / status routing")
    class StatusRouting {

        @Test
        @DisplayName("currentStatus=LOCKED → 해당 셀러를 suspend 한다")
        void onMessage_locked_suspendsSeller() {
            given(registerSellerService.suspendByLockedAccount("acct-1")).willReturn(true);

            consumer.onMessage(flatWire("acct-1", "ecommerce", "LOCKED"));

            then(registerSellerService).should().suspendByLockedAccount("acct-1");
            then(registerSellerService).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("이미 SUSPENDED (suspendByLockedAccount=false) → 추가 update 없음 (멱등)")
        void onMessage_alreadySuspended_noUpdate() {
            given(registerSellerService.suspendByLockedAccount("acct-1")).willReturn(false);

            consumer.onMessage(flatWire("acct-1", "ecommerce", "LOCKED"));

            then(registerSellerService).should().suspendByLockedAccount("acct-1");
            then(registerSellerService).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("셀러 미존재 (suspendByLockedAccount=false) → fail-soft 스킵")
        void onMessage_sellerNotFound_skips() {
            given(registerSellerService.suspendByLockedAccount("acct-x")).willReturn(false);

            consumer.onMessage(flatWire("acct-x", "ecommerce", "LOCKED"));

            then(registerSellerService).should().suspendByLockedAccount("acct-x");
            then(registerSellerService).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("currentStatus=ACTIVE → LOCKED 아님 → 아무 반응도 하지 않는다")
        void onMessage_nonLocked_skips() {
            consumer.onMessage(flatWire("acct-1", "ecommerce", "ACTIVE"));

            then(registerSellerService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("CLOSED 셀러 (IllegalStateException) → catch, 재던지지 않음")
        void onMessage_closedSeller_caughtNotRethrown() {
            given(registerSellerService.suspendByLockedAccount("acct-c"))
                    .willThrow(new IllegalStateException("Cannot suspend a CLOSED seller: s-c"));

            // No exception escapes onMessage (race tolerated → no DLQ).
            consumer.onMessage(flatWire("acct-c", "ecommerce", "LOCKED"));

            then(registerSellerService).should().suspendByLockedAccount("acct-c");
        }

        @Test
        @DisplayName("잘못된 JSON이면 IllegalArgumentException이 발생한다")
        void onMessage_invalidJson_throwsException() {
            assertThatThrownBy(() -> consumer.onMessage("{ invalid json }"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Failed to deserialize account.status.changed event");
        }

        @Test
        @DisplayName("실 IAM 와이어(flat, account-events.md 계약 그대로) 역직렬화 → suspend (nested-DTO 함정 회귀 가드)")
        void onMessage_realFlatContractWire_deserializesAndSuspends() {
            // The EXACT flat payload from specs/contracts/events/account-events.md
            // § account.status.changed: top-level fields, NO envelope, NO nested "payload".
            // A nested DTO would deserialize accountId=null here and silently no-op (BE-388 class).
            String wire = """
                    {
                      "accountId": "acct-real",
                      "tenantId": "fan-platform",
                      "previousStatus": "ACTIVE",
                      "currentStatus": "LOCKED",
                      "reasonCode": "ADMIN_LOCK",
                      "actorType": "operator",
                      "actorId": "op-9",
                      "occurredAt": "2026-04-12T10:00:00Z"
                    }
                    """;
            given(registerSellerService.suspendByLockedAccount("acct-real")).willReturn(true);

            consumer.onMessage(wire);

            then(registerSellerService).should().suspendByLockedAccount("acct-real");
        }
    }

    @Nested
    @DisplayName("handle (fail-soft guards)")
    class Handle {

        @Test
        @DisplayName("accountId가 null이면 아무 반응도 하지 않는다")
        void handle_nullAccountId_skips() {
            AccountStatusChangedEvent event = new AccountStatusChangedEvent(
                    null, "ecommerce", "ACTIVE", "LOCKED", "ADMIN_LOCK",
                    "operator", "op-1", Instant.now());

            consumer.handle(event);

            then(registerSellerService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("accountId가 blank이면 아무 반응도 하지 않는다")
        void handle_blankAccountId_skips() {
            AccountStatusChangedEvent event = new AccountStatusChangedEvent(
                    "  ", "ecommerce", "ACTIVE", "LOCKED", "ADMIN_LOCK",
                    "operator", "op-1", Instant.now());

            consumer.handle(event);

            then(registerSellerService).shouldHaveNoInteractions();
        }
    }
}
