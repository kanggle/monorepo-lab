package com.example.notification.adapter.in.rest.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * W3C {@code PushSubscription} serialization posted by the browser (TASK-BE-464).
 * {@code expirationTime} is optional/nullable (informational, not persisted).
 */
public record RegisterPushSubscriptionRequest(
        @NotBlank(message = "endpoint is required") String endpoint,
        Long expirationTime,
        @NotNull(message = "keys is required") @Valid Keys keys
) {
    public record Keys(
            @NotBlank(message = "keys.p256dh is required") String p256dh,
            @NotBlank(message = "keys.auth is required") String auth
    ) {}
}
