package com.example.finance.ledger.infrastructure.fxrate;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateProviderPort;
import com.example.finance.ledger.domain.money.Currency;
import com.example.common.resilience.ResilienceClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/**
 * HTTP FX rate adapter (23rd increment — TASK-FIN-BE-031, ADR-002 D1/D5). Wired when
 * {@code financeplatform.ledger.fxrate.mode=http}. The {@link RestClient} is built ONCE in the
 * constructor via {@link ResilienceClientFactory#buildRestClient(String, int, int)}
 * (libs/java-common — never {@code new RestTemplate()}), carrying the shared connect/read timeouts.
 *
 * <p><b>Best-effort, never-throw</b> (AC-4): {@link #latestQuote} does a {@code GET
 * <baseUrl>/<base>/<foreign>} expecting a simple JSON body {@code {"rate":"1300.0",
 * "asOf":"2026-06-15T00:00:00Z"}} and parses it to a {@link RateQuote}. EVERY failure mode —
 * blank/null base URL, non-2xx, connection refused, timeout, missing/garbage {@code rate}, parse
 * failure — is wrapped in a catch-all and surfaces as {@link Optional#empty()}. {@code asOf} falls
 * back to {@code clock.now()} when the response omits it. {@code source="http:<host>"}.
 */
@Component
@ConditionalOnProperty(name = "financeplatform.ledger.fxrate.mode", havingValue = "http")
public class HttpFxRateProviderAdapter implements FxRateProviderPort {

    private final RestClient restClient;
    private final boolean configured;
    private final String sourceTag;
    private final ClockPort clock;

    public HttpFxRateProviderAdapter(FxRateFeedProperties properties, ClockPort clock) {
        this.clock = clock;
        String baseUrl = properties.getHttp().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            // Fail-soft: a misconfigured http mode (no base URL) wires an inert adapter that always
            // returns empty rather than failing the context start (ADR-002 edge case).
            this.configured = false;
            this.restClient = null;
            this.sourceTag = "http:unconfigured";
            return;
        }
        this.configured = true;
        this.restClient = ResilienceClientFactory.buildRestClient(
                baseUrl,
                properties.getHttp().getConnectTimeoutMs(),
                properties.getHttp().getReadTimeoutMs());
        this.sourceTag = "http:" + hostOf(baseUrl);
    }

    @Override
    public Optional<RateQuote> latestQuote(Currency base, Currency foreign) {
        if (!configured) {
            return Optional.empty();
        }
        try {
            JsonNode body = restClient.get()
                    .uri("/{base}/{foreign}", base.code(), foreign.code())
                    .retrieve()
                    .body(JsonNode.class);   // throws on non-2xx → caught below
            if (body == null || !body.hasNonNull("rate")) {
                return Optional.empty();
            }
            BigDecimal rate = new BigDecimal(body.get("rate").asText());
            Instant asOf = body.hasNonNull("asOf")
                    ? Instant.parse(body.get("asOf").asText())
                    : clock.now();
            return Optional.of(new RateQuote(rate, asOf, sourceTag));
        } catch (Exception e) {
            // best-effort: 5xx / connection refused / timeout / parse failure → empty, never throw.
            return Optional.empty();
        }
    }

    private static String hostOf(String baseUrl) {
        try {
            String host = URI.create(baseUrl).getHost();
            return host != null ? host : baseUrl;
        } catch (Exception e) {
            return baseUrl;
        }
    }
}
