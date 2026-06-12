package com.example.shipping.infrastructure.carrier;

import com.example.common.resilience.ResilienceClientFactory;
import com.example.shipping.application.port.CarrierTrackingPort;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Real carrier-tracking integration over HTTP (TASK-BE-293) — a provider-agnostic
 * adapter active when {@code shipping.carrier.mode=http}. GETs
 * {@code ${base-url}/track?carrier=&trackingNumber=} with an API-key bearer header
 * via a {@link ResilienceClientFactory} RestClient (connect / read timeouts) and
 * reads the carrier's {@code status} field.
 *
 * <p><b>Best-effort, never throws</b> (the {@link CarrierTrackingPort} contract): a
 * non-2xx / transport / timeout / unparseable-or-no-status response is caught, logged
 * {@code warn}, and returned as {@link Optional#empty()} — a carrier hiccup must never
 * fail the admin refresh or mutate the shipment. No carrier SDK is added (plain
 * RestClient). Mutually exclusive with {@link MockCarrierTrackingAdapter}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "shipping.carrier.mode", havingValue = "http")
public class HttpCarrierTrackingAdapter implements CarrierTrackingPort {

    private final RestClient restClient;
    private final String apiKey;

    public HttpCarrierTrackingAdapter(
            @Value("${shipping.carrier.base-url:}") String baseUrl,
            @Value("${shipping.carrier.api-key:}") String apiKey,
            @Value("${shipping.carrier.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${shipping.carrier.read-timeout-ms:5000}") int readTimeoutMs) {
        this.apiKey = apiKey;
        this.restClient = ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Override
    public Optional<CarrierTrackingSnapshot> fetchLatest(String carrier, String trackingNumber) {
        try {
            JsonNode response = restClient.get()
                    .uri(builder -> builder.path("/track")
                            .queryParam("carrier", carrier)
                            .queryParam("trackingNumber", trackingNumber)
                            .build())
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("status")) {
                log.warn("Carrier response missing status (best-effort no-op) carrier={} tracking={}",
                        carrier, trackingNumber);
                return Optional.empty();
            }
            return Optional.of(new CarrierTrackingSnapshot(response.get("status").asText()));
        } catch (Exception ex) {
            log.warn("Carrier tracking fetch FAILED (best-effort no-op) carrier={} tracking={} reason={}",
                    carrier, trackingNumber, ex.toString());
            return Optional.empty();
        }
    }
}
