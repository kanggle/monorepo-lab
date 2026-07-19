package com.example.batch.infrastructure.client;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Internal helper for building {@link RestClient}s with explicit connect/read timeouts.
 *
 * <p>Every outbound client in this package needs the same guard: explicit timeouts so a hung
 * downstream cannot block the scheduler thread for the entire ShedLock window (BE-409/BE-413).
 * This collapses the repeated {@link SimpleClientHttpRequestFactory} setup into one place.
 * Returns a {@link RestClient.Builder} so callers can add a {@code baseUrl} (or not) before
 * {@code build()}.
 */
final class RestClients {

    private RestClients() {
    }

    /**
     * A {@link RestClient.Builder} pre-configured with a {@link SimpleClientHttpRequestFactory}
     * carrying the given connect/read timeouts.
     */
    static RestClient.Builder timed(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory);
    }
}
