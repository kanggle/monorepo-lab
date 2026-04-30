package com.example.membership.domain.event;

import java.util.Map;

/**
 * Pure domain value object carrying an event type and its payload fields.
 * Envelope wrapping and outbox persistence are handled by the application layer.
 */
public record MembershipDomainEvent(String eventType, Map<String, Object> payload) {}
