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

    @Test
    void unknownOrBlankMapsToEmpty() {
        assertThat(CarrierStatusMapper.toShippingStatus("LOST")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus("PREPARING")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus("")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus("   ")).isEmpty();
        assertThat(CarrierStatusMapper.toShippingStatus(null)).isEqualTo(Optional.empty());
    }
}
