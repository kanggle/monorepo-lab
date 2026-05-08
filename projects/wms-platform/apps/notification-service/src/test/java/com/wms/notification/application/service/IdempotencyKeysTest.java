package com.wms.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdempotencyKeysTest {

    @Test
    void deterministicForSameInputs() {
        UUID eventId = UUID.fromString("11111111-1111-7111-8111-111111111111");
        String a = IdempotencyKeys.forDelivery(eventId, "wms-alerts", "wms-alerts");
        String b = IdempotencyKeys.forDelivery(eventId, "wms-alerts", "wms-alerts");
        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    void differsByChannel() {
        UUID eventId = UUID.randomUUID();
        String a = IdempotencyKeys.forDelivery(eventId, "wms-alerts", "wms-alerts");
        String b = IdempotencyKeys.forDelivery(eventId, "wms-shipping", "wms-shipping");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void differsByEventId() {
        String a = IdempotencyKeys.forDelivery(UUID.randomUUID(), "x", "x");
        String b = IdempotencyKeys.forDelivery(UUID.randomUUID(), "x", "x");
        assertThat(a).isNotEqualTo(b);
    }
}
