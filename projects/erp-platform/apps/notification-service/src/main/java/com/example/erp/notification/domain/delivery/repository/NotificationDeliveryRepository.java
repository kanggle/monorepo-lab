package com.example.erp.notification.domain.delivery.repository;

import com.example.erp.notification.domain.delivery.NotificationDelivery;

/** Outbound port for the {@code notification_delivery} store. */
public interface NotificationDeliveryRepository {

    void save(NotificationDelivery delivery);
}
