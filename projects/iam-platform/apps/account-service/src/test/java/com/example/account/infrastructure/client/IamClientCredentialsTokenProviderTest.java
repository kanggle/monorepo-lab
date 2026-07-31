package com.example.account.infrastructure.client;

import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
 * account-service-side wiring tests for the canonical {@code libs/java-security}
 * {@link IamClientCredentialsTokenProvider} (TASK-BE-568, ADR-MONO-058 § D6 — replaces the
 * previous per-service local copy, TASK-BE-487). Added for parity with the sibling
 * auth/admin/security-service wiring tests — account-service previously had no dedicated
 * provider test of its own. Exercises the provider constructed with account-service's real
 * {@code client-id} default (seeded in auth-service V0019) and the {@code internal.invoke}
 * scope its {@code /internal/**} callers require, so the header/body bytes asserted here are
 * exactly what production account-service sends — not just "it compiles". The class's own
 * exhaustive behavioural test suite (caching, refresh-skew, timeout enforcement,
 * scope-is-a-parameter) lives in {@code libs/java-security}.
 */
@DisplayName("IamClientCredentialsTokenProvider 단위 테스트 (account-service wiring)")
class IamClientCredentialsTokenProviderTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

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
                wireMock.baseUrl() + "/oauth2/token", "account-service-client", "secret",
                "internal.invoke", DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);
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
        // RFC 7617: UTF-8, not the JVM platform-default charset (TASK-BE-568 closes this defect —
        // the deleted local copy used the platform-default-charset `.getBytes()`).
        String expectedBasic = "Basic " + Base64.getEncoder()
                .encodeToString("account-service-client:secret".getBytes(StandardCharsets.UTF_8));
        wireMock.verify(postRequestedFor(urlEqualTo("/oauth2/token"))
                .withHeader("Authorization", equalTo(expectedBasic))
                .withRequestBody(equalTo("grant_type=client_credentials&scope=internal.invoke")));
    }

    @Test
    @DisplayName("유효한 캐시 토큰은 재사용되어 토큰 엔드포인트를 한 번만 호출한다")
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
