package com.example.erp.notification.application.command;

import com.example.erp.notification.domain.render.ApprovalEvent;

import java.util.Objects;

/**
 * Command for the consume boundary: the parsed approval event + the source
 * topic (for dedupe provenance). The inbound adapter maps the Kafka envelope to
 * this command (no Kafka / Jackson types reach the application layer).
 */
public record NotifyOnApprovalCommand(ApprovalEvent event, String topic) {

    public NotifyOnApprovalCommand {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(topic, "topic");
    }
}
