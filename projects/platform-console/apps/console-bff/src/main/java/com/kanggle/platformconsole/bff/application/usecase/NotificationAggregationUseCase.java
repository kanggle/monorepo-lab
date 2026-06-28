package com.kanggle.platformconsole.bff.application.usecase;

import com.kanggle.platformconsole.bff.application.composition.CompositionEngine;
import com.kanggle.platformconsole.bff.application.composition.CompositionLeg;
import com.kanggle.platformconsole.bff.application.port.outbound.ErpNotificationsReadPort;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import com.kanggle.platformconsole.bff.domain.credential.MissingCredentialException;
import com.kanggle.platformconsole.bff.domain.credential.OutboundCredential;
import com.kanggle.platformconsole.bff.infrastructure.config.NotificationAggregatorProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Notification aggregator fan-in use-case (ADR-MONO-043 P3a / D2).
 *
 * <p>Fans out across the configured operator-facing inbox domains
 * ({@link NotificationAggregatorProperties}, Phase-1 = {@code [erp]}) via the
 * shared {@link CompositionEngine}, merges every domain's items into one feed,
 * injects {@code sourceDomain} where absent (contract § 4.2), sorts by
 * {@code createdAt} desc (contract § 1 default sort), and returns the feed plus
 * the {@code degradedDomains} attribution.
 *
 * <h2>How this mirrors {@code OperatorOverviewCompositionUseCase}</h2>
 * <ul>
 *   <li><b>Pre-resolve on the servlet thread</b> — credentials are resolved via
 *       {@link CredentialSelectionPort} BEFORE {@code fanOut} (virtual threads
 *       carry no request scope).</li>
 *   <li><b>Per-domain credential dispatch (D6)</b> — each domain maps to a
 *       {@link DomainTarget}; the erp leg dispatches the IAM OIDC access token,
 *       never a rewrite.</li>
 *   <li><b>{@code CompositionEngine} fan-out</b> — route label
 *       {@code "notification-aggregator"}; reuses the {@code bff_fanout_latency} /
 *       {@code bff_fanout_errors} / {@code bff_aggregation_degrade_count} families.</li>
 * </ul>
 *
 * <h2>How this DIVERGES from Operator Overview</h2>
 * <ul>
 *   <li><b>NO cross-leg 401 collapse (D5)</b> — a single domain's 401 degrades
 *       only that leg; the bell still shows what the other domains return. Unlike
 *       Operator Overview, a degraded leg here never collapses the whole response.</li>
 *   <li><b>Always 200 with a partial feed</b> — a 503/timeout/network from one
 *       domain marks it degraded (added to {@code degradedDomains}) while the rest
 *       render (HARD INVARIANT — the § 1.2 regression this ADR forbids).</li>
 *   <li><b>erp sends NO {@code X-Tenant-Id}</b> — handled in the adapter (D6 /
 *       contract § 3); the use-case passes no tenant to the read port.</li>
 * </ul>
 */
