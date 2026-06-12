package com.example.shipping.infrastructure.carrier;

import com.example.common.resilience.ResilienceClientFactory;
import com.example.shipping.application.port.CarrierTrackingPort;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Delivery Tracker (tracker.delivery) outbound pull adapter (TASK-BE-364 /
 * external-integrations.md § 1). The concrete aggregator behind the provider-agnostic
 * {@link CarrierTrackingPort} (ADR-007 D2). Active only when
 * {@code shipping.carrier.mode=delivery-tracker}; the default {@code mode=mock} and the legacy
 * {@code mode=http} adapter ({@link HttpCarrierTrackingAdapter}) are net-zero / untouched
 * (exactly one {@link CarrierTrackingPort} bean is ever active, mutually-exclusive conditions).
 *
 * <p>Wire (§ 1.2–1.3): obtain an OAuth2 {@code client_credentials} Bearer token via
 * {@link DeliveryTrackerTokenProvider}, then {@code POST {graphql-url}} the
 * {@code GetTrackLastEvent} query and read ONLY {@code data.track.lastEvent.status.code} into a
 * {@link CarrierTrackingSnapshot}. The GraphQL request/response DTOs are adapter-internal — they
 * never cross the port boundary (integration-heavy I8).
 *
 * <p><b>Best-effort / never-throw</b> (§ 1.6 / AC-4): no token, transport error, non-2xx, a
 * GraphQL {@code errors[]} payload, {@code track == null}, a missing {@code lastEvent}, or an
 * absent {@code status.code} all return {@link Optional#empty()} (a no-op refresh) with a WARN/DEBUG
 * log and a {@code carrier_tracking_fetch{result}} counter. A carrier hiccup never fails the admin
 * request or mutates the shipment. Mapping of the raw code → domain status (and the unmapped
 * {@code carrier_status_unmapped} signal) happens in the caller, keeping this a pure ACL.
 */
@Slf4j
public class DeliveryTrackerCarrierTrackingAdapter implements CarrierTrackingPort {

    /** Counter name for pull outcomes (external-integrations.md § 7). */
    static final String FETCH_COUNTER = "carrier_tracking_fetch";

    private static final String QUERY =
            "query GetTrackLastEvent($carrierId: ID!, $trackingNumber: String!) {"
                    + " track(carrierId: $carrierId, trackingNumber: $trackingNumber) {"
                    + " lastEvent { time status { code } } } }";

    private final DeliveryTrackerTokenProvider tokenProvider;
    private final RestClient graphqlClient;
    private final MeterRegistry meterRegistry;

    DeliveryTrackerCarrierTrackingAdapter(DeliveryTrackerProperties properties,
                                          DeliveryTrackerTokenProvider tokenProvider,
                                          int connectTimeoutMs,
                                          int readTimeoutMs,
                                          MeterRegistry meterRegistry) {
        this.tokenProvider = tokenProvider;
        this.graphqlClient = ResilienceClientFactory.buildRestClient(
                properties.graphqlUrl(), connectTimeoutMs, readTimeoutMs);
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Optional<CarrierTrackingSnapshot> fetchLatest(String carrier, String trackingNumber) {
        Optional<String> token = tokenProvider.getToken();
        if (token.isEmpty()) {
            // Disabled (blank credential) or token failure — no GraphQL call (§ 1.6).
            count("auth_failed");
            return Optional.empty();
        }

        JsonNode response;
        try {
            response = graphqlClient.post()
                    .uri(builder -> builder.build())
                    .header("Authorization", "Bearer " + token.get())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(carrier, trackingNumber))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.warn("Delivery Tracker track call FAILED (best-effort no-op) carrier={} tracking={} reason={}",
                    carrier, trackingNumber, ex.toString());
            count("transport_failed");
            return Optional.empty();
        }

        if (response == null) {
            count("transport_failed");
            return Optional.empty();
        }
        if (response.has("errors") && !response.get("errors").isNull()
                && !response.get("errors").isEmpty()) {
            log.warn("Delivery Tracker returned GraphQL errors (best-effort no-op) carrier={} tracking={} errors={}",
                    carrier, trackingNumber, response.get("errors").toString());
            count("graphql_error");
            return Optional.empty();
        }

        JsonNode code = response.path("data").path("track").path("lastEvent").path("status").path("code");
        if (response.path("data").path("track").isMissingNode()
                || response.path("data").path("track").isNull()
                || !code.isTextual() || code.asText().isBlank()) {
            log.debug("Delivery Tracker track has no usable lastEvent.status.code carrier={} tracking={}",
                    carrier, trackingNumber);
            count("no_event");
            return Optional.empty();
        }

        count("advanced");
        return Optional.of(new CarrierTrackingSnapshot(code.asText()));
    }

    /**
     * The GraphQL POST body as a {@link Map} (Jackson-serialised by RestClient): {@code query} +
     * {@code variables}. Adapter-internal shape — never exposed beyond this class (I8).
     */
    private Map<String, Object> requestBody(String carrier, String trackingNumber) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("carrierId", carrier);
        variables.put("trackingNumber", trackingNumber);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", QUERY);
        body.put("variables", variables);
        return body;
    }

    private void count(String result) {
        if (meterRegistry != null) {
            meterRegistry.counter(FETCH_COUNTER, "result", result).increment();
        }
    }
}
