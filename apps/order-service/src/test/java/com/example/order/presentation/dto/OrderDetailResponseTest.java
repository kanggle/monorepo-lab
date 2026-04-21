package com.example.order.presentation.dto;

import com.example.order.application.dto.OrderDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderDetailResponse 직렬화/역직렬화 테스트")
class OrderDetailResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("OrderDetailResponse가 올바르게 JSON 직렬화된다")
    void serialize_validResponse_producesCorrectJson() throws Exception {
        Instant now = Instant.parse("2026-03-25T10:00:00Z");
        OrderDetailResponse response = new OrderDetailResponse(
                "order-1", "PENDING", 500000,
                List.of(new OrderDetailResponse.OrderItemDetail(
                        "p1", "v1", "노트북", "기본", 1, 500000
                )),
                new OrderDetailResponse.ShippingAddressDetail(
                        "홍길동", "010-1234-5678", "12345", "서울시 강남구", null
                ),
                now, now
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"orderId\":\"order-1\"");
        assertThat(json).contains("\"items\":");
        assertThat(json).contains("\"productName\":\"노트북\"");
        assertThat(json).contains("\"shippingAddress\":");
        assertThat(json).contains("\"recipient\":\"홍길동\"");
    }

    @Test
    @DisplayName("JSON에서 OrderDetailResponse로 역직렬화된다")
    void deserialize_validJson_producesCorrectObject() throws Exception {
        String json = """
                {
                  "orderId": "order-1",
                  "status": "PENDING",
                  "totalPrice": 500000,
                  "items": [{
                    "productId": "p1",
                    "variantId": "v1",
                    "productName": "노트북",
                    "optionName": "기본",
                    "quantity": 1,
                    "unitPrice": 500000
                  }],
                  "shippingAddress": {
                    "recipient": "홍길동",
                    "phone": "010-1234-5678",
                    "zipCode": "12345",
                    "address1": "서울시 강남구",
                    "address2": null
                  },
                  "createdAt": "2026-03-25T10:00:00Z",
                  "updatedAt": "2026-03-25T10:00:00Z"
                }
                """;

        OrderDetailResponse response = objectMapper.readValue(json, OrderDetailResponse.class);

        assertThat(response.orderId()).isEqualTo("order-1");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productName()).isEqualTo("노트북");
        assertThat(response.shippingAddress().recipient()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("OrderDetail에서 OrderDetailResponse로 변환 시 내부 레코드 이름이 올바르다")
    void from_orderDetail_convertsCorrectly() {
        Instant now = Instant.parse("2026-03-25T10:00:00Z");
        OrderDetail detail = new OrderDetail(
                "order-2", "CONFIRMED", 1000000,
                List.of(new OrderDetail.OrderItemDetail(
                        "p2", "v2", "모니터", "27인치", 2, 500000
                )),
                new OrderDetail.ShippingAddressDetail(
                        "김철수", "010-9876-5432", "54321", "부산시 해운대구", "상세주소"
                ),
                now, now
        );

        OrderDetailResponse response = OrderDetailResponse.from(detail);

        assertThat(response.orderId()).isEqualTo("order-2");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0)).isInstanceOf(OrderDetailResponse.OrderItemDetail.class);
        assertThat(response.items().get(0).productName()).isEqualTo("모니터");
        assertThat(response.shippingAddress()).isInstanceOf(OrderDetailResponse.ShippingAddressDetail.class);
        assertThat(response.shippingAddress().recipient()).isEqualTo("김철수");
    }
}
