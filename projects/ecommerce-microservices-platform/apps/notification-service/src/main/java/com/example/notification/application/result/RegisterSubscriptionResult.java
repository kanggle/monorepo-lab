package com.example.notification.application.result;

/**
 * Result of registering a push subscription (TASK-BE-464). {@code created} distinguishes a
 * brand-new subscription (HTTP 201) from a refresh of an existing endpoint's keys (HTTP 200).
 */
public record RegisterSubscriptionResult(String subscriptionId, boolean created) {}
