package com.example.finance.ledger.infrastructure.fxrate;

import com.example.finance.ledger.application.port.outbound.FxRateFeedSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Config properties for the FX rate feed (23rd increment — TASK-FIN-BE-031, ADR-002 D1/D5).
 * Bound under {@code financeplatform.ledger.fxrate}. <b>Default net-zero</b>: {@code enabled=false}
 * (the poller bean is not created) + {@code mode=noop} (the noop adapter is wired, making zero
 * external calls). The base currency is fixed to KRW ({@code LedgerReportingCurrency.BASE}); only
 * the foreign legs are configured via {@link #pairs}.
 *
 * <p>Registered via {@code @EnableConfigurationProperties} on {@link FxRateFeedConfig} (this is the
 * service's first {@code @ConfigurationProperties} type — no {@code @ConfigurationPropertiesScan} is
 * in play).
 */
@ConfigurationProperties("financeplatform.ledger.fxrate")
public class FxRateFeedProperties implements FxRateFeedSettings {

    /** Master gate for the scheduled poller. Default OFF (net-zero — no poller bean). */
    private boolean enabled = false;

    /**
     * Selects the active adapter bean: {@code noop} (default) / {@code stub} / {@code http} /
     * {@code real} (Frankfurter public API — TASK-FIN-BE-038).
     */
    private String mode = "noop";

    /** Poll cadence in ms (fixed-delay between {@code RefreshFxRateQuotesUseCase} runs). */
    private long pollIntervalMs = 60_000;

    /** Foreign currency legs to poll (base is KRW). e.g. {@code [USD, EUR, JPY]}. */
    private List<String> pairs = new ArrayList<>();

    /**
     * Staleness horizon for a cached quote, in minutes (24th increment — TASK-FIN-BE-032,
     * ADR-002 D4). A quote whose {@code asOf} is older than this relative to now is rejected as
     * stale on the operator's omitted-rate fallback. Default 1440 (24h).
     */
    private long maxAgeMinutes = 1440;

    /** {@code stub}-mode fixed rates. */
    private Stub stub = new Stub();

    /** {@code http}-mode endpoint config. */
    private Http http = new Http();

    /** {@code real}-mode (Frankfurter public API) endpoint config. */
    private Real real = new Real();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public List<String> getPairs() {
        return pairs;
    }

    /** {@link FxRateFeedSettings} — the application-layer view used by the load use case. */
    @Override
    public List<String> pairs() {
        return pairs;
    }

    public void setPairs(List<String> pairs) {
        this.pairs = pairs;
    }

    public long getMaxAgeMinutes() {
        return maxAgeMinutes;
    }

    public void setMaxAgeMinutes(long maxAgeMinutes) {
        this.maxAgeMinutes = maxAgeMinutes;
    }

    /**
     * {@link FxRateFeedSettings} — the master gate for the operator's omitted-rate cache fallback
     * (24th increment — TASK-FIN-BE-032). Mirrors {@link #isEnabled()} (default OFF = net-zero).
     */
    @Override
    public boolean feedEnabled() {
        return enabled;
    }

    /**
     * {@link FxRateFeedSettings} — the staleness horizon as a {@link Duration} (24th increment —
     * TASK-FIN-BE-032), derived from {@link #maxAgeMinutes}.
     */
    @Override
    public Duration staleAfter() {
        return Duration.ofMinutes(maxAgeMinutes);
    }

    public Stub getStub() {
        return stub;
    }

    public void setStub(Stub stub) {
        this.stub = stub;
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http;
    }

    public Real getReal() {
        return real;
    }

    public void setReal(Real real) {
        this.real = real;
    }

    /** {@code stub}-mode fixed rates: foreign ISO code → base-minor-per-foreign-minor rate. */
    public static class Stub {

        private Map<String, BigDecimal> rates = new LinkedHashMap<>();

        public Map<String, BigDecimal> getRates() {
            return rates;
        }

        public void setRates(Map<String, BigDecimal> rates) {
            this.rates = rates;
        }
    }

    /** {@code http}-mode endpoint config (consumed only when {@code mode=http}). */
    public static class Http {

        private String baseUrl;
        private int connectTimeoutMs = 2_000;
        private int readTimeoutMs = 5_000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    /**
     * {@code real}-mode config (consumed only when {@code mode=real}) — Frankfurter public API
     * (no-key, ECB daily). Default base URL {@code https://api.frankfurter.dev/v1}.
     */
    public static class Real {

        private String baseUrl = "https://api.frankfurter.dev/v1";
        private int connectTimeoutMs = 2_000;
        private int readTimeoutMs = 5_000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }
}
