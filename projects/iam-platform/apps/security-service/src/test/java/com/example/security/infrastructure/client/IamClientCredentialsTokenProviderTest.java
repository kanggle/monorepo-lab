package com.example.security.infrastructure.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IamClientCredentialsTokenProvider} (TASK-BE-318):
 * fetches a client_credentials token via Basic auth and caches it (AC-5).
 */
@DisplayName("IamClientCredentialsTokenProvider 단위 테스트")
class IamClientCredentialsTokenProviderTest {

    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private IamClientCredentialsTokenProvider provider() {
        return new IamClientCredentialsTokenProvider(
                wireMock.baseUrl() + "/oauth2/token", "security-service-client", "secret");
    }

    @Test
    @DisplayName("client_credentials 토큰을 Basic auth 로 발급받고 access_token 을 반환한다")
    void fetchesToken_withBasicAuth() {
        wireMock.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"jwt-abc\",\"expires_in\":1800,\"token_type\":\"Bearer\"}")));

        String token = provider().currentBearer();

        assertThat(token).isEqualTo("jwt-abc");
        String expectedBasic = "Basic " + Base64.getEncoder()
                .encodeToString("security-service-client:secret".getBytes());
        wireMock.verify(postRequestedFor(urlEqualTo("/oauth2/token"))
                .withHeader("Authorization", equalTo(expectedBasic))
                .withRequestBody(equalTo("grant_type=client_credentials&scope=internal.invoke")));
    }

    @Test
    @DisplayName("AC-5: 유효한 캐시 토큰은 재사용되어 토큰 엔드포인트를 한 번만 호출한다")
    void cachesToken_singleFetchForMultipleCalls() {
        wireMock.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"jwt-abc\",\"expires_in\":1800,\"token_type\":\"Bearer\"}")));

        IamClientCredentialsTokenProvider provider = provider();
        String first = provider.currentBearer();
        String second = provider.currentBearer();
        String third = provider.currentBearer();

        assertThat(first).isEqualTo("jwt-abc");
        assertThat(second).isEqualTo("jwt-abc");
        assertThat(third).isEqualTo("jwt-abc");
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo("/oauth2/token")));
    }
}
