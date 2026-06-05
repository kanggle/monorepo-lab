package com.example.erp.notification.infrastructure.channel;

import com.example.erp.notification.application.port.outbound.NotificationChannelPort;
import com.example.erp.notification.domain.delivery.DeliveryChannel;
import com.example.erp.notification.domain.notification.Notification;
import org.springframework.stereotype.Component;

/**
 * v1 IN_APP channel: "delivery" is the in-app notification persist itself. By
 * the time {@link #deliver} is called the {@link Notification} row has been
 * saved in the same transaction, so the in-app delivery is immediately
 * successful — the use case marks the {@code NotificationDelivery} DELIVERED
 * with {@code attempt_count = 1}. There is no transient-failure path for an
 * in-app notification (architecture.md § Delivery model).
 */
@Component
public class InAppChannelAdapter implements NotificationChannelPort {

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.IN_APP;
    }

    @Override
    public DeliveryOutcome deliver(Notification notification) {
        // The persist is the delivery — always delivered for IN_APP.
        return DeliveryOutcome.ofDelivered();
    }
}
