package com.example.community.infrastructure.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Unit-level verification of TASK-BE-253's outbound OAuth2 token-attachment flow.
 *
 * <p>Builds an {@link OAuth2AuthorizedClientManager} and a {@link WebClient} configured
 * with {@link ServletOAuth2AuthorizedClientExchangeFilterFunction} the same way
 * {@link OAuth2WebClientConfig} does in production, then verifies that:
 * <ul>
 *   <li>The token endpoint is invoked when the WebClient calls a protected resource.</li>
 *   <li>The {@code Authorization: Bearer <access-token>} header is attached on the
 *       outbound request.</li>
 * </ul>
 *
 * <p>Avoids the Spring Boot context to keep the test fast and free of auto-config
 * coupling.
 */
@DisplayName("OAuth2WebClient — bearer token attachment (no Spring context)")
class OAuth2WebClientConfigUnitTest {

    private WireMockServer authServer;
    private WireMockServer resourceServer;

    @BeforeEach
    void start() {
        authServer = new WireMockServer(wireMockConfig().dynamicPort());
        authServer.start();
        resourceServer = new WireMockServer(wireMockConfig().dynamicPort());
        resourceServer.start();
    }

    @AfterEach
    void stop() {
        if (authServer != null) authServer.stop();
        if (resourceServer != null) resourceServer.stop();
    }

    @Test
    @DisplayName("WebClient 호출 시 token endpoint를 거쳐 Authorization: Bearer 헤더가 첨부된다")
    void attaches_bearer_token_from_token_endpoint() {
        // ── Token endpoint stub: returns a fixed access token.
        authServer.stubFor(post(urlPathEqualTo("/oauth2/token"))
                .willReturn(okJson("{\"access_token\":\"unit-test-token\","
                        + "\"token_type\":\"Bearer\",\"expires_in\":1800,\"scope\":\"account.read\"}")));
        // ── Resource server stub: any GET succeeds.
        resourceServer.stubFor(get(urlPathMatching("/internal/.*"))
                .willReturn(aResponse().withStatus(200)));

        ClientRegistration registration = ClientRegistration.withRegistrationId("gap-account")
                .clientId("community-service-client")
                .clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("account.read")
                .tokenUri(authServer.baseUrl() + "/oauth2/token")
                .build();
        ClientRegistrationRepository repo = new InMemoryClientRegistrationRepository(registration);
        OAuth2AuthorizedClientService service = new InMemoryOAuth2AuthorizedClientService(repo);

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(repo, service);
        // Wire the client_credentials-only provider, mirroring OAuth2WebClientConfig.
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials(c ->
                        c.accessTokenResponseClient(
                                new RestClientClientCredentialsTokenResponseClient())).build());

        ServletOAuth2AuthorizedClientExchangeFilterFunction filter =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(manager);
        filter.setDefaultClientRegistrationId("gap-account");

        WebClient webClient = WebClient.builder()
                .baseUrl(resourceServer.baseUrl())
                .apply(filter.oauth2Configuration())
                .build();

        // Call the resource — the OAuth2 filter should drive a token endpoint call first.
        webClient.get()
                .uri("/internal/accounts/some-id")
                .retrieve()
                .toBodilessEntity()
                .block();

        // Assertions: token endpoint was invoked, and resource call carried the token.
        authServer.verify(postRequestedFor(urlPathEqualTo("/oauth2/token")));
        resourceServer.verify(getRequestedFor(urlPathEqualTo("/internal/accounts/some-id"))
                .withHeader("Authorization", equalTo("Bearer unit-test-token")));
    }
}
