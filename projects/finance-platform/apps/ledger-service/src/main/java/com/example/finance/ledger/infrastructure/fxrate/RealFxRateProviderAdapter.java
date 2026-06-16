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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Real public FX rate adapter against <b>Frankfurter</b> (no-key, ECB daily reference rates —
 * TASK-FIN-BE-038, ADR-002 § 3.1 item 3). Wired when
 * {@code financeplatform.ledger.fxrate.mode=real}. Unlike {@link HttpFxRateProviderAdapter} (which
 * expects a bespoke {@code {"rate","asOf"}} shape no real provider emits), this adapter speaks
 * Frankfurter's actual response so the feed can run against a live source. The {@link RestClient} is
 * built ONCE in the constructor via {@link ResilienceClientFactory#buildRestClient(String, int, int)}
 * (libs/java-common — never {@code new RestTemplate()}).
 *
 * <p><b>Rate direction (CRUX):</b> the port's {@link RateQuote#rate()} is
 * base-minor-per-foreign-minor (KRW per 1 USD). {@link #latestQuote} is called with
 * {@code base=KRW}, {@code foreign=}the leg. Frankfurter {@code GET /latest?from=USD&to=KRW} returns
 * {@code rates.KRW} = KRW per 1 USD, so the mapping is
 * {@code from=foreign.code(), to=base.code(), rate=rates[base.code()]}. Swapping from/to would
 * invert the rate.
 *
 * <p><b>Best-effort, never-throw</b> (ADR-002 D3/D4): blank/null base URL, non-2xx, connection
 * refused, timeout, missing {@code rates} / missing {@code to} key, malformed number — all surface
 * as {@link Optional#empty()}. A malformed/absent {@code date} alone falls back to
 * {@code clock.now()} for {@code asOf} but still returns the parsed rate.
 * {@code source="real:<host>"}.
 */
@Component
@ConditionalOnProperty(name = "financeplatform.ledger.fxrate.mode", havingValue = "real")
public class RealFxRateProviderAdapter implements FxRateProviderPort {

    private final RestClient restClient;
    private final boolean configured;
    private final String sourceTag;
    private final ClockPort clock;

    public RealFxRateProviderAdapter(FxRateFeedProperties properties, ClockPort clock) {
        this.clock = clock;
        String baseUrl = properties.getReal().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            // Fail-soft: a misconfigured real mode (no base URL) wires an inert adapter that always
            // returns empty rather than failing the context start (ADR-002 edge case).
            this.configured = false;
            this.restClient = null;
            this.sourceTag = "real:unconfigured";
            return;
        }
        this.configured = true;
        this.restClient = ResilienceClientFactory.buildRestClient(
                baseUrl,
                properties.getReal().getConnectTimeoutMs(),
                properties.getReal().getReadTimeoutMs());
        this.sourceTag = "real:" + hostOf(baseUrl);
    }

    @Override
    public Optional<RateQuote> latestQuote(Currency base, Currency foreign) {
        if (!configured) {
            return Optional.empty();
        }
        try {
            // Direction CRUX: from=foreign, to=base → rates[base] = base-minor-per-foreign-minor.
            JsonNode body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/latest")
                            .queryParam("from", foreign.code())
                            .queryParam("to", base.code())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);   // throws on non-2xx → caught below
            if (body == null) {
                return Optional.empty();
            }
            JsonNode rates = body.get("rates");
            if (rates == null || !rates.hasNonNull(base.code())) {
                return Optional.empty();
            }
            BigDecimal rate = new BigDecimal(rates.get(base.code()).asText());
            Instant asOf = parseAsOf(body);
            return Optional.of(new RateQuote(rate, asOf, sourceTag));
        } catch (Exception e) {
            // best-effort: 5xx / connection refused / timeout / parse failure → empty, never throw.
            return Optional.empty();
        }
    }

    /** Parse Frankfurter's {@code date} (ECB daily {@link LocalDate}) at 00:00:00Z; fall back to clock. */
    private Instant parseAsOf(JsonNode body) {
        if (body.hasNonNull("date")) {
            try {
                return LocalDate.parse(body.get("date").asText())
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant();
            } catch (Exception e) {
                // malformed date → fall through to clock fallback (rate is still valid).
            }
        }
        return clock.now();
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
