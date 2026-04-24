package com.example.promotion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("프로모션 CRUD 통합 테스트")
class PromotionCrudIntegrationTest {

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
    @DisplayName("프로모션 생성, 조회, 수정, 삭제가 정상 동작한다")
    void promotionCrud_fullCycle_works() throws Exception {
        // Create
        MvcResult createResult = mockMvc.perform(post("/api/promotions")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "통합테스트 프로모션",
                                  "description": "통합 테스트용",
                                  "discountType": "FIXED",
                                  "discountValue": 3000,
                                  "maxDiscountAmount": 0,
                                  "maxIssuanceCount": 50,
                                  "startDate": "2026-01-01T00:00:00Z",
                                  "endDate": "2026-12-31T23:59:59Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.promotionId").isNotEmpty())
                .andReturn();

        String promotionId = extractField(createResult.getResponse().getContentAsString(), "promotionId");

        // Get detail
        mockMvc.perform(get("/api/promotions/" + promotionId)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("통합테스트 프로모션"))
                .andExpect(jsonPath("$.discountValue").value(3000));

        // Update
        mockMvc.perform(put("/api/promotions/" + promotionId)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "수정된 프로모션",
                                  "description": "수정됨",
                                  "discountType": "PERCENTAGE",
                                  "discountValue": 10,
                                  "maxDiscountAmount": 5000,
                                  "maxIssuanceCount": 100,
                                  "startDate": "2026-01-01T00:00:00Z",
                                  "endDate": "2026-12-31T23:59:59Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotionId").value(promotionId));

        // Verify updated
        mockMvc.perform(get("/api/promotions/" + promotionId)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정된 프로모션"))
                .andExpect(jsonPath("$.discountType").value("PERCENTAGE"));

        // List
        mockMvc.perform(get("/api/promotions")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").isNumber());

        // Delete
        mockMvc.perform(delete("/api/promotions/" + promotionId)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/promotions/" + promotionId)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ADMIN이 아닌 역할로 요청 시 403이 반환된다")
    void createPromotion_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/promotions")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "테스트",
                                  "discountType": "FIXED",
                                  "discountValue": 1000,
                                  "maxIssuanceCount": 10,
                                  "startDate": "2026-01-01T00:00:00Z",
                                  "endDate": "2026-12-31T23:59:59Z"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private String extractField(String json, String field) {
        return json.replaceAll(".*\"" + field + "\":\"([^\"]+)\".*", "$1");
    }
}
