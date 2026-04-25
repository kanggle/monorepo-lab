package com.example.promotion;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("쿠폰 발급 및 적용 통합 테스트")
class CouponIssuanceIntegrationTest {

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

    @Test
    @DisplayName("쿠폰 발급 후 사용자가 조회하고 적용할 수 있다")
    void couponIssuanceAndApply_fullFlow() throws Exception {
        String userId = "user-integ-" + System.nanoTime();

        // Create promotion
        MvcResult createResult = mockMvc.perform(post("/api/promotions")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "쿠폰 테스트 프로모션",
                                  "description": "쿠폰 발급 테스트",
                                  "discountType": "FIXED",
                                  "discountValue": 5000,
                                  "maxDiscountAmount": 0,
                                  "maxIssuanceCount": 100,
                                  "startDate": "2026-01-01T00:00:00Z",
                                  "endDate": "2026-12-31T23:59:59Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String promotionId = extractField(createResult.getResponse().getContentAsString(), "promotionId");

        // Issue coupons
        mockMvc.perform(post("/api/promotions/" + promotionId + "/coupons/issue")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\": [\"" + userId + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issuedCount").value(1));

        // User queries coupons
        MvcResult couponsResult = mockMvc.perform(get("/api/coupons/me")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].promotionName").value("쿠폰 테스트 프로모션"))
                .andExpect(jsonPath("$.content[0].status").value("ISSUED"))
                .andReturn();

        String couponId = extractField(couponsResult.getResponse().getContentAsString(), "couponId");

        // Apply coupon
        mockMvc.perform(post("/api/coupons/" + couponId + "/apply")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "order-test-1",
                                  "orderAmount": 30000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountAmount").value(5000))
                .andExpect(jsonPath("$.finalAmount").value(25000));

        // Verify coupon is now USED
        mockMvc.perform(get("/api/coupons/me")
                        .header("X-User-Id", userId)
                        .param("status", "USED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("USED"));

        // Apply same coupon again should fail
        mockMvc.perform(post("/api/coupons/" + couponId + "/apply")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "order-test-2",
                                  "orderAmount": 20000
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COUPON_ALREADY_USED"));
    }

    @Test
    @DisplayName("발급된 쿠폰이 있는 프로모션은 삭제할 수 없다")
    void deletePromotion_withIssuedCoupons_returns422() throws Exception {
        // Create promotion
        MvcResult createResult = mockMvc.perform(post("/api/promotions")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "삭제 테스트",
                                  "discountType": "FIXED",
                                  "discountValue": 1000,
                                  "maxDiscountAmount": 0,
                                  "maxIssuanceCount": 100,
                                  "startDate": "2026-01-01T00:00:00Z",
                                  "endDate": "2026-12-31T23:59:59Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String promotionId = extractField(createResult.getResponse().getContentAsString(), "promotionId");

        // Issue coupons
        mockMvc.perform(post("/api/promotions/" + promotionId + "/coupons/issue")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\": [\"user-del-test\"]}"))
                .andExpect(status().isCreated());

        // Try to delete
        mockMvc.perform(delete("/api/promotions/" + promotionId)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROMOTION_HAS_ISSUED_COUPONS"));
    }

    private String extractField(String json, String field) {
        return json.replaceAll(".*\"" + field + "\":\"([^\"]+)\".*", "$1");
    }
}
