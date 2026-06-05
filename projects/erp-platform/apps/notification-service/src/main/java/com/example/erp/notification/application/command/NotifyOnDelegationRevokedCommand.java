package com.example.erp.notification.application.command;

import com.example.erp.notification.domain.render.DelegationRevokedEvent;

import java.util.Objects;

/**
 * Command for the delegation-revoked consume boundary (TASK-ERP-BE-016): the
 * parsed revoke event + the source topic (for dedupe provenance). Parallel to
 * {@link NotifyOnDelegationCommand}; the inbound adapter maps the Kafka envelope
 * to this command (no Kafka / Jackson types reach the application layer).
 */
public record NotifyOnDelegationRevokedCommand(DelegationRevokedEvent event, String topic) {

    public NotifyOnDelegationRevokedCommand {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(topic, "topic");
    }
}
