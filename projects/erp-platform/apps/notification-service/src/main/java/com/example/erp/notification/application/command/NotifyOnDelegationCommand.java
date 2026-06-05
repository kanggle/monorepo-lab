package com.example.erp.notification.application.command;

import com.example.erp.notification.domain.render.DelegationEvent;

import java.util.Objects;

/**
 * Command for the delegation consume boundary (TASK-ERP-BE-014): the parsed
 * delegation event + the source topic (for dedupe provenance). Parallel to
 * {@link NotifyOnApprovalCommand}; the inbound adapter maps the Kafka envelope to
 * this command (no Kafka / Jackson types reach the application layer).
 */
public record NotifyOnDelegationCommand(DelegationEvent event, String topic) {

    public NotifyOnDelegationCommand {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(topic, "topic");
    }
}
