package com.example.erp.notification.presentation.dto;

import com.example.erp.notification.domain.notification.Notification;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Inbox notification DTO (notification-api.md § Common shape +
 * {@code platform/contracts/notification-inbox-contract.md} § 1 — the canonical
 * cross-domain envelope this surface conforms to as the P2/erp deliverable of
 * ADR-MONO-043).
 *
 * <p>Follows the {@code @JsonInclude(NON_NULL)} absent-field convention —
 * {@code readAt} is <b>omitted</b> while {@code read == false}, and
 * {@code deepLink} is omitted when {@code null}, never serialized as {@code null}.
 *
 * <h2>ADR-MONO-043 P2 conformance fields (additive)</h2>
 * <ul>
 *   <li>{@code sourceDomain} — contract § 1 normative attribution field; always
 *       the constant {@code "erp"} for this domain-owned surface. The console-bff
 *       aggregator (P3) uses it to label + route each merged item.</li>
 *   <li>{@code deepLink} — contract § 1 optional in-app link; {@code null} here
 *       (erp does not yet derive a console approval route for the bell — see the
 *       conformance note). Omitted from the JSON when {@code null}.</li>
 * </ul>
 *
 * <p>The pre-existing {@code sourceType} / {@code sourceId} pair is a non-normative
 * domain extension (contract § 1.2) and is preserved unchanged — it is ignored by
 * the aggregator/shell but kept for the erp-native inbox client.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        String id,
        String sourceDomain,
        String type,
        String title,
        String body,
        String deepLink,
        String sourceType,
        String sourceId,
        boolean read,
        Instant createdAt,
        Instant readAt) {

    /** The owning domain for every notification this service serves (contract § 1). */
    public static final String SOURCE_DOMAIN = "erp";

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.id(),
                SOURCE_DOMAIN,
                n.type().name(),
                n.title(),
                n.body(),
                deepLinkFor(n),
                n.source().sourceType().name(),
                n.source().sourceId(),
                n.read(),
                n.createdAt(),
                n.readAt().orElse(null));
    }

    /**
     * Optional in-app link (contract § 1, nullable). erp does not currently expose a
     * console route for the notification bell to navigate to, so this is always
     * {@code null} (Jackson NON_NULL omits it). Deriving a route from the
     * {@code APPROVAL}/{@code DELEGATION} source would be a behavioural addition
     * beyond this net-zero conformance refactor; left as a deliberate no-op so the
     * P2 change stays strictly additive. A future task may map
     * {@code sourceType}/{@code sourceId} to a console route here.
     */
    private static String deepLinkFor(Notification n) {
        return null;
    }
}
