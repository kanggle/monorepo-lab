package com.example.fanplatform.notification.presentation.dto;

import com.example.fanplatform.notification.domain.notification.Notification;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Inbox notification DTO (the fan inbox surface + {@code
 * platform/contracts/notification-inbox-contract.md} § 1 — the canonical
 * cross-domain envelope this surface conforms to as the P2/fan deliverable of
 * ADR-MONO-043).
 *
 * <p>Follows the {@code @JsonInclude(NON_NULL)} absent-field convention (§14) —
 * {@code readAt} is <b>omitted</b> while the notification is UNREAD, and
 * {@code deepLink} is omitted when {@code null}, never serialized as {@code null}.
 *
 * <h2>ADR-MONO-043 P2 conformance fields (additive)</h2>
 * <ul>
 *   <li>{@code sourceDomain} — contract § 1 normative attribution field; always
 *       the constant {@code "fan"} for this domain-owned surface. The console-bff
 *       aggregator (P3) uses it to label + route each merged item.</li>
 *   <li>{@code deepLink} — contract § 1 optional in-app link; {@code null} here
 *       (fan does not yet derive an in-app link for the bell). Omitted when null.</li>
 * </ul>
 *
 * <p>The pre-existing {@code status} (UNREAD/READ) + {@code membershipId} are
 * non-normative domain extensions (contract § 1.2), preserved unchanged — the
 * normative read signal is the {@code read} boolean (contract § 1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        String id,
        String sourceDomain,
        String type,
        String title,
        String body,
        String deepLink,
        String status,
        boolean read,
        String membershipId,
        Instant createdAt,
        Instant readAt) {

    /** The owning domain for every notification this service serves (contract § 1). */
    public static final String SOURCE_DOMAIN = "fan";

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                SOURCE_DOMAIN,
                n.getType().name(),
                n.getTitle(),
                n.getBody(),
                null, // deepLink — fan does not yet derive an in-app link (future increment)
                n.getStatus().name(),
                n.isRead(),
                n.getMembershipId(),
                n.getCreatedAt(),
                n.getReadAt());
    }
}
