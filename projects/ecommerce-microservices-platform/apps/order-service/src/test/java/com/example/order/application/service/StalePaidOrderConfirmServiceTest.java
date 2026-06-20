package com.example.order.application.service;

import com.example.order.application.dto.ConfirmPaidStaleResult;
import com.example.order.application.service.StalePaidOrderConfirmHandler.Outcome;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("StalePaidOrderConfirmService unit tests")
class StalePaidOrderConfirmServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-20T10:00:00Z");
    private static final int OLDER_THAN_MINUTES = 30;
    private static final int LIMIT = 200;

    @Mock private OrderRepository orderRepository;
    @Mock private StalePaidOrderConfirmHandler handler;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private StalePaidOrderConfirmService service() {
        return new StalePaidOrderConfirmService(orderRepository, handler, clock);
    }

    @Test
    @DisplayName("빈 결과 — scanned/confirmed/skipped 모두 0, handler 미호출")
    void emptyResult_zeroTally_noHandlerCall() {
        given(orderRepository.findStalePaidUnconfirmed(any(), eq(LIMIT))).willReturn(List.of());

        ConfirmPaidStaleResult result = service().sweep(OLDER_THAN_MINUTES, LIMIT);

        assertThat(result.scanned()).isZero();
        assertThat(result.confirmed()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.confirmedOrderIds()).isEmpty();
        verify(handler, never()).confirmIfStillPending(any());
    }

    @Test
    @DisplayName("cutoff = now - olderThanMinutes 로 predicate 를 질의한다")
    void cutoff_isComputedFromClock() {
        Instant expectedCutoff = NOW.minusSeconds(OLDER_THAN_MINUTES * 60L);
        given(orderRepository.findStalePaidUnconfirmed(eq(expectedCutoff), eq(LIMIT)))
                .willReturn(List.of());

        service().sweep(OLDER_THAN_MINUTES, LIMIT);

        verify(orderRepository).findStalePaidUnconfirmed(expectedCutoff, LIMIT);
    }

    @Test
    @DisplayName("CONFIRMED outcome 은 confirmed 로, SKIPPED outcome 은 skipped 로 집계된다")
    void mixedOutcomes_tallied() {
        Order a = candidate("order-a");
        Order b = candidate("order-b");
        Order c = candidate("order-c");
        given(orderRepository.findStalePaidUnconfirmed(any(), eq(LIMIT)))
                .willReturn(List.of(a, b, c));
        given(handler.confirmIfStillPending("order-a")).willReturn(Outcome.CONFIRMED);
        given(handler.confirmIfStillPending("order-b")).willReturn(Outcome.SKIPPED);
        given(handler.confirmIfStillPending("order-c")).willReturn(Outcome.CONFIRMED);

        ConfirmPaidStaleResult result = service().sweep(OLDER_THAN_MINUTES, LIMIT);

        assertThat(result.scanned()).isEqualTo(3);
        assertThat(result.confirmed()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.confirmedOrderIds()).containsExactly("order-a", "order-c");
        // scanned == confirmed + skipped on a clean run
        assertThat(result.scanned()).isEqualTo(result.confirmed() + result.skipped());
    }

    @Test
    @DisplayName("per-order 예외는 격리된다 — 해당 주문만 confirmed/skipped 에서 제외, 다음 주문 계속 처리")
    void perOrderFailure_isolated_partialTally() {
        Order a = candidate("order-a");
        Order b = candidate("order-b");
        Order c = candidate("order-c");
        given(orderRepository.findStalePaidUnconfirmed(any(), eq(LIMIT)))
                .willReturn(List.of(a, b, c));
        given(handler.confirmIfStillPending("order-a")).willReturn(Outcome.CONFIRMED);
        willThrow(new RuntimeException("optimistic-lock"))
                .given(handler).confirmIfStillPending("order-b");
        given(handler.confirmIfStillPending("order-c")).willReturn(Outcome.CONFIRMED);

        ConfirmPaidStaleResult result = service().sweep(OLDER_THAN_MINUTES, LIMIT);

        assertThat(result.scanned()).isEqualTo(3);
        assertThat(result.confirmed()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        // the failed order is excluded from both tallies → partial run
        assertThat(result.confirmed() + result.skipped()).isLessThan(result.scanned());
        assertThat(result.confirmedOrderIds()).containsExactly("order-a", "order-c");
        verify(handler).confirmIfStillPending("order-c");
    }

    @Test
    @DisplayName("limit 은 그대로 repository 에 전달된다 (server-side LIMIT)")
    void limit_passedThroughToRepository() {
        given(orderRepository.findStalePaidUnconfirmed(any(), eq(5))).willReturn(List.of());

        service().sweep(OLDER_THAN_MINUTES, 5);

        verify(orderRepository).findStalePaidUnconfirmed(any(), eq(5));
        verify(handler, never()).confirmIfStillPending(any());
    }

    private static Order candidate(String orderId) {
        return Order.reconstitute(
                orderId, "user-1", List.of(),
                OrderStatus.PENDING, 0L,
                new ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시", null),
                NOW.minusSeconds(7200), NOW.minusSeconds(7200),
                "pay-1", NOW.minusSeconds(7000), null, 0, null, 1L);
    }
}
