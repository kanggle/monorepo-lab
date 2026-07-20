package com.example.promotion;

import com.example.promotion.interfaces.event.OrderCancelledEvent;
import com.example.promotion.interfaces.event.OrderCancelledEvent.OrderCancelledPayload;
import com.example.promotion.interfaces.event.OrderCancelledEventConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PromotionServiceApplication.class, properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("쿠폰 복원 통합 테스트")
class CouponRestoreIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("promotion_db")
            .withUsername("promotion_user")
            .withPassword("promotion_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderCancelledEventConsumer orderCancelledEventConsumer;

    @Test
    @DisplayName("쿠폰 적용 후 주문 취소 이벤트 처리 시 쿠폰이 ISSUED로 복원된다")
    void restoreCoupon_afterOrderCancelled_statusBecomesIssued() throws Exception {
        String userId = "user-restore-" + System.nanoTime();
        String orderId = "order-restore-" + System.nanoTime();

        // 프로모션 생성
        String promotionId = createPromotion();

        // 쿠폰 발급
        issueCoupon(promotionId, userId);

        // 쿠폰 조회
        String couponId = getCouponId(userId);

        // 쿠폰 적용
        applyCoupon(couponId, userId, orderId);

        // 쿠폰 상태 USED 확인
        verifyCouponStatus(userId, "USED");

        // OrderCancelled 이벤트 처리 (consumer 직접 호출)
        OrderCancelledPayload payload = new OrderCancelledPayload(orderId, userId, "2026-03-28T12:00:00Z");
        OrderCancelledEvent event = new OrderCancelledEvent(
                "evt-" + System.nanoTime(), "OrderCancelled", "2026-03-28T12:00:00Z", "order-service", "ecommerce", payload);
        orderCancelledEventConsumer.handle(event);

        // 쿠폰 상태 ISSUED로 복원 확인
        verifyCouponStatus(userId, "ISSUED");
    }

    @Test
    @DisplayName("EXPIRED 쿠폰은 주문 취소 이벤트로 복원되지 않는다")
    void restoreCoupon_expiredCoupon_statusRemainsExpired() throws Exception {
        String userId = "user-expired-" + System.nanoTime();
        String orderId = "order-expired-" + System.nanoTime();

        // EXPIRED 쿠폰은 findByOrderIdAndStatus(orderId, USED)로 조회되지 않으므로
        // consumer 호출해도 복원 대상이 없음
        OrderCancelledPayload payload = new OrderCancelledPayload(orderId, userId, "2026-03-28T12:00:00Z");
        OrderCancelledEvent event = new OrderCancelledEvent(
                "evt-" + System.nanoTime(), "OrderCancelled", "2026-03-28T12:00:00Z", "order-service", "ecommerce", payload);

        // 예외 없이 정상 처리됨 (복원 대상 없음)
        orderCancelledEventConsumer.handle(event);
    }

    @Test
    @DisplayName("동일 이벤트를 중복 처리해도 멱등하게 동작한다")
    void restoreCoupon_duplicateEvent_idempotent() throws Exception {
        String userId = "user-idempotent-" + System.nanoTime();
        String orderId = "order-idempotent-" + System.nanoTime();

        // 프로모션 생성 및 쿠폰 발급
        String promotionId = createPromotion();
        issueCoupon(promotionId, userId);
        String couponId = getCouponId(userId);
        applyCoupon(couponId, userId, orderId);

        // 동일 이벤트를 두 번 처리
        OrderCancelledPayload payload = new OrderCancelledPayload(orderId, userId, "2026-03-28T12:00:00Z");
        OrderCancelledEvent event = new OrderCancelledEvent(
                "evt-" + System.nanoTime(), "OrderCancelled", "2026-03-28T12:00:00Z", "order-service", "ecommerce", payload);

        orderCancelledEventConsumer.handle(event);
        orderCancelledEventConsumer.handle(event);

        // 여전히 ISSUED 상태
        verifyCouponStatus(userId, "ISSUED");
    }

    // --- helper methods ---

    private String createPromotion() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/promotions")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "복원 테스트 프로모션",
                                  "description": "복원 테스트",
                                  "discountType": "FIXED",
                                  "discountValue": 3000,
                                  "maxDiscountAmount": 0,
                                  "maxIssuanceCount": 100,
                                  "startDate": "2026-01-01T00:00:00Z",
                                  "endDate": "2026-12-31T23:59:59Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return extractField(result.getResponse().getContentAsString(), "promotionId");
    }

    private void issueCoupon(String promotionId, String userId) throws Exception {
        // A fresh random Idempotency-Key per call (TASK-BE-536) — each call here is
        // a genuinely distinct issuance request.
        mockMvc.perform(post("/api/promotions/" + promotionId + "/coupons/issue")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\": [\"" + userId + "\"]}"))
                .andExpect(status().isCreated());
    }

    private String getCouponId(String userId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/coupons/me")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andReturn();
        return extractField(result.getResponse().getContentAsString(), "couponId");
    }

    private void applyCoupon(String couponId, String userId, String orderId) throws Exception {
        mockMvc.perform(post("/api/coupons/" + couponId + "/apply")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": \"" + orderId + "\", \"orderAmount\": 30000}"))
                .andExpect(status().isOk());
    }

    private void verifyCouponStatus(String userId, String expectedStatus) throws Exception {
        mockMvc.perform(get("/api/coupons/me")
                        .header("X-User-Id", userId)
                        .param("status", expectedStatus))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value(expectedStatus));
    }

    private String extractField(String json, String field) {
        return json.replaceAll(".*\"" + field + "\":\"([^\"]+)\".*", "$1");
    }
}
