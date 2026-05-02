package com.example.community.infrastructure.client;

import com.example.community.domain.access.AccountProfileLookup;
import com.example.community.infrastructure.config.OAuth2WebClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Looks up account display names from account-service.
 *
 * <p>TASK-BE-253: outbound auth switched to standard OAuth2 (see
 * {@link OAuth2WebClientConfig}); failures still return {@code null} so that
 * post bodies render even when account-service is degraded.
 */
@Slf4j
@Component
public class AccountProfileClient implements AccountProfileLookup {

    private final WebClient webClient;

    public AccountProfileClient(
            @Qualifier(OAuth2WebClientConfig.ACCOUNT_WEB_CLIENT) WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    @Cacheable(cacheNames = "accountProfiles", key = "#accountId", unless = "#result == null")
    public String displayNameOf(String accountId) {
        if (accountId == null) return null;
        try {
            ProfileResponse resp = webClient.get()
                    .uri("/internal/accounts/{id}/profile", accountId)
                    .retrieve()
                    .bodyToMono(ProfileResponse.class)
                    .block();
            return resp == null ? null : resp.displayName();
        } catch (Exception e) {
            log.warn("account-service profile lookup failed for {}: {}",
                    accountId, e.getMessage());
            return null;
        }
    }

    public record ProfileResponse(String accountId, String displayName) {}
}
