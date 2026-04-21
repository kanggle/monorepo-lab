package com.example.order.infrastructure.event;

import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.event.OrderPlacedEvent;
import com.example.order.application.port.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class StandaloneOrderEventPublisher implements OrderEventPublisher {

    private final RestClient restClient;

    @Override
    public void publishOrderPlaced(OrderPlacedEvent event) {
        OrderPlacedEvent.Payload payload = event.payload();
        try {
            restClient.post()
                    .uri("/api/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "orderId", payload.orderId(),
                            "userId", payload.userId(),
                            "amount", payload.totalPrice()
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[standalone] Payment created for orderId={}", payload.orderId());
        } catch (Exception e) {
            log.error("[standalone] Failed to create payment for orderId={}: {}",
                    payload.orderId(), e.getMessage());
            throw new IllegalStateException(
                    "결제 레코드 생성 실패로 주문을 완료할 수 없습니다. payment-service 상태를 확인하세요. orderId="
                            + payload.orderId(), e);
        }
    }

    @Override
    public void publishOrderCancelled(OrderCancelledEvent event) {
        log.debug("[standalone] OrderCancelled event (no-op): {}", event.payload().orderId());
    }
}
