package com.example.payment;

import com.example.payment.domain.model.PaymentStatus;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.adapter.in.event.OrderPlacedEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("결제 처리 통합 테스트")
class PaymentProcessingIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("payment_user")
            .withPassword("payment_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderPlacedEventConsumer orderPlacedEventConsumer;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String buildOrderPlacedJson(String orderId, String userId, long totalPrice) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "OrderPlaced",
                "occurredAt", "2026-03-23T00:00:00",
                "source", "order-service",
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "totalPrice", totalPrice,
                        "items", List.of()
                )
        ));
    }

    @Test
    @DisplayName("OrderPlaced 이벤트 발행 시 Payment가 PENDING 상태로 저장된다")
    void orderPlaced_createsPendingPayment() throws Exception {
        String orderId = "order-" + System.nanoTime();
        String userId = "user-" + System.nanoTime();

        orderPlacedEventConsumer.onMessage(buildOrderPlacedJson(orderId, userId, 50000L));

        var payment = paymentRepository.findByOrderId(orderId);
        assertThat(payment).isPresent();
        assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.get().getPaidAt()).isNull();
        assertThat(payment.get().getAmount()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("동일 orderId로 OrderPlaced 이벤트 중복 발행 시 Payment가 1개만 생성된다 (멱등)")
    void orderPlaced_duplicate_isIdempotent() throws Exception {
        String orderId = "order-dup-" + System.nanoTime();
        String userId = "user-1";

        String eventJson = buildOrderPlacedJson(orderId, userId, 30000L);

        orderPlacedEventConsumer.onMessage(eventJson);
        orderPlacedEventConsumer.onMessage(eventJson);

        assertThat(paymentRepository.findByOrderId(orderId)).isPresent();
    }
}
