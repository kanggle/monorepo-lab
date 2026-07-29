package com.example.scmplatform.logistics.config;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

/**
 * The <b>dedicated</b> {@code goodsflowRestClient} — a Spring {@link RestClient} over an Apache
 * HttpClient 5 {@link PoolingHttpClientConnectionManager} sized {@code maxTotal=10 /
 * defaultMaxPerRoute=10} (external-integrations.md §2.4/§2.8, I1/I9).
 *
 * <p><b>Not shared</b> with EasyPost, the HTTP server pool, HikariCP, or Kafka pools — this pool +
 * the {@code goodsflowDispatch} Resilience4j instances are 굿스플로's alone (I9: "no pool shared
 * across vendors"). The transport wiring is built by {@link VendorHttpClientFactory}, which
 * allocates a <b>new</b> pool on every invocation; this bean method calls it once, separately from
 * EasyPost's, so the two pools remain distinct instances. Auth is an API-key header (§2.2) applied
 * per-request by the adapter, not here, so the key is not baked into the client.
 *
 * <p>Not created under {@code standalone} (the 굿스플로 adapter is absent there).
 */
@Configuration
@Profile("!standalone")
public class GoodsflowDispatchClientConfig {

    @Bean
    RestClient goodsflowRestClient(GoodsflowClientProperties props) {
        // No client-level auth customisation — the API-key header is per-request (§2.2).
        return VendorHttpClientFactory.pooledRestClient(props, builder -> {
        });
    }
}
