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
 *   <li>{@code deepLink} — contract § 1 optional in-app link, <b>derived</b> from
 *       the {@code sourceType}/{@code sourceId} pair (TASK-ERP-BE-028): APPROVAL →
 *       {@code /erp/approval?request=<sourceId>}, DELEGATION → {@code /erp/delegation}.
 *       The shell's notification bell navigates to it. Non-null for every current
 *       source, so it is always present in the JSON.</li>
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
     * Derives the in-app console route the shell's notification bell navigates to
     * (contract § 1 {@code deepLink}) from the {@link Notification#source() source}
     * (TASK-ERP-BE-028):
     * <ul>
     *   <li>{@code APPROVAL} → {@code /erp/approval?request=<approvalRequestId>} — the
     *       결재함 route preselects the request (console PC-FE-230).</li>
     *   <li>{@code DELEGATION} → {@code /erp/delegation} — the 위임 route.</li>
     * </ul>
     * The console owns the route SoT; these strings must stay in sync with the
     * console-web route table. The switch is exhaustive over {@code SourceType}, so a
     * future source value fails to compile until its route is defined here (a
     * deliberate route is safer than a silent {@code null}). {@code sourceId} is a
     * system-generated id (safe charset), so it is concatenated verbatim.
     */
    private static String deepLinkFor(Notification n) {
        return switch (n.source().sourceType()) {
            case APPROVAL -> "/erp/approval?request=" + n.source().sourceId();
            case DELEGATION -> "/erp/delegation";
        };
    }
}
