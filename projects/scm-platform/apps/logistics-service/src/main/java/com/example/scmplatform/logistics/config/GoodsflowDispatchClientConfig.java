package com.example.scmplatform.logistics.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The <b>dedicated</b> {@code goodsflowRestClient} — a Spring {@link RestClient} over an Apache
 * HttpClient 5 {@link PoolingHttpClientConnectionManager} sized {@code maxTotal=10 /
 * defaultMaxPerRoute=10} (external-integrations.md §2.4/§2.8, I1/I9).
 *
 * <p><b>Not shared</b> with EasyPost, the HTTP server pool, HikariCP, or Kafka pools — this pool +
 * the {@code goodsflowDispatch} Resilience4j instances are 굿스플로's alone (I9: "no pool shared
 * across vendors"). Auth is an API-key header (§2.2) applied per-request by the adapter, not here,
 * so the key is not baked into the shared client.
 *
 * <p>Not created under {@code standalone} (the 굿스플로 adapter is absent there).
 */
@Configuration
@Profile("!standalone")
public class GoodsflowDispatchClientConfig {

    @Bean
    RestClient goodsflowRestClient(GoodsflowClientProperties props) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(props.getConnectTimeoutSeconds()))
                .setSocketTimeout(Timeout.ofSeconds(props.getReadTimeoutSeconds()))
                .build();

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
                // Retry is governed SOLELY by Resilience4j (goodsflowDispatch). HttpClient 5's
                // DefaultHttpRequestRetryStrategy retries 429/503/IO internally, which would
                // double-count against the Resilience4j @Retry (max-attempts=3) and inflate the
                // real vendor-call count (§2.6 fixes attempts at 3). This is the reapplication of
                // the second BE-042 retry lesson for the 굿스플로 vendor.
                .disableAutomaticRetries()
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
