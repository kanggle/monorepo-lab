package com.example.community.infrastructure.client;

import com.example.community.domain.access.AccountProfileLookup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Looks up account display names from account-service.
 * Failures return {@code null} — post body must still render.
 */
@Slf4j
@Component
public class AccountProfileClient implements AccountProfileLookup {

    private final RestClient restClient;

    public AccountProfileClient(
            @Value("${community.account-service.base-url}") String baseUrl,
            @Value("${community.account-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${community.account-service.read-timeout-ms:3000}") int readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    @Cacheable(cacheNames = "accountProfiles", key = "#accountId", unless = "#result == null")
    public String displayNameOf(String accountId) {
        if (accountId == null) return null;
        try {
            ProfileResponse resp = restClient.get()
                    .uri("/internal/accounts/{id}/profile", accountId)
                    .retrieve()
                    .body(ProfileResponse.class);
            return resp == null ? null : resp.displayName();
        } catch (Exception e) {
            log.warn("account-service profile lookup failed for {}: {}", accountId, e.getMessage());
            return null;
        }
    }

    public record ProfileResponse(String accountId, String displayName) {}
}
