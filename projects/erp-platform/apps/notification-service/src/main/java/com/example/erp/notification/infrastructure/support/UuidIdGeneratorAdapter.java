package com.example.erp.notification.infrastructure.support;

import com.example.erp.notification.application.port.outbound.IdGeneratorPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** UUID-based id generator ({@code ntf-...} / {@code dlv-...}). */
@Component
public class UuidIdGeneratorAdapter implements IdGeneratorPort {

    @Override
    public String newNotificationId() {
        return "ntf-" + UUID.randomUUID();
    }

    @Override
    public String newDeliveryId() {
        return "dlv-" + UUID.randomUUID();
    }
}
