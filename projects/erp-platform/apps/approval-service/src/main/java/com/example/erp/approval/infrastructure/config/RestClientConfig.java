package com.example.erp.approval.infrastructure.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient builder for the {@code MasterDataPort} adapter (ADR-MONO-005
 * Category B synchronous masterdata-service call). Connect / read timeouts are
 * bounded per architecture.md § Saga / Long-running Flow (connect 2s / read 3s
 * defaults).
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClient.Builder restClientBuilder(
            @org.springframework.beans.factory.annotation.Value(
                    "${erpplatform.approval.masterdata.connect-timeout-ms:2000}") long connectMs,
            @org.springframework.beans.factory.annotation.Value(
                    "${erpplatform.approval.masterdata.read-timeout-ms:3000}") long readMs) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(connectMs))
                .withReadTimeout(Duration.ofMillis(readMs));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }
}
