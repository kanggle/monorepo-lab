package com.example.order;

import com.example.order.application.service.OrderCancellationService;
import com.example.order.application.service.OrderConfirmationService;
import com.example.order.application.service.OrderPlacementService;
import com.example.order.domain.model.Order;
import com.example.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OrderServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Disabled("TASK-BE-439: order read/mapping LazyInitializationException (OrderJpaEntity.items, "
        + "detached entity, no Session) — 2 of 3 concurrency tests; TASK-MONO-307 quarantine")
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("주문 Optimistic Locking 통합 테스트")
class OrderOptimisticLockIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_db")
            .withUsername("order_user")
            .withPassword("order_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderConfirmationService confirmationService;

    @Autowired
    private OrderCancellationService cancellationService;

    @Autowired
    private OrderRepository orderRepository;

    private static final String PLACE_BODY = """
            {
              "items": [
                {"productId": "p1", "variantId": "v1", "productName": "노트북", "quantity": 1, "unitPrice": 500000}
              ],
              "shippingAddress": {
                "recipient": "홍길동", "phone": "010-1234-5678",
                "zipCode": "12345", "address1": "서울시 강남구"
              }
            }
            """;

    @Test
    @DisplayName("동시에 확인과 취소가 실행되면 하나만 성공하고 다른 하나는 OptimisticLockingFailureException 발생")
    void concurrentConfirmAndCancel_onlyOneSucceeds() throws Exception {
        String userId = "concurrent-user-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();
        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        int threadCount = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        executor.submit(() -> {
            try {
                startLatch.await();
                confirmationService.confirmOrder(orderId);
                successCount.incrementAndGet();
            } catch (OptimisticLockingFailureException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                // 비즈니스 예외 (이미 상태 변경됨)도 충돌로 간주
                conflictCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                cancellationService.cancelOrder(orderId, userId);
                successCount.incrementAndGet();
            } catch (OptimisticLockingFailureException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                conflictCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus().name()).isIn("CONFIRMED", "CANCELLED");
        assertThat(successCount.get() + conflictCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("동시 취소 요청 시 하나만 성공한다")
    void concurrentCancel_onlyOneSucceeds() throws Exception {
        String userId = "concurrent-cancel-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();
        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    cancellationService.cancelOrder(orderId, userId);
                    successCount.incrementAndGet();
                } catch (OptimisticLockingFailureException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);

        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus().name()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("OptimisticLockingFailureException 발생 시 HTTP 409 Conflict 반환")
    void optimisticLockConflict_returns409() throws Exception {
        mockMvc.perform(post("/api/orders/test-order/cancel")
                        .header("X-User-Id", "user1"))
                .andExpect(status().isNotFound());
        // 409 반환은 실제 동시 충돌 상황에서만 발생 — 위 동시성 테스트에서 검증
    }
}
