package com.example.erp.notification.domain.delivery;

/**
 * Delivery channel. v1 exercises {@link #IN_APP} only; {@link #SLACK} /
 * {@link #SMTP} are reserved for the v2 external-channel increment
 * (architecture.md § Data Model — the enum is forward-declared so the v2 path
 * needs no schema migration).
 */
public enum DeliveryChannel {
    IN_APP,
    SLACK,
    SMTP
}
