package com.example.product.infrastructure.config;

import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression guard for {@link IamTokenProviderConfig}'s wiring of the shared
 * {@link IamClientCredentialsTokenProvider} (ADR-MONO-058 § D6, TASK-BE-568). Confirms the
 * two defects product-service's now-deleted local copy carried are actually closed through
 * THIS service's own bean-wiring path — not just that the class compiles against the new
 * type (TASK-BE-568 Failure Scenarios: "verify the actual Basic-auth bytes sent, not just
 * that the class compiles against the new type"). The shared class's own UTF-8/timeout unit
 * tests already live in {@code libs/java-security} (TASK-MONO-501); this is the
 * service-local guard the task's Acceptance Criteria / Test Requirements explicitly ask for.
 */
@DisplayName("IamTokenProviderConfig (TASK-BE-568) — UTF-8 Basic-auth + explicit timeouts")
class IamTokenProviderConfigTest {

    private final IamTokenProviderConfig config = new IamTokenProviderConfig();

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

    private String tokenUri() {
        return wireMock.baseUrl() + "/oauth2/token";
    }

    @Test
    @DisplayName("RFC 7617: the wired provider UTF-8-encodes the Basic-auth header, not the JVM platform-default charset")
    void basicAuthHeaderIsUtf8Encoded() {
        // Non-ASCII client-id/secret: UTF-8 and ISO-8859-1 (a plausible platform-default
        // charset on a non-UTF-8 host) encode these code points to DIFFERENT byte sequences,
        // so this fails loudly if the wiring ever regressed to String.getBytes() (platform
        // default) instead of an explicit UTF-8 charset — the exact defect TASK-BE-568 closes.
        String clientId = "product-svc-éclient";
        String clientSecret = "sécrèt-카팍";
        wireMock.stubFor(post(urlPathEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"tok\",\"expires_in\":1800}")));

        IamClientCredentialsTokenProvider provider = config.iamClientCredentialsTokenProvider(
                tokenUri(), clientId, clientSecret, 5000L, 5000L);

        assertThat(provider.currentBearer()).isEqualTo("tok");

        String expectedUtf8Header = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String wrongIso88591Header = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.ISO_8859_1));
        assertThat(expectedUtf8Header).isNotEqualTo(wrongIso88591Header); // sanity: fixture actually distinguishes the two charsets

        wireMock.verify(postRequestedFor(urlPathEqualTo("/oauth2/token"))
                .withHeader("Authorization", equalTo(expectedUtf8Header)));
        String actualHeader = wireMock.getAllServeEvents().get(0).getRequest().getHeader("Authorization");
        assertThat(actualHeader).isEqualTo(expectedUtf8Header);
        assertThat(actualHeader).isNotEqualTo(wrongIso88591Header);
    }

    @Test
    @Timeout(10)
    @DisplayName("a configured read-timeout is honored: a hung IAM token endpoint fails fast instead of blocking indefinitely (closes the RestClient.create() zero-timeout defect)")
    void readTimeoutIsHonored() {
        wireMock.stubFor(post(urlPathEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(5000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"tok\",\"expires_in\":1800}")));

        IamClientCredentialsTokenProvider provider = config.iamClientCredentialsTokenProvider(
                tokenUri(), "client", "secret", 5000L, 300L);

        assertThatThrownBy(provider::currentBearer).isInstanceOf(RestClientException.class);
    }

    @Test
    @DisplayName("no scope is sent (product-service's IAM token endpoint has no registered scope, unlike batch-worker/fan-platform)")
    void tokenRequestBodyOmitsScope() {
        wireMock.stubFor(post(urlPathEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"tok\",\"expires_in\":1800}")));

        IamClientCredentialsTokenProvider provider = config.iamClientCredentialsTokenProvider(
                tokenUri(), "client", "secret", 5000L, 5000L);
        provider.currentBearer();

        wireMock.verify(postRequestedFor(urlPathEqualTo("/oauth2/token"))
                .withRequestBody(equalTo("grant_type=client_credentials")));
    }
}
