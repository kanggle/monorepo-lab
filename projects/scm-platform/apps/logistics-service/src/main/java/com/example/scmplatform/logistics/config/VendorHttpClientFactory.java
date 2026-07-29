package com.example.scmplatform.logistics.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.function.Consumer;

/**
 * Builds a vendor's pooled {@link RestClient} — the Apache HttpClient 5 wiring that every
 * carrier-aggregator vendor needs identically (external-integrations.md §1.4/§1.8, §2.4/§2.8,
 * I1/I9).
 *
 * <p><b>This factory shares code, never instances (I9).</b> It is a stateless static helper with no
 * cache: each vendor {@code @Bean} calls {@link #pooledRestClient} exactly once, and every call
 * constructs a <b>new</b> {@link PoolingHttpClientConnectionManager}. Two vendor beans therefore
 * still hold two entirely independent connection pools — an EasyPost pool exhaustion cannot starve
 * 굿스플로, and vice versa. Returning a shared client here would silently collapse that isolation.
 *
 * <p>{@code disableAutomaticRetries()} is applied centrally: it is one of the two retry lessons
 * that had to be re-derived per vendor (TASK-SCM-BE-042 → TASK-SCM-BE-043), and a third vendor must
 * not pay for it a third time.
 */
final class VendorHttpClientFactory {

    private VendorHttpClientFactory() {
    }

    /**
     * @param props            the vendor's own properties (timeouts + <b>dedicated</b> pool sizing)
     * @param builderCustomizer vendor auth hook applied to the {@link RestClient.Builder} — e.g.
     *                          EasyPost's HTTP Basic default header. A vendor authenticating
     *                          per-request (굿스플로's API-key header) passes a no-op so the key is
     *                          not baked into the client.
     * @return a fresh {@link RestClient} over a fresh, vendor-private connection pool
     */
    static RestClient pooledRestClient(AbstractVendorClientProperties props,
                                       Consumer<RestClient.Builder> builderCustomizer) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(props.getConnectTimeoutSeconds()))
                .setSocketTimeout(Timeout.ofSeconds(props.getReadTimeoutSeconds()))
                .build();

        // A NEW pool per call — the I9 isolation guarantee. Never hoist this to a static field.
        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connectionConfig)
                        .build();
        connectionManager.setMaxTotal(props.getPoolMaxTotal());
        connectionManager.setDefaultMaxPerRoute(props.getPoolMaxPerRoute());

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(props.getConnectTimeoutSeconds()))
                .setResponseTimeout(Timeout.ofSeconds(props.getReadTimeoutSeconds()))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                // Retry is governed SOLELY by Resilience4j (the vendor's OWN instance). HttpClient
                // 5's DefaultHttpRequestRetryStrategy retries 429/503/IO internally, which would
                // double-count against the Resilience4j @Retry (max-attempts=3) and inflate the
                // real vendor-call count (external-integrations.md §1.6/§2.6 fix attempts at 3).
                .disableAutomaticRetries()
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.getBaseUrl());
        builderCustomizer.accept(builder);
        return builder
                .requestFactory(requestFactory)
                .build();
    }
}
