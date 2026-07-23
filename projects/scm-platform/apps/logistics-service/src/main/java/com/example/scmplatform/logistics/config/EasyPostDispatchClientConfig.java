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
 * The <b>dedicated</b> {@code easyPostRestClient} — a Spring {@link RestClient} over an Apache
 * HttpClient 5 {@link PoolingHttpClientConnectionManager} sized {@code maxTotal=10 /
 * defaultMaxPerRoute=10} (external-integrations.md §1.4/§1.8, I1/I9).
 *
 * <p><b>Not shared</b> with 굿스플로 (BE-043), the HTTP server pool, HikariCP, or Kafka pools —
 * this pool + the {@code easyPostDispatch} Resilience4j instances are EasyPost's alone. Auth is
 * HTTP Basic with the API key as username and an empty password (§1.2).
 *
 * <p>Not created under {@code standalone} (the EasyPost adapter is absent there).
 */
@Configuration
@Profile("!standalone")
public class EasyPostDispatchClientConfig {

    @Bean
    RestClient easyPostRestClient(EasyPostClientProperties props) {
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
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                // HTTP Basic: API key as username, empty password (§1.2).
                .defaultHeaders(headers -> headers.setBasicAuth(props.getApiKey(), ""))
                .requestFactory(requestFactory)
                .build();
    }
}
