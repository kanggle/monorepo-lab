package com.example.order;

import com.example.order.domain.model.Order;
import com.example.order.domain.repository.OrderRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("주문 생성 통합 테스트")
class OrderPlacementIntegrationTest {

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
    private OrderRepository orderRepository;

    @Test
    @DisplayName("주문 생성 요청 시 DB에 PENDING 상태로 저장된다")
    void placeOrder_validRequest_savedAsPending() throws Exception {
        String body = """
                {
                  "items": [
                    {
                      "productId": "p1",
                      "variantId": "v1",
                      "productName": "노트북",
                      "quantity": 2,
                      "unitPrice": 1000000
                    }
                  ],
                  "shippingAddress": {
                    "recipient": "홍길동",
                    "phone": "010-1234-5678",
                    "zipCode": "12345",
                    "address1": "서울시 강남구"
                  }
                }
                """;

        String responseBody = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "user-integ-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String orderId = responseBody.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");
        Optional<Order> saved = orderRepository.findById(orderId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTotalPrice()).isEqualTo(2000000L);
    }
}
