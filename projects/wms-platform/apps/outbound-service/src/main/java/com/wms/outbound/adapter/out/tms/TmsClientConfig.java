package com.wms.outbound.adapter.out.tms;

import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wiring for the TMS {@link RestClient} bean.
 *
 * <p>Per {@code external-integrations.md} §2.4 / §2.8 we use a dedicated
 * Apache HttpClient 5 connection pool (size = {@code maxConnections}, I9
 * bulkhead) with hard timeouts (I1). The pool is <em>not shared</em> with
 * any other vendor — Spring's autoconfigured {@code RestTemplate} /
 * {@code WebClient} pools are intentionally not reused here.
 *
 * <p>Disabled under {@code standalone} — that profile uses
 * {@link StubTmsClientAdapter} and never opens HTTP connections.
 */
@Configuration
@EnableConfigurationProperties(TmsClientProperties.class)
@Profile("!standalone")
public class TmsClientConfig {

    /**
     * Dedicated Apache HttpClient 5 with bounded connection pool. Bean name
     * is qualifier-friendly so future TMS-related beans can wire it
     * explicitly.
     */
    @Bean(name = "tmsHttpClient", destroyMethod = "close")
    public CloseableHttpClient tmsHttpClient(TmsClientProperties properties) {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(properties.maxConnections());
        connectionManager.setDefaultMaxPerRoute(properties.maxConnections());
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(properties.connectTimeoutMs(), TimeUnit.MILLISECONDS))
                .setSocketTimeout(Timeout.of(properties.readTimeoutMs(), TimeUnit.MILLISECONDS))
                .build());

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(properties.connectTimeoutMs(), TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(properties.readTimeoutMs(), TimeUnit.MILLISECONDS))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(Timeout.ofMinutes(1))
                .build();
    }

    /**
     * Dedicated {@link RestClient} for the TMS adapter. Base URL and the
     * default {@code X-Tms-Api-Key} header are populated here so the
     * adapter only worries about request/response shape.
     */
    @Bean(name = "tmsRestClient")
    public RestClient tmsRestClient(@org.springframework.beans.factory.annotation.Qualifier("tmsHttpClient")
                                    CloseableHttpClient httpClient,
                                    TmsClientProperties properties) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        // Connect / read timeouts also enforced at the Spring layer for
        // defence-in-depth (the underlying HttpClient already enforces them).
        factory.setConnectTimeout((int) properties.connectTimeoutMs());

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json");
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            builder = builder.baseUrl(properties.baseUrl());
        }
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder = builder.defaultHeader("X-Tms-Api-Key", properties.apiKey());
        }
        return builder.build();
    }
}
