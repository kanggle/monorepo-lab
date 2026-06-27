package com.example.platform.notification.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * The canonical inbox item shape returned by every conforming per-domain
 * notification surface, and the single model the console-bff aggregator parses
 * + merges across domains.
 *
 * <p>This is the D3 envelope of {@code platform/contracts/notification-inbox-contract.md}
 * § 1, lifted into shared code so the aggregator (D2) and any future client parse
 * one model. Field names are normative; types match the contract table exactly:
 *
 * <ul>
 *   <li>{@code id}           — stable opaque identifier (UUID recommended), unique within the domain.</li>
 *   <li>{@code sourceDomain} — owning domain (e.g. {@code "erp"}, {@code "wms"}); the aggregator
 *       uses it for attribution. A single-domain surface MAY omit it (aggregator injects from the
 *       call target); the canonical shape includes it so a merged feed is self-describing.</li>
 *   <li>{@code type}         — domain notification-type discriminator; opaque to the aggregator/shell.</li>
 *   <li>{@code title}        — short human-readable headline.</li>
 *   <li>{@code body}         — human-readable detail (plain text).</li>
 *   <li>{@code deepLink}     — optional in-app link; {@code null}/absent when not supplied.</li>
 *   <li>{@code read}         — the single normative read signal.</li>
 *   <li>{@code readAt}       — when read; <b>omitted</b> (JSON {@code NON_NULL}) when {@code read=false}.</li>
 *   <li>{@code createdAt}    — creation time; the default sort key (newest first).</li>
 * </ul>
 *
 * <p>{@code @JsonInclude(NON_NULL)} so {@code readAt} and {@code deepLink} are
 * omitted from the JSON when null, per contract § 1.
 *
 * <p>A domain MAY carry extra fields in its own response (contract § 1.2); those
 * are ignored cross-domain. Extensions must not rename or retype these canonical
 * fields. This record is project-agnostic — the {@code type}/{@code sourceDomain}
 * vocabulary is domain-owned (HARDSTOP-03).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationView(
        String id,
        String sourceDomain,
        String type,
        String title,
        String body,
        String deepLink,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
}
