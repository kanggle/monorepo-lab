package com.example.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClient 빌더 빈을 제공한다.
 *
 * {@code ProductCatalogHttpAdapter} 등 외부 서비스 HTTP 연동에서 공통으로 사용된다.
 */
@Configuration
class RestClientConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
