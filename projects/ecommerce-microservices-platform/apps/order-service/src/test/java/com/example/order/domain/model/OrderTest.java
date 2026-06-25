package com.example.order.domain.model;

import com.example.order.domain.exception.InvalidOrderException;
import com.example.order.domain.exception.OrderCannotBeCancelledException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Order 애그리거트 단위 테스트")
class OrderTest {

    private static final ShippingAddress ADDRESS = new ShippingAddress(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"
    );

    private static final Instant FIXED_NOW = Instant.parse("2026-03-25T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("유효한 항목으로 주문 생성 시 PENDING 상태로 생성된다")
    void create_validItems_returnsPendingOrder() {
        List<Order.OrderItemData> items = List.of(
                new Order.OrderItemData("p1", "v1", "노트북", "블랙", 2, 1000000L)
        );

        Order order = Order.create("user1", items, ADDRESS, FIXED_CLOCK);

        assertThat(order.getOrderId()).isNotNull();
        assertThat(order.getUserId()).isEqualTo("user1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getTotalPrice()).isEqualTo(2000000L);
    }

    @Test
    @DisplayName("여러 항목의 totalPrice는 unitPrice * quantity 합산이다")
    void create_multipleItems_calculatesTotalPriceCorrectly() {
        List<Order.OrderItemData> items = List.of(
                new Order.OrderItemData("p1", "v1", "노트북", "블랙", 2, 1000000L),
                new Order.OrderItemData("p2", "v2", "마우스", "화이트", 3, 50000L)
        );

        Order order = Order.create("user1", items, ADDRESS, FIXED_CLOCK);

        assertThat(order.getTotalPrice()).isEqualTo(2150000L);
    }

    @Test
    @DisplayName("userId가 null이면 주문 생성 시 예외가 발생한다")
    void create_nullUserId_throwsInvalidOrderException() {
        List<Order.OrderItemData> items = List.of(
                new Order.OrderItemData("p1", "v1", "노트북", "블랙", 1, 1000000L)
        );

        assertThatThrownBy(() -> Order.create(null, items, ADDRESS, FIXED_CLOCK))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("User ID");
    }

    @Test
    @DisplayName("userId가 blank이면 주문 생성 시 예외가 발생한다")
    void create_blankUserId_throwsInvalidOrderException() {
        List<Order.OrderItemData> items = List.of(
                new Order.OrderItemData("p1", "v1", "노트북", "블랙", 1, 1000000L)
        );

        assertThatThrownBy(() -> Order.create("   ", items, ADDRESS, FIXED_CLOCK))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("User ID");
    }

    @Test
    @DisplayName("shippingAddress가 null이면 주문 생성 시 예외가 발생한다")
    void create_nullShippingAddress_throwsInvalidOrderException() {
        List<Order.OrderItemData> items = List.of(
                new Order.OrderItemData("p1", "v1", "노트북", "블랙", 1, 1000000L)
        );

        assertThatThrownBy(() -> Order.create("user1", items, null, FIXED_CLOCK))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Shipping address");
    }

    @Test
    @DisplayName("items가 비어있으면 주문 생성 시 예외가 발생한다")
    void create_emptyItems_throwsInvalidOrderException() {
        assertThatThrownBy(() -> Order.create("user1", List.of(), ADDRESS, FIXED_CLOCK))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("items가 null이면 주문 생성 시 예외가 발생한다")
    void create_nullItems_throwsInvalidOrderException() {
        assertThatThrownBy(() -> Order.create("user1", null, ADDRESS, FIXED_CLOCK))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("quantity가 0 이하인 항목 생성 시 예외가 발생한다")
    void createItem_zeroQuantity_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                Order.create("user1",
                        List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 0, 1000L)),
                        ADDRESS, FIXED_CLOCK)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PENDING 상태에서 cancel 호출 시 CANCELLED로 전이된다")
    void cancel_pendingOrder_becomeCancelled() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);

        order.cancel(FIXED_CLOCK);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel(Clock) 기본 사유는 OPERATOR 다 (back-compat)")
    void cancel_defaultReason_isOperator() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);

