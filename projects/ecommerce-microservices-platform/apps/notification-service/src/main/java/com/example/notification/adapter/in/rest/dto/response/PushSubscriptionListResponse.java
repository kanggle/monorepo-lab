package com.example.notification.adapter.in.rest.dto.response;

import com.example.notification.domain.model.PushSubscription;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The authenticated user's Web Push subscriptions (TASK-FE-085), one {@link Item} per
 * browser/device, powering the push-device list UI.
 *
 * <p>Deliberately exposes ONLY the fields the UI needs — {@code id}, {@code endpoint},
 * {@code userAgent}, {@code createdAt}. The client key material ({@code p256dh}/{@code auth})
 * and the {@code tenantId}/{@code userId} are never serialized. {@code createdAt} is a
 * {@link LocalDateTime} rendered as ISO-8601 by the service's configured Jackson JavaTimeModule.
 */
public record PushSubscriptionListResponse(List<Item> subscriptions) {

    public record Item(String id, String endpoint, String userAgent, LocalDateTime createdAt) {}

    public static PushSubscriptionListResponse from(List<PushSubscription> subs) {
        return new PushSubscriptionListResponse(
                subs.stream()
                        .map(s -> new Item(
                                s.getSubscriptionId(),
                                s.getEndpoint(),
                                s.getUserAgent(),
                                s.getCreatedAt()))
                        .toList());
    }
}
