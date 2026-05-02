package com.example.community.infrastructure.client;

import com.example.community.domain.access.ArtistAccountChecker;
import com.example.community.domain.access.ArtistNotFoundException;
import com.example.community.infrastructure.config.OAuth2WebClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Verifies artist account existence by calling account-service's internal lookup.
 *
 * <p>TASK-BE-253: outbound auth was switched from {@code X-Internal-Token} header
 * to standard OAuth2 {@code Authorization: Bearer <client_credentials access token>}.
 * The token is automatically attached and refreshed by
 * {@link org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction}
 * configured on the injected {@link WebClient}.
 *
 * <p>Failure semantics preserved:
 * <ul>
 *   <li>404 → {@link ArtistNotFoundException} (strict)</li>
 *   <li>5xx / timeout / network error → fail-open: log warn, return normally</li>
 * </ul>
 */
@Slf4j
@Component
public class AccountExistenceClient implements ArtistAccountChecker {

    private final WebClient webClient;

    public AccountExistenceClient(
            @Qualifier(OAuth2WebClientConfig.ACCOUNT_WEB_CLIENT) WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public void assertExists(String artistAccountId) {
        try {
            webClient.get()
                    .uri("/internal/accounts/{id}", artistAccountId)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ArtistNotFoundException(artistAccountId);
            }
            log.warn("account-service existence check failed for {}: {}",
                    artistAccountId, e.getMessage());
        } catch (Exception e) {
            log.warn("account-service existence check failed for {}: {}",
                    artistAccountId, e.getMessage());
        }
    }
}
