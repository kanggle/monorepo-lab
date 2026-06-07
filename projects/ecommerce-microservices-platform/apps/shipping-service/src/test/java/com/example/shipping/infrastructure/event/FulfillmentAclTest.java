package com.example.shipping.infrastructure.event;

import com.example.shipping.infrastructure.config.FulfillmentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FulfillmentAcl 단위 테스트")
class FulfillmentAclTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-08T10:00:00Z"), ZoneOffset.UTC);

    private OrderConfirmedEvent.OrderConfirmedPayload order() {
        return new OrderConfirmedEvent.OrderConfirmedPayload(
                "order-1", "user-1", "2026-06-08T10:00:00Z",
                List.of(new OrderConfirmedEvent.Line("v1", "p1", "v1", 2),
                        new OrderConfirmedEvent.Line("v2", "p2", "v2", 1)),
                new OrderConfirmedEvent.ShippingAddress("홍길동", "서울시 강남구 101호", "010-1234-5678"));
    }

    private FulfillmentAcl acl(boolean requireMapping, Map<String, String> skuMap) {
        FulfillmentProperties props = new FulfillmentProperties(true, "WH-MAIN", requireMapping, skuMap);
        return new FulfillmentAcl(props, FIXED_CLOCK);
    }

    @Test
    @DisplayName("camelCase 봉투 + wms-shaped payload를 빌드한다 (identity SKU)")
    void toFulfillmentRequested_buildsWmsEnvelope() {
        FulfillmentRequestedMessage msg = acl(false, Map.of()).toFulfillmentRequested(order());

        assertThat(msg.eventType()).isEqualTo("ecommerce.fulfillment.requested");
        assertThat(msg.aggregateType()).isEqualTo("fulfillment");
        assertThat(msg.aggregateId()).isEqualTo("order-1");
        assertThat(msg.occurredAt()).isEqualTo("2026-06-08T10:00:00Z");

        FulfillmentRequestedMessage.Payload p = msg.payload();
        assertThat(p.orderNo()).isEqualTo("order-1");
        assertThat(p.customerPartnerCode()).isEqualTo("ECOMMERCE-STORE");
        assertThat(p.warehouseCode()).isEqualTo("WH-MAIN");
        assertThat(p.requiredShipDate()).isNull();
        assertThat(p.shipTo().recipientName()).isEqualTo("홍길동");
        assertThat(p.shipTo().address()).isEqualTo("서울시 강남구 101호");
        assertThat(p.lines()).hasSize(2);
        assertThat(p.lines().get(0).lineNo()).isEqualTo(1);
        assertThat(p.lines().get(0).skuCode()).isEqualTo("v1"); // identity
        assertThat(p.lines().get(0).qtyOrdered()).isEqualTo(2);
        assertThat(p.lines().get(0).lotNo()).isNull();
        assertThat(p.lines().get(1).lineNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("SKU 매핑이 있으면 wms SKU 코드로 변환한다")
    void toFulfillmentRequested_appliesSkuMap() {
        FulfillmentRequestedMessage msg = acl(false, Map.of("v1", "WMS-SKU-A"))
                .toFulfillmentRequested(order());

        assertThat(msg.payload().lines().get(0).skuCode()).isEqualTo("WMS-SKU-A");
        assertThat(msg.payload().lines().get(1).skuCode()).isEqualTo("v2"); // unmapped → identity
    }

    @Test
    @DisplayName("require-sku-mapping=true에서 미매핑 SKU면 UnmappedSkuException")
    void toFulfillmentRequested_requireMappingAndMissing_throws() {
        assertThatThrownBy(() -> acl(true, Map.of("v1", "WMS-SKU-A")).toFulfillmentRequested(order()))
                .isInstanceOf(UnmappedSkuException.class)
                .hasMessageContaining("v2");
    }

    @Test
    @DisplayName("shippingAddress가 null이면 shipTo=null")
    void toFulfillmentRequested_nullAddress_shipToNull() {
        OrderConfirmedEvent.OrderConfirmedPayload order = new OrderConfirmedEvent.OrderConfirmedPayload(
                "order-2", "user-2", "2026-06-08T10:00:00Z",
                List.of(new OrderConfirmedEvent.Line("v1", "p1", "v1", 1)), null);

        FulfillmentRequestedMessage msg = acl(false, Map.of()).toFulfillmentRequested(order);

        assertThat(msg.payload().shipTo()).isNull();
    }

    @Test
    @DisplayName("lines가 null이면 빈 lines로 처리한다")
    void toFulfillmentRequested_nullLines_emptyLines() {
        OrderConfirmedEvent.OrderConfirmedPayload order = new OrderConfirmedEvent.OrderConfirmedPayload(
                "order-3", "user-3", "2026-06-08T10:00:00Z", null, null);

        FulfillmentRequestedMessage msg = acl(false, Map.of()).toFulfillmentRequested(order);

        assertThat(msg.payload().lines()).isEmpty();
    }
}
