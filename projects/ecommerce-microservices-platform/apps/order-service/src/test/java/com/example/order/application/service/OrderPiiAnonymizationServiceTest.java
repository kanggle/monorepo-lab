package com.example.order.application.service;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPiiAnonymizationService 단위 테스트 (ADR-MONO-037 P3-B)")
class OrderPiiAnonymizationServiceTest {

    @InjectMocks
    private OrderPiiAnonymizationService service;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private Clock clock;

    private static final Instant FIXED_NOW = Instant.parse("2026-03-25T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private static final ShippingAddress ADDRESS = new ShippingAddress(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"
    );

    private Order order(String userId) {
        return Order.create(userId,
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
    }

    @Test
    @DisplayName("주문이 있으면 모든 주문의 배송지 PII를 마스킹하고 saveAll로 저장한다")
    void anonymize_withOrders_masksAllAndSaves() {
        String userId = "user-1";
        Order order1 = order(userId);
        Order order2 = order(userId);
        given(orderRepository.findAllByUserIdAcrossTenants(userId)).willReturn(List.of(order1, order2));
        given(clock.instant()).willReturn(FIXED_NOW);

        service.anonymizeOrdersForAccount(userId);

        assertThat(order1.getShippingAddress().isAnonymized()).isTrue();
        assertThat(order2.getShippingAddress().isAnonymized()).isTrue();

        ArgumentCaptor<List<Order>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository, times(1)).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("주문이 없으면 no-op (saveAll 호출 안 함)")
    void anonymize_noOrders_noOp() {
        given(orderRepository.findAllByUserIdAcrossTenants("user-1")).willReturn(Collections.emptyList());

        service.anonymizeOrdersForAccount("user-1");

        verify(orderRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("모든 주문이 이미 익명화돼 있으면 no-op (멱등 재전달)")
    void anonymize_allAlreadyAnonymized_noOp() {
        String userId = "user-1";
        Order alreadyMasked = order(userId);
        alreadyMasked.anonymizePii(FIXED_CLOCK);
        given(orderRepository.findAllByUserIdAcrossTenants(userId)).willReturn(List.of(alreadyMasked));

        service.anonymizeOrdersForAccount(userId);

        verify(orderRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("일부만 미익명화면 변경된 주문만 저장한다")
    void anonymize_partiallyMasked_savesOnlyChanged() {
        String userId = "user-1";
        Order fresh = order(userId);
        Order alreadyMasked = order(userId);
        alreadyMasked.anonymizePii(FIXED_CLOCK);
        given(orderRepository.findAllByUserIdAcrossTenants(userId)).willReturn(List.of(fresh, alreadyMasked));
        given(clock.instant()).willReturn(FIXED_NOW);

        service.anonymizeOrdersForAccount(userId);

        ArgumentCaptor<List<Order>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository, times(1)).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(fresh);
    }

    @Test
    @DisplayName("동일 userId를 2회 처리해도 두 번째는 저장하지 않는다 (멱등성)")
    void anonymize_calledTwice_idempotent() {
        String userId = "user-1";
        Order order = order(userId);
        given(orderRepository.findAllByUserIdAcrossTenants(userId)).willReturn(List.of(order));
        given(clock.instant()).willReturn(FIXED_NOW);

        service.anonymizeOrdersForAccount(userId);
        // 두 번째 호출: 같은 (이제 마스킹된) 주문이 반환됨
        service.anonymizeOrdersForAccount(userId);

        verify(orderRepository, times(1)).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
