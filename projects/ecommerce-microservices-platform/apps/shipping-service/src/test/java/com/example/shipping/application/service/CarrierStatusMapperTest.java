package com.example.shipping.application.service;

import com.example.shipping.domain.model.ShippingStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierStatusMapperTest {

    @Test
    void mapsKnownCarrierVocabularies() {
        assertThat(CarrierStatusMapper.toShippingStatus("SHIPPED")).contains(ShippingStatus.SHIPPED);
        assertThat(CarrierStatusMapper.toShippingStatus("dispatched")).contains(ShippingStatus.SHIPPED);
        assertThat(CarrierStatusMapper.toShippingStatus("in-transit")).contains(ShippingStatus.IN_TRANSIT);
        assertThat(CarrierStatusMapper.toShippingStatus("Out Of Delivery".replace("Of", "For")))
                .contains(ShippingStatus.IN_TRANSIT);
        assertThat(CarrierStatusMapper.toShippingStatus("DELIVERED")).contains(ShippingStatus.DELIVERED);
        assertThat(CarrierStatusMapper.toShippingStatus("completed")).contains(ShippingStatus.DELIVERED);
    }

    /**
     * The aggregator (ADR-007 D2) normalises every carrier into its own unified status scheme;
     * the Korean unified tokens must land on the same four-state domain. Separators/case are
     * tolerated (Hangul is unaffected by the ASCII upper-casing).
     */
    @Test
    void mapsAggregatorUnifiedKoreanCodes() {
        assertThat(CarrierStatusMapper.toShippingStatus("집화완료")).contains(ShippingStatus.SHIPPED);
        assertThat(CarrierStatusMapper.toShippingStatus("상품인수")).contains(ShippingStatus.SHIPPED);
        assertThat(CarrierStatusMapper.toShippingStatus("이동중")).contains(ShippingStatus.IN_TRANSIT);
        assertThat(CarrierStatusMapper.toShippingStatus("간선상차")).contains(ShippingStatus.IN_TRANSIT);
        assertThat(CarrierStatusMapper.toShippingStatus("배송출발")).contains(ShippingStatus.IN_TRANSIT);
        assertThat(CarrierStatusMapper.toShippingStatus("배송중")).contains(ShippingStatus.IN_TRANSIT);
        assertThat(CarrierStatusMapper.toShippingStatus("배송완료")).contains(ShippingStatus.DELIVERED);
        assertThat(CarrierStatusMapper.toShippingStatus(" 배달완료 ")).contains(ShippingStatus.DELIVERED);
    }

    /**
     * Delivery Tracker {@code TrackEventStatusCode} unified scheme (TASK-BE-364 /
     * external-integrations.md § 1.4). The normaliser upper-cases + maps {@code -}/space → {@code _},
     * so the plain enum names map directly.
     */
    @Test
    void mapsDeliveryTrackerUnifiedCodes() {
        assertThat(CarrierStatusMapper.toShippingStatus("AT_PICKUP")).contains(ShippingStatus.SHIPPED);
        assertThat(CarrierStatusMapper.toShippingStatus("IN_TRANSIT")).contains(ShippingStatus.IN_TRANSIT);
        assertThat(CarrierStatusMapper.toShippingStatus("OUT_FOR_DELIVERY")).contains(ShippingStatus.IN_TRANSIT);
        assertThat(CarrierStatusMapper.toShippingStatus("AVAILABLE_FOR_PICKUP")).contains(ShippingStatus.IN_TRANSIT);
        assertThat(CarrierStatusMapper.toShippingStatus("DELIVERED")).contains(ShippingStatus.DELIVERED);
    }

    /**
     * Delivery Tracker codes that must stay unmapped → empty (forward-only / best-effort; § 1.4):
     * waybill-registered, failed attempt, and the explicit error/unknown trio.
     */
    @Test
    void deliveryTrackerUnmappedCodesMapToEmpty() {
        assertThat(CarrierStatusMapper.toShippingStatus("INFORMATION_RECEIVED")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus("ATTEMPT_FAIL")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus("EXCEPTION")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus("UNKNOWN")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus("NOT_FOUND")).isEmpty();
    }

    @Test
    void unknownOrBlankMapsToEmpty() {
        assertThat(CarrierStatusMapper.toShippingStatus("LOST")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus("PREPARING")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus("통관보류")).isEmpty(); // unmapped aggregator code
        assertThat(CarrierStatusMapper.toShippingStatus("반품")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus("")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus("   ")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus(null)).isEqualTo(Optional.empty());
    }
}