@Service
public class NotificationAggregationUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationAggregationUseCase.class);

    static final String ROUTE_LABEL = "notification-aggregator";
    static final String DASHBOARD_LABEL = "notification-aggregator";

    /** Attribution field name injected when a domain omits it (contract § 1 / § 4.2). */
    static final String FIELD_SOURCE_DOMAIN = "sourceDomain";
    /** Default sort key (contract § 1 — newest first). */
    static final String FIELD_CREATED_AT = "createdAt";

    private final NotificationAggregatorProperties properties;
    private final CredentialSelectionPort credentialSelection;
    private final CompositionEngine engine;
    private final ErpNotificationsReadPort erpPort;

    public NotificationAggregationUseCase(
            NotificationAggregatorProperties properties,
            CredentialSelectionPort credentialSelection,
            MeterRegistry meterRegistry,
            Tracer tracer,
            ErpNotificationsReadPort erpPort) {
        this.properties = properties;
        this.credentialSelection = credentialSelection;
        this.engine = new CompositionEngine(meterRegistry, tracer, ROUTE_LABEL);
        this.erpPort = erpPort;
    }

    // ------------------------------------------------------------------
    // Inbox aggregation (GET)
    // ------------------------------------------------------------------

    /**
     * Fans in the configured domains' inboxes into one merged feed.
     *
     * @param page   page index forwarded to each domain (≥ 0)
     * @param size   page size forwarded to each domain (1–100)
     * @param unread {@code true}/{@code false}/{@code null} read filter (contract § 2.1)
     */
    public NotificationAggregationResult aggregate(int page, int size, Boolean unread) {
        // (1) Resolve the configured domain set → DomainTarget routing, on the SERVLET THREAD.
        Map<DomainTarget, String> targets = resolveTargets();

        // (2) Pre-resolve credentials on the SERVLET THREAD (D6 — virtual threads
        //     spawned by the engine inherit no request scope).
        Map<DomainTarget, OutboundCredential> preResolved = new EnumMap<>(DomainTarget.class);
        Map<DomainTarget, CompositionLeg> earlyDecided = new EnumMap<>(DomainTarget.class);
        for (DomainTarget domain : targets.keySet()) {
            try {
                preResolved.put(domain, credentialSelection.selectFor(domain));
            } catch (MissingCredentialException mce) {
                // Per-domain degrade (D5) — NO cross-leg collapse. The domain is
                // simply marked degraded; the others still render.
                engine.emitErrorCounter(domain, "missing_prerequisite");
                earlyDecided.put(domain,
                        CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "MISSING_PREREQUISITE")));
            }
        }

        // (3) Build the per-domain leg bodies (one virtual thread each via the engine).
        Map<DomainTarget, Supplier<CompositionLeg>> legBodies = new EnumMap<>(DomainTarget.class);
        for (DomainTarget domain : targets.keySet()) {
            legBodies.put(domain, legBody(domain, earlyDecided, preResolved,
                    cred -> CompositionLeg.ok(LegOutcome.ok(domain),
                            readInbox(domain, bearerFromCred(cred), page, size, unread))));
        }

        // (4) Fan out — failure isolation is enforced inside the engine + classifier
        //     (each leg degrades independently; no leg can fail the whole response).
        Map<DomainTarget, CompositionLeg> results = engine.fanOut(ROUTE_LABEL, legBodies);

        // (5) Merge + inject sourceDomain + sort + collect degradedDomains.
        List<Map<String, Object>> merged = new ArrayList<>();
        List<String> degradedDomains = new ArrayList<>();
        long totalElements = 0L;
        for (DomainTarget domain : targets.keySet()) {
            String domainName = targets.get(domain);
            CompositionLeg leg = results.get(domain);
            if (leg == null || !leg.outcome().isOk()) {
                // D5: per-domain degrade — record it, render the rest.
                degradedDomains.add(domainName);
                engine.emitAggregationDegradeCounter(DASHBOARD_LABEL, domain);
                continue;
            }
            DomainItems extracted = extractItems(leg.data(), domainName);
            merged.addAll(extracted.items());
            totalElements += extracted.total();
        }

        merged.sort(BY_CREATED_AT_DESC);

        if (!degradedDomains.isEmpty()) {
            LOG.warn("notification-aggregator: {} of {} domains degraded {} (still emitting 200 per D5)",
                    degradedDomains.size(), targets.size(), degradedDomains);
        }
        return new NotificationAggregationResult(merged, degradedDomains, totalElements);
    }

    // ------------------------------------------------------------------
    // Mark-read dispatch (POST)
    // ------------------------------------------------------------------

    /**
     * Proxies an idempotent mark-read to the owning domain (contract § 4.5).
     *
     * @param sourceDomain the owning domain (must be a configured inbox domain)
     * @param id           the notification id
     * @return the owning domain's raw response body
     * @throws UnknownNotificationDomainException if {@code sourceDomain} is not a
     *                                            configured inbox domain (→ 404)
     */
    public Map<String, Object> markRead(String sourceDomain, String id) {
        DomainTarget domain = targetForDomain(sourceDomain);
        if (domain == null) {
            throw new UnknownNotificationDomainException(sourceDomain);
        }
        // Per-domain credential dispatch (D6) — resolved on the servlet thread.
        OutboundCredential cred = credentialSelection.selectFor(domain);
        return markReadFor(domain, bearerFromCred(cred), id);
    }

    // ------------------------------------------------------------------
    // Domain routing — Phase-1 erp-only; Phase-2 extends with zero controller change
    // ------------------------------------------------------------------

    /**
     * Maps each configured domain string to its {@link DomainTarget}, preserving
     * configured order via a {@link LinkedHashMap}. An unknown domain string in
     * config is skipped with a warning (fail-soft — a misconfigured domain must
     * not fail the whole bell, D5).
     */
    private Map<DomainTarget, String> resolveTargets() {
        Map<DomainTarget, String> targets = new LinkedHashMap<>();
        for (String domain : properties.getDomains()) {
            DomainTarget target = targetForDomain(domain);
            if (target == null) {
                LOG.warn("notification-aggregator: configured domain '{}' has no inbox read port — skipped", domain);
                continue;
            }
            targets.put(target, domain);
        }
        return targets;
    }

    /**
     * Maps a {@code sourceDomain} string to its {@link DomainTarget}, or
     * {@code null} when no inbox read port serves it. Phase-1 routes {@code "erp"}
     * only; Phase-2 adds arms here as each domain's read port lands.
     */
    private DomainTarget targetForDomain(String domain) {
        if (domain == null) {
            return null;
        }
        return switch (domain.toLowerCase()) {
            case "erp" -> DomainTarget.ERP;
            default -> null;
        };
    }

    /** Dispatches the inbox read to the owning domain's read port. */
    private Map<String, Object> readInbox(DomainTarget domain, String credential,
                                          int page, int size, Boolean unread) {
        if (domain == DomainTarget.ERP) {
            return erpPort.readInbox(credential, page, size, unread);
        }
        throw new IllegalStateException("No inbox read port for domain " + domain);
    }

    /** Dispatches the mark-read to the owning domain's read port. */
    private Map<String, Object> markReadFor(DomainTarget domain, String credential, String id) {
        if (domain == DomainTarget.ERP) {
            return erpPort.markRead(credential, id);
        }
        throw new IllegalStateException("No inbox read port for domain " + domain);
    }

    // ------------------------------------------------------------------
    // Leg plumbing (mirrors OperatorOverviewCompositionUseCase)
    // ------------------------------------------------------------------

    private Supplier<CompositionLeg> legBody(
            DomainTarget domain,
            Map<DomainTarget, CompositionLeg> earlyDecided,
            Map<DomainTarget, OutboundCredential> preResolved,
            java.util.function.Function<OutboundCredential, CompositionLeg> body) {
        return () -> {
            CompositionLeg early = earlyDecided.get(domain);
            if (early != null) {
                return early;
            }
            return engine.time(domain, () -> body.apply(preResolved.get(domain)), this::classifyError);
        };
    }

    /** Sealed-switch on the resolved {@link OutboundCredential} (D6 consumer side). */
    private static String bearerFromCred(OutboundCredential cred) {
        return switch (cred) {
            case OutboundCredential.OperatorToken t -> t.token();
            case OutboundCredential.IamOidcAccessToken t -> t.token();
        };
    }

    /**
     * Per-leg error classifier — every failure (incl. 401) degrades ONLY this
     * leg (D5). There is NO cross-leg 401 collapse here (the divergence from
     * Operator Overview): a 401 from one domain must not blank the bell for the
     * others, so it is classified as a per-domain degrade, never an
     * {@code UpstreamUnauthorizedException}.
     */
    private CompositionLeg classifyError(DomainTarget domain, Throwable e) {
        if (e instanceof MissingCredentialException) {
            engine.emitErrorCounter(domain, "missing_prerequisite");
            return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "MISSING_PREREQUISITE"));
        }
        if (e instanceof org.springframework.web.client.HttpClientErrorException.Unauthorized) {
            // D5 divergence: per-domain degrade, NOT a cross-leg 401 collapse.
            engine.emitErrorCounter(domain, "5xx");
            return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "UNAUTHORIZED"));
        }
        if (e instanceof org.springframework.web.client.HttpClientErrorException) {
            engine.emitErrorCounter(domain, "5xx");
            return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"));
        }
        if (e instanceof org.springframework.web.client.ResourceAccessException rae
                && rae.getCause() instanceof java.net.SocketTimeoutException) {
            engine.emitErrorCounter(domain, "timeout");
            return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "TIMEOUT"));
        }
        engine.emitErrorCounter(domain, "5xx");
        if (!(e instanceof org.springframework.web.client.ResourceAccessException)) {
            LOG.warn("notification-aggregator leg {} unexpected error: {}: {}", domain,
                    e.getClass().getSimpleName(), e.getMessage());
        }
        return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"));
    }

    // ------------------------------------------------------------------
    // Item extraction + sourceDomain injection (contract § 1 / § 4.2)
    // ------------------------------------------------------------------

    /**
     * Extracts the item list + reported total from a domain's raw inbox response
     * ({@code { data: [...], meta: { totalElements } }}), injecting
     * {@code sourceDomain} on any item that omits it (contract § 4.2).
     */
    @SuppressWarnings("unchecked")
    private DomainItems extractItems(Object rawBody, String domainName) {
        if (!(rawBody instanceof Map<?, ?> body)) {
            return new DomainItems(List.of(), 0L);
        }
        Object data = body.get("data");
        List<Map<String, Object>> items = new ArrayList<>();
        if (data instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> item) {
                    Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) item);
                    // Inject sourceDomain iff absent (contract § 4.2) — the domain may
                    // already carry it (erp does, via TASK-ERP-BE-027); never overwrite.
                    Object existing = copy.get(FIELD_SOURCE_DOMAIN);
                    if (existing == null || (existing instanceof String s && s.isBlank())) {
                        copy.put(FIELD_SOURCE_DOMAIN, domainName);
                    }
                    items.add(copy);
                }
            }
        }
        long total = readTotal(body.get("meta"));
        return new DomainItems(items, total);
    }

    private static long readTotal(Object meta) {
        if (meta instanceof Map<?, ?> m) {
            Object te = m.get("totalElements");
            if (te instanceof Number n) {
                return n.longValue();
            }
        }
        return 0L;
    }

    /** Merge-sort comparator: {@code createdAt} desc, nulls last (contract § 1). */
    private static final Comparator<Map<String, Object>> BY_CREATED_AT_DESC =
            Comparator.comparing(NotificationAggregationUseCase::createdAtOf,
                    Comparator.nullsLast(Comparator.reverseOrder()));

    private static Instant createdAtOf(Map<String, Object> item) {
        Object v = item.get(FIELD_CREATED_AT);
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Instant.parse(s);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private record DomainItems(List<Map<String, Object>> items, long total) {
    }
}
