package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderRepositoryImpl 단위 테스트")
class OrderRepositoryImplTest {

    @InjectMocks
    private OrderRepositoryImpl orderRepository;

    @Mock
    private OrderJpaRepository jpaRepository;

    @Mock
    private OrderJpaMapper mapper;

    private static final Instant NOW = Instant.parse("2026-03-25T10:00:00Z");

    private Order createNewOrder() {
        return Order.reconstitute(
                "order-1", "user-1",
                List.of(OrderItem.reconstitute("item-1", "prod-1", "var-1", "상품A", "옵션1", 2, 10000L)),
                OrderStatus.PENDING, 20000L,
                new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"),
                NOW, NOW, null, null, null, 0, null,
                null  // version null = 신규
        );
    }

    private Order createExistingOrder() {
        return Order.reconstitute(
                "order-1", "user-1",
                List.of(OrderItem.reconstitute("item-1", "prod-1", "var-1", "상품A", "옵션1", 2, 10000L)),
                OrderStatus.CONFIRMED, 20000L,
                new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"),
                NOW, NOW, null, null, null, 0, null,
                1L  // version non-null = 기존
        );
    }

    @Nested
    @DisplayName("save 메서드")
    class Save {

        @Test
        @DisplayName("version이 null인 신규 주문은 INSERT 경로를 탄다")
        void save_newOrder_insertsViaJpaRepositorySave() {
            Order newOrder = createNewOrder();
            OrderJpaEntity entity = OrderJpaEntity.fromDomain(newOrder);
            Order savedDomain = createNewOrder();

            given(mapper.toEntity(newOrder)).willReturn(entity);
            given(jpaRepository.save(entity)).willReturn(entity);
            given(mapper.toDomain(entity)).willReturn(savedDomain);

            Order result = orderRepository.save(newOrder);

            assertThat(result).isEqualTo(savedDomain);
            verify(jpaRepository).save(entity);
            verify(jpaRepository, never()).findById(any());
        }

        @Test
        @DisplayName("version이 non-null인 기존 주문은 findById 후 updateFrom으로 업데이트한다")
        void save_existingOrder_updatesViaDirtyChecking() {
            Order existingOrder = createExistingOrder();
            OrderJpaEntity persistedEntity = OrderJpaEntity.fromDomain(existingOrder);
            Order resultDomain = createExistingOrder();

            given(jpaRepository.findById("order-1")).willReturn(Optional.of(persistedEntity));
            given(mapper.toDomain(persistedEntity)).willReturn(resultDomain);

            Order result = orderRepository.save(existingOrder);

            assertThat(result).isEqualTo(resultDomain);
            verify(jpaRepository).findById("order-1");
            verify(jpaRepository, never()).save(any());
        }

        @Test
        @DisplayName("업데이트 시 findById 결과가 없으면 예외를 던진다")
        void save_existingOrderNotFound_throwsException() {
            Order existingOrder = createExistingOrder();

            given(jpaRepository.findById("order-1")).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderRepository.save(existingOrder))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Order not found for update: order-1");
        }
    }
}
