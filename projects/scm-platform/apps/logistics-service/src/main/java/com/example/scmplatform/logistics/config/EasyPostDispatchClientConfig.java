package com.example.scmplatform.logistics.config;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

/**
 * The <b>dedicated</b> {@code easyPostRestClient} — a Spring {@link RestClient} over an Apache
 * HttpClient 5 {@link PoolingHttpClientConnectionManager} sized {@code maxTotal=10 /
 * defaultMaxPerRoute=10} (external-integrations.md §1.4/§1.8, I1/I9).
 *
 * <p><b>Not shared</b> with 굿스플로 (BE-043), the HTTP server pool, HikariCP, or Kafka pools —
 * this pool + the {@code easyPostDispatch} Resilience4j instances are EasyPost's alone. The
 * transport wiring is built by {@link VendorHttpClientFactory}, which allocates a <b>new</b> pool
 * on every invocation: this bean method and 굿스플로's each call it once, so two independent
 * {@code PoolingHttpClientConnectionManager} instances exist at runtime. Auth is HTTP Basic with
 * the API key as username and an empty password (§1.2).
 *
 * <p>Not created under {@code standalone} (the EasyPost adapter is absent there).
 */
@Configuration
@Profile("!standalone")
public class EasyPostDispatchClientConfig {

    @Bean
    RestClient easyPostRestClient(EasyPostClientProperties props) {
        return VendorHttpClientFactory.pooledRestClient(props,
                // HTTP Basic: API key as username, empty password (§1.2). Applied as a client
                // default header — EasyPost's alone; no other vendor's client sees it.
                builder -> builder.defaultHeaders(headers -> headers.setBasicAuth(props.getApiKey(), "")));
    }
}
