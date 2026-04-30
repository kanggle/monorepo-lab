package com.example.community.infrastructure.client;

import com.example.community.domain.access.ArtistNotFoundException;
import com.example.community.domain.access.ArtistAccountChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Verifies artist account existence by calling account-service's internal lookup.
 * <ul>
 *     <li>404 → {@link ArtistNotFoundException} (strict)</li>
 *     <li>5xx / timeout / network error → fail-open: log warn, return normally</li>
 * </ul>
 * The fail-open policy avoids coupling community-service follow availability to
 * account-service availability.
 */
@Slf4j
@Component
public class AccountExistenceClient implements ArtistAccountChecker {

    private final RestClient restClient;

    public AccountExistenceClient(
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
    public void assertExists(String artistAccountId) {
        try {
            restClient.get()
                    .uri("/internal/accounts/{id}", artistAccountId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ArtistNotFoundException(artistAccountId);
            }
            log.warn("account-service existence check failed for {}: {}", artistAccountId, e.getMessage());
        } catch (Exception e) {
            log.warn("account-service existence check failed for {}: {}", artistAccountId, e.getMessage());
        }
    }
}
