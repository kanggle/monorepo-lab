package com.example.community.infrastructure.client;

import com.example.community.domain.access.ArtistNotFoundException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link AccountExistenceClient} with a plain {@link WebClient} (no OAuth2 filter).
 *
 * <p>The OAuth2 token-attachment filter is verified separately in
 * {@link com.example.community.infrastructure.config.OAuth2WebClientConfigTest}.
 */
@DisplayName("AccountExistenceClient 단위 테스트")
class AccountExistenceClientTest {

    private WireMockServer wireMock;
    private AccountExistenceClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        WebClient webClient = WebClient.builder().baseUrl(wireMock.baseUrl()).build();
        client = new AccountExistenceClient(webClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("200 응답 → 예외 없이 정상 반환")
    void assertExists_200_doesNotThrow() {
        wireMock.stubFor(get(urlPathMatching("/internal/accounts/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"artist-1\",\"email\":\"a@b.com\",\"status\":\"ACTIVE\"}")));

        assertThatCode(() -> client.assertExists("artist-1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("404 응답 → ArtistNotFoundException")
    void assertExists_404_throwsArtistNotFoundException() {
        wireMock.stubFor(get(urlPathMatching("/internal/accounts/.*"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.assertExists("artist-ghost"))
                .isInstanceOf(ArtistNotFoundException.class);
    }

    @Test
    @DisplayName("503 응답 → fail-open: 예외 없이 정상 반환")
    void assertExists_503_doesNotThrow() {
        wireMock.stubFor(get(urlPathMatching("/internal/accounts/.*"))
                .willReturn(aResponse().withStatus(503)));

        assertThatCode(() -> client.assertExists("artist-err"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("네트워크 오류 → fail-open: 예외 없이 정상 반환")
    void assertExists_networkFault_doesNotThrow() {
        wireMock.stubFor(get(urlPathMatching("/internal/accounts/.*"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatCode(() -> client.assertExists("artist-fault"))
                .doesNotThrowAnyException();
    }
}
