package com.example.erp.notification.application.port.outbound;

/**
 * Outbound port for id minting (notification + delivery ids). Keeps id
 * generation deterministically substitutable in tests.
 */
public interface IdGeneratorPort {

    /** A new notification id ({@code ntf-...}). */
    String newNotificationId();

    /** A new delivery id ({@code dlv-...}). */
    String newDeliveryId();
}