        order.cancel(FIXED_CLOCK);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo(CancelReason.OPERATOR);
    }

    @Test
    @DisplayName("PENDING 에서 cancel(PAYMENT_TIMEOUT) 시 CANCELLED + 사유 PAYMENT_TIMEOUT (stuck-detector 경로)")
    void cancel_paymentTimeoutFromPending_becomesCancelledWithReason() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);

        order.cancel(CancelReason.PAYMENT_TIMEOUT, FIXED_CLOCK);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo(CancelReason.PAYMENT_TIMEOUT);
    }

    @Test
    @DisplayName("CANCELLED 상태에서 cancel 호출 시 예외가 발생한다")
    void cancel_cancelledOrder_throwsOrderCannotBeCancelledException() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.cancel(FIXED_CLOCK);

        assertThatThrownBy(() -> order.cancel(FIXED_CLOCK))
                .isInstanceOf(OrderCannotBeCancelledException.class);
    }

    @Test
    @DisplayName("SHIPPED 상태에서는 cancel이 불가능하다")
    void orderStatus_shipped_isNotCancellable() {
        assertThat(OrderStatus.SHIPPED.isCancellable()).isFalse();
        assertThat(OrderStatus.DELIVERED.isCancellable()).isFalse();
        assertThat(OrderStatus.CANCELLED.isCancellable()).isFalse();
    }

    @Test
    @DisplayName("PENDING, CONFIRMED 상태에서는 cancel이 가능하다")
    void orderStatus_pendingAndConfirmed_isCancellable() {
        assertThat(OrderStatus.PENDING.isCancellable()).isTrue();
        assertThat(OrderStatus.CONFIRMED.isCancellable()).isTrue();
    }

    // ---- Backorder saga (TASK-BE-428) -----------------------------------------

    private static Order pendingOrder() {
        return Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
    }

    @Test
    @DisplayName("BACKORDERED 상태에서는 cancel이 가능하다")
    void orderStatus_backordered_isCancellable() {
        assertThat(OrderStatus.BACKORDERED.isCancellable()).isTrue();
    }

    @Test
    @DisplayName("PENDING 상태에서 markBackordered 호출 시 BACKORDERED로 전이되고 true를 반환한다")
    void markBackordered_pendingOrder_becomesBackorderedAndReturnsTrue() {
        Order order = pendingOrder();

        boolean result = order.markBackordered(FIXED_CLOCK);

        assertThat(result).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.BACKORDERED);
    }

    @Test
    @DisplayName("이미 BACKORDERED 인 주문에 markBackordered 호출 시 멱등 no-op (false 반환)")
    void markBackordered_alreadyBackordered_isIdempotentAndReturnsFalse() {
        Order order = pendingOrder();
        order.markBackordered(FIXED_CLOCK);

        boolean result = order.markBackordered(FIXED_CLOCK);

        assertThat(result).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.BACKORDERED);
    }

    @Test
    @DisplayName("CONFIRMED 인 주문에 늦은 markBackordered 가 와도 예외 없이 no-op (false 반환)")
    void markBackordered_confirmedOrder_isNoOpAndReturnsFalse() {
        Order order = pendingOrder();
        order.confirm(FIXED_CLOCK);

        boolean result = order.markBackordered(FIXED_CLOCK);

        assertThat(result).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("CANCELLED 인 주문에 늦은 markBackordered 가 와도 예외 없이 no-op (false 반환)")
    void markBackordered_cancelledOrder_isNoOpAndReturnsFalse() {
        Order order = pendingOrder();
        order.cancel(FIXED_CLOCK);

        boolean result = order.markBackordered(FIXED_CLOCK);

        assertThat(result).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("markBackordered() 전이 시 Clock.fixed()로 주입한 시간이 updatedAt에 설정된다")
    void markBackordered_withFixedClock_updatesTimestamp() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, Clock.fixed(Instant.parse("2026-03-25T09:00:00Z"), ZoneOffset.UTC));

        Instant backorderTime = Instant.parse("2026-03-25T11:00:00Z");
        order.markBackordered(Clock.fixed(backorderTime, ZoneOffset.UTC));

        assertThat(order.getUpdatedAt()).isEqualTo(backorderTime);
    }

    @Test
    @DisplayName("BACKORDERED 상태에서 confirm 호출 시 CONFIRMED로 전이되고 true를 반환한다 (재입고 재예약)")
    void confirm_backorderedOrder_becomesConfirmedAndReturnsTrue() {
        Order order = pendingOrder();
        order.markBackordered(FIXED_CLOCK);

        boolean result = order.confirm(FIXED_CLOCK);

        assertThat(result).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("BACKORDERED 상태에서 cancel 호출 시 CANCELLED로 전이된다 (운영자 수동 취소)")
    void cancel_backorderedOrder_becomesCancelled() {
        Order order = pendingOrder();
        order.markBackordered(FIXED_CLOCK);

        order.cancel(FIXED_CLOCK);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("PENDING 상태에서 confirm 호출 시 CONFIRMED로 전이되고 true를 반환한다")
    void confirm_pendingOrder_becomesConfirmedAndReturnsTrue() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);

        boolean result = order.confirm(FIXED_CLOCK);

        assertThat(result).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("CONFIRMED 상태에서 confirm 호출 시 멱등 처리되고 false를 반환한다")
    void confirm_alreadyConfirmed_isIdempotentAndReturnsFalse() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.confirm(FIXED_CLOCK);

        boolean result = order.confirm(FIXED_CLOCK);

        assertThat(result).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("CANCELLED 상태에서 confirm 호출 시 예외가 발생한다")
    void confirm_cancelledOrder_throwsInvalidOrderException() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.cancel(FIXED_CLOCK);

        assertThatThrownBy(() -> order.confirm(FIXED_CLOCK))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("결제 완료 시 paymentId와 paidAt이 기록된다")
    void markPaymentCompleted_pendingOrder_recordsPaymentInfo() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        Instant paidAt = Instant.parse("2026-03-24T10:00:00Z");

        order.markPaymentCompleted("pay-123", paidAt, FIXED_CLOCK);

        assertThat(order.getPaymentId()).isEqualTo("pay-123");
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
        assertThat(order.isPaymentCompleted()).isTrue();
    }

    @Test
    @DisplayName("이미 결제 완료된 주문에 다시 호출하면 멱등 처리된다")
    void markPaymentCompleted_alreadyCompleted_isIdempotent() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.markPaymentCompleted("pay-123", Instant.now(), FIXED_CLOCK);

        order.markPaymentCompleted("pay-456", Instant.now(), FIXED_CLOCK);

        assertThat(order.getPaymentId()).isEqualTo("pay-123");
    }

    @Test
    @DisplayName("취소된 주문에 결제 완료 시 예외가 발생한다")
    void markPaymentCompleted_cancelledOrder_throwsInvalidOrderException() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.cancel(FIXED_CLOCK);

        assertThatThrownBy(() -> order.markPaymentCompleted("pay-123", Instant.now(), FIXED_CLOCK))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("결제 완료 전 isPaymentCompleted는 false를 반환한다")
    void isPaymentCompleted_beforePayment_returnsFalse() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);

        assertThat(order.isPaymentCompleted()).isFalse();
    }

    @Test
    @DisplayName("CANCELLED 상태에서 markRefunded 호출 시 refundedAt이 기록된다")
    void markRefunded_cancelledOrder_recordsRefundedAt() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.cancel(FIXED_CLOCK);
        Instant refundedAt = Instant.parse("2026-03-24T12:00:00Z");

        order.markRefunded(refundedAt, FIXED_CLOCK);

        assertThat(order.getRefundedAt()).isEqualTo(refundedAt);
        assertThat(order.isRefunded()).isTrue();
    }

    @Test
    @DisplayName("이미 환불된 주문에 다시 호출하면 멱등 처리된다")
    void markRefunded_alreadyRefunded_isIdempotent() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.cancel(FIXED_CLOCK);
        Instant firstRefundedAt = Instant.parse("2026-03-24T12:00:00Z");
        order.markRefunded(firstRefundedAt, FIXED_CLOCK);

        order.markRefunded(Instant.parse("2026-03-25T12:00:00Z"), FIXED_CLOCK);

        assertThat(order.getRefundedAt()).isEqualTo(firstRefundedAt);
    }

    @Test
    @DisplayName("CANCELLED가 아닌 상태에서 markRefunded 호출 시 예외가 발생한다")
    void markRefunded_pendingOrder_throwsInvalidOrderException() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);

        assertThatThrownBy(() -> order.markRefunded(Instant.now(), FIXED_CLOCK))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("환불 전 isRefunded는 false를 반환한다")
    void isRefunded_beforeRefund_returnsFalse() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);

        assertThat(order.isRefunded()).isFalse();
    }

    @Test
    @DisplayName("Order.create()에서 Clock.fixed()로 주입한 시간이 createdAt/updatedAt에 설정된다")
    void create_withFixedClock_setsCorrectTimestamps() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);

        assertThat(order.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(order.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("cancel() 호출 시 Clock.fixed()로 주입한 시간이 updatedAt에 설정된다")
    void cancel_withFixedClock_updatesTimestamp() {
        Instant createTime = Instant.parse("2026-03-25T09:00:00Z");
        Clock createClock = Clock.fixed(createTime, ZoneOffset.UTC);
        Instant cancelTime = Instant.parse("2026-03-25T10:00:00Z");
        Clock cancelClock = Clock.fixed(cancelTime, ZoneOffset.UTC);

        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, createClock);

        order.cancel(cancelClock);

        assertThat(order.getCreatedAt()).isEqualTo(createTime);
        assertThat(order.getUpdatedAt()).isEqualTo(cancelTime);
    }

    @Test
    @DisplayName("confirm() 호출 시 Clock.fixed()로 주입한 시간이 updatedAt에 설정된다")
    void confirm_withFixedClock_updatesTimestamp() {
        Instant createTime = Instant.parse("2026-03-25T09:00:00Z");
        Clock createClock = Clock.fixed(createTime, ZoneOffset.UTC);
        Instant confirmTime = Instant.parse("2026-03-25T11:00:00Z");
        Clock confirmClock = Clock.fixed(confirmTime, ZoneOffset.UTC);

        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, createClock);

        order.confirm(confirmClock);

        assertThat(order.getUpdatedAt()).isEqualTo(confirmTime);
    }

    @Test
    @DisplayName("markPaymentCompleted() 호출 시 Clock.fixed()로 주입한 시간이 updatedAt에 설정된다")
    void markPaymentCompleted_withFixedClock_updatesTimestamp() {
        Instant paymentTime = Instant.parse("2026-03-25T12:00:00Z");
        Clock paymentClock = Clock.fixed(paymentTime, ZoneOffset.UTC);

        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);

        Instant paidAt = Instant.parse("2026-03-25T11:30:00Z");
        order.markPaymentCompleted("pay-1", paidAt, paymentClock);

        assertThat(order.getUpdatedAt()).isEqualTo(paymentTime);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("markRefunded() 호출 시 Clock.fixed()로 주입한 시간이 updatedAt에 설정된다")
    void markRefunded_withFixedClock_updatesTimestamp() {
        Instant refundTime = Instant.parse("2026-03-25T14:00:00Z");
        Clock refundClock = Clock.fixed(refundTime, ZoneOffset.UTC);

        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.cancel(FIXED_CLOCK);

        Instant refundedAt = Instant.parse("2026-03-25T13:30:00Z");
        order.markRefunded(refundedAt, refundClock);

        assertThat(order.getUpdatedAt()).isEqualTo(refundTime);
        assertThat(order.getRefundedAt()).isEqualTo(refundedAt);
    }

    // ---- PII anonymization cascade (ADR-MONO-037 P3-B) -------------------------

    @Test
    @DisplayName("anonymizePii()는 배송지 PII를 마스킹하고 true를 반환한다")
    void anonymizePii_unmaskedOrder_masksAddressAndReturnsTrue() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 2, 1000L)),
                ADDRESS, FIXED_CLOCK);

        boolean masked = order.anonymizePii(FIXED_CLOCK);

        assertThat(masked).isTrue();
        assertThat(order.getShippingAddress().isAnonymized()).isTrue();
        assertThat(order.getShippingAddress().getRecipient()).isEqualTo(ShippingAddress.ANONYMIZED_TOMBSTONE);
        assertThat(order.getShippingAddress().getPhone()).isEqualTo(ShippingAddress.ANONYMIZED_TOMBSTONE);
    }

    @Test
    @DisplayName("anonymizePii()는 주문 비즈니스 데이터(금액/항목/상태/userId)를 보존한다")
    void anonymizePii_preservesBusinessData() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", "블랙", 2, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.confirm(FIXED_CLOCK);
        String orderIdBefore = order.getOrderId();
        long totalBefore = order.getTotalPrice();

        order.anonymizePii(FIXED_CLOCK);

        assertThat(order.getOrderId()).isEqualTo(orderIdBefore);
        assertThat(order.getUserId()).isEqualTo("user1");
        assertThat(order.getTotalPrice()).isEqualTo(totalBefore);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("anonymizePii()는 멱등적이다 — 이미 익명화된 주문은 false를 반환하고 변이하지 않는다")
    void anonymizePii_alreadyAnonymized_returnsFalseAndNoMutation() {
        Instant createTime = Instant.parse("2026-03-25T09:00:00Z");
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, Clock.fixed(createTime, ZoneOffset.UTC));

        Instant firstMaskTime = Instant.parse("2026-03-25T10:00:00Z");
        order.anonymizePii(Clock.fixed(firstMaskTime, ZoneOffset.UTC));

        boolean secondResult = order.anonymizePii(
                Clock.fixed(Instant.parse("2026-03-26T10:00:00Z"), ZoneOffset.UTC));

        assertThat(secondResult).isFalse();
        // updatedAt 이 두 번째 호출로 바뀌지 않음 (no-op)
        assertThat(order.getUpdatedAt()).isEqualTo(firstMaskTime);
    }

    @Test
    @DisplayName("anonymizePii() 호출 시 Clock.fixed()로 주입한 시간이 updatedAt에 설정된다")
    void anonymizePii_withFixedClock_updatesTimestamp() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, Clock.fixed(Instant.parse("2026-03-25T09:00:00Z"), ZoneOffset.UTC));

        Instant maskTime = Instant.parse("2026-03-25T15:00:00Z");
        order.anonymizePii(Clock.fixed(maskTime, ZoneOffset.UTC));

        assertThat(order.getUpdatedAt()).isEqualTo(maskTime);
    }
}
