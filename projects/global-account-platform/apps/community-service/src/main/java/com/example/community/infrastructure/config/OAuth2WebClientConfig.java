package com.example.community.infrastructure.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Builds {@link WebClient} instances for outbound calls to account-service and
 * membership-service that automatically attach OAuth2 access tokens
 * ({@code client_credentials} grant) using the registrations declared in
 * {@code spring.security.oauth2.client.registration.*} (TASK-BE-253).
 *
 * <p>Replaces the previous {@code RestClient}-based clients that relied on
 * {@code X-Internal-Token} headers. The {@link OAuth2AuthorizedClientManager}
 * caches access tokens until expiry and refreshes them automatically — including
 * a 401-driven retry orchestrated by Spring Security.
 *
 * <p>Servlet stack note: {@link ServletOAuth2AuthorizedClientExchangeFilterFunction}
 * is the servlet-friendly variant of the reactive client filter. It cooperates
 * with {@link WebClient} but does not require WebFlux at the controller layer.
 */
@Configuration
public class OAuth2WebClientConfig {

    /** Registration id used when calling account-service. */
    public static final String GAP_ACCOUNT_REGISTRATION_ID = "gap-account";

    /** Registration id used when calling membership-service. */
    public static final String GAP_MEMBERSHIP_REGISTRATION_ID = "gap-membership";

    /** Bean qualifier for the WebClient pre-bound to {@code gap-account}. */
    public static final String ACCOUNT_WEB_CLIENT = "accountServiceWebClient";

    /** Bean qualifier for the WebClient pre-bound to {@code gap-membership}. */
    public static final String MEMBERSHIP_WEB_CLIENT = "membershipServiceWebClient";

    @Value("${community.account-service.base-url}")
    private String accountServiceBaseUrl;

    @Value("${community.membership-service.base-url}")
    private String membershipServiceBaseUrl;

    @Value("${community.account-service.connect-timeout-ms:2000}")
    private int accountConnectTimeoutMs;

    @Value("${community.account-service.read-timeout-ms:3000}")
    private int accountReadTimeoutMs;

    @Value("${community.membership-service.connect-timeout-ms:2000}")
    private int membershipConnectTimeoutMs;

    @Value("${community.membership-service.read-timeout-ms:3000}")
    private int membershipReadTimeoutMs;

    /**
     * Builds an {@link OAuth2AuthorizedClientManager} suitable for
     * {@code client_credentials} grant. Service-to-service flows are unauthenticated
     * from a user perspective, so we use {@link AuthorizedClientServiceOAuth2AuthorizedClientManager}
     * which does not depend on a {@code SecurityContext}.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build());
        return manager;
    }

    @Bean(ACCOUNT_WEB_CLIENT)
    public WebClient accountServiceWebClient(OAuth2AuthorizedClientManager manager) {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(manager);
        oauth2.setDefaultClientRegistrationId(GAP_ACCOUNT_REGISTRATION_ID);
        return WebClient.builder()
                .baseUrl(accountServiceBaseUrl)
                .apply(oauth2.oauth2Configuration())
                .clientConnector(connector(accountConnectTimeoutMs, accountReadTimeoutMs))
                .build();
    }

    @Bean(MEMBERSHIP_WEB_CLIENT)
    public WebClient membershipServiceWebClient(OAuth2AuthorizedClientManager manager) {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(manager);
        oauth2.setDefaultClientRegistrationId(GAP_MEMBERSHIP_REGISTRATION_ID);
        return WebClient.builder()
                .baseUrl(membershipServiceBaseUrl)
                .apply(oauth2.oauth2Configuration())
                .clientConnector(connector(membershipConnectTimeoutMs, membershipReadTimeoutMs))
                .build();
    }

    /**
     * Builds a Reactor Netty connector with TCP connect + response timeouts applied
     * (TASK-BE-269: restore the 2s/3s caller constraint declared in
     * {@code community-to-{account,membership}.md}).
     */
    private static ReactorClientHttpConnector connector(int connectTimeoutMs, int readTimeoutMs) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(readTimeoutMs));
        return new ReactorClientHttpConnector(httpClient);
    }
}
