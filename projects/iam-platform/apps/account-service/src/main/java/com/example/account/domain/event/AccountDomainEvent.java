package com.example.account.domain.event;

import java.util.Map;

/**
 * Pure domain value object carrying an event type and its payload fields.
 * Infrastructure serialization (JSON, outbox write) is handled by the application layer.
 */
public record AccountDomainEvent(String eventType, Map<String, Object> payload) {}
