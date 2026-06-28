package com.kanggle.platformconsole.bff.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Operator-facing notification inbox domain set for the console aggregator
 * (ADR-MONO-043 P3a / D2).
 *
 * <p>Bound from {@code consolebff.notifications.*} in {@code application.yml}.
 * Phase-1 is <b>erp-only</b> (the default {@code ["erp"]}); Phase-2 extends
 * the list with zero controller change — the aggregator iterates whatever
 * domains are configured and routes each to its read port + credential.
 *
 * <p>The string values are the {@code sourceDomain} attributions
 * (contract § 1 / § 4.2) AND the {@code {sourceDomain}} path segment of the
 * mark-read route ({@code POST /api/console/notifications/{sourceDomain}/{id}/read}).
 * The aggregator maps each string to a {@code DomainTarget} (credential
 * selection, D6) + the owning read port; an unconfigured / unknown domain is
 * rejected with 404 at the mark-read controller edge.
 */
@ConfigurationProperties(prefix = "consolebff.notifications")
public class NotificationAggregatorProperties {

    /**
     * The operator-facing notification inbox domains, in render priority order.
     * Default: {@code ["erp"]} (Phase-1).
     */
    private List<String> domains = List.of("erp");

    public List<String> getDomains() {
        return domains;
    }

    public void setDomains(List<String> domains) {
        this.domains = domains;
    }
}
