package com.example.shipping.infrastructure.carrier;

import com.example.common.resilience.ResilienceClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Hand-rolled OAuth2 {@code client_credentials} token provider for Delivery Tracker
 * (TASK-BE-364 / external-integrations.md § 1.2), mirroring the
 * {@code GapClientCredentialsTokenProvider} pattern (ADR-005 workload identity) — there is no
 * shared provider in ecommerce, so this is built fresh.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@code POST {auth-url}} with {@code Authorization: Basic base64(clientId:clientSecret)}
 *       and form body {@code grant_type=client_credentials}; reads {@code access_token}
 *       (+ optional {@code expires_in}).</li>
 *   <li>The token is cached in memory and reused until {@code REFRESH_MARGIN} before expiry, then
 *       re-fetched (F1 — avoids per-call token storms / AC-2). If the response has no
 *       {@code expires_in}, the token is re-fetched on the next call (conservative).</li>
 *   <li><b>Best-effort / never-throw</b> (AC-4): any 4xx/5xx/timeout/parse failure → empty token,
 *       the cache is left untouched/cleared, and {@code delivery_tracker_token_failed} is counted.
 *       The adapter then performs no GraphQL call (returns {@code Optional.empty()}).</li>
 *   <li>Thread-safe — the auto-collect scheduler may call concurrently
 *       ({@code synchronized} around the cache read/refresh; the HTTP fetch itself is fast and the
 *       v1 call volume is low, so a coarse lock is correct + simplest).</li>
 * </ul>
 */
@Slf4j
public class DeliveryTrackerTokenProvider {

    /** Counter name for OAuth2 token fetch failures (external-integrations.md § 7). */
    static final String TOKEN_FAILED_COUNTER = "delivery_tracker_token_failed";

    /** Re-fetch this long before the declared expiry (safety margin against clock skew / in-flight). */
    private static final long REFRESH_MARGIN_SECONDS = 60;

    private final DeliveryTrackerProperties properties;
    private final RestClient tokenClient;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    private String cachedToken;
    private Instant expiresAt;

    DeliveryTrackerTokenProvider(DeliveryTrackerProperties properties,
                                 int connectTimeoutMs,
                                 int readTimeoutMs,
                                 MeterRegistry meterRegistry,
                                 Clock clock) {
        this.properties = properties;
        this.tokenClient = ResilienceClientFactory.buildRestClient(
                properties.authUrl(), connectTimeoutMs, readTimeoutMs);
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /**
     * A valid access token, fetching/refreshing as needed. {@link Optional#empty()} on a blank
     * credential (disabled) or any token failure (best-effort; never throws).
     */
    synchronized Optional<String> getToken() {
        if (!properties.hasCredentials()) {
            return Optional.empty();
        }
        if (cachedToken != null && expiresAt != null && clock.instant().isBefore(expiresAt)) {
            return Optional.of(cachedToken);
        }
        return fetchToken();
    }

    private Optional<String> fetchToken() {
        String basic = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8));
        try {
            JsonNode response = tokenClient.post()
                    .uri(builder -> builder.build())
                    .header("Authorization", "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("access_token")) {
                return fail("token response missing access_token");
            }
            String token = response.get("access_token").asText();
            long expiresIn = response.hasNonNull("expires_in") ? response.get("expires_in").asLong(0) : 0;
            cachedToken = token;
            // No expires_in (or non-positive) → no caching window: re-fetch next call (conservative).
            expiresAt = expiresIn > REFRESH_MARGIN_SECONDS
                    ? clock.instant().plusSeconds(expiresIn - REFRESH_MARGIN_SECONDS)
                    : null;
            return Optional.of(token);
        } catch (Exception ex) {
            return fail(ex.toString());
        }
    }

    private Optional<String> fail(String reason) {
        cachedToken = null;
        expiresAt = null;
        if (meterRegistry != null) {
            meterRegistry.counter(TOKEN_FAILED_COUNTER).increment();
        }
        log.warn("Delivery Tracker OAuth2 token fetch FAILED (best-effort no-op) reason={}", reason);
        return Optional.empty();
    }
}
