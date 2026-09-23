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

    /**
     * 🔴🔴 <b>이 칸은 뒤집혔다 (TASK-MONO-717, 소유자 결정 ⓐ).</b> 전에는
     * {@code "no scope is sent (product-service's IAM token endpoint has no registered
     * scope)"} 를 단언했다 — 즉 <b>결함을 얼리고 있었다</b>.
     *
     * <p>그 전제가 2026-09-22/23 에 반증됐다: {@code product-service-client} 는 IdP 의
     * {@code oauth_clients} 시드 <b>전수에 없었고</b>, 그래서 「등록된 스코프가 없다」는
     * 「등록 자체가 없다」였다. 그 상태에서 이 프로비저닝은 <b>한 번도 성공한 적이 없다</b>
     * (데모 창 실측: {@code Connection refused} → 주소를 고쳐도 {@code invalid_client}).
     *
     * <p>{@code V0036} 이 그 클라이언트를 {@code ["internal.invoke"]} 로 등록했고,
     * account-service 는 {@code internalTokenValidator()} 로 그 스코프를 <b>핀</b>한다
     * (TASK-BE-514) — 스코프가 없는 토큰은 서명과 issuer 가 멀쩡해도 거절된다.
     * ⇒ 이제 <b>보내는 것이 옳다</b>.
     *
     * <p>🔵 서버 기본(생략 시 등록된 스코프 전부 부여)에 기대지 않는 이유: 그러면 이
     * 서비스의 토큰 내용이 <b>아무 데도 안 적힌 서버 측 기본값</b>에 달리고, 등록에 스코프가
     * 하나 더 붙는 날 이 토큰이 조용히 넓어진다.
     */
    @Test
    @DisplayName("the internal.invoke scope IS sent — account-service pins it on /internal/** (TASK-MONO-717 ⓐ; this cell used to pin the opposite)")
    void tokenRequestBodyCarriesTheInternalInvokeScope() {
        wireMock.stubFor(post(urlPathEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"tok\",\"expires_in\":1800}")));

        IamClientCredentialsTokenProvider provider = config.iamClientCredentialsTokenProvider(
                tokenUri(), "client", "secret", 5000L, 5000L);
        provider.currentBearer();

        // 🔴 문자열 전체로 단언한다 — `contains` 로 재면 스코프가 **빈 값**이어도 통과한다.
        wireMock.verify(postRequestedFor(urlPathEqualTo("/oauth2/token"))
                .withRequestBody(equalTo("grant_type=client_credentials&scope=internal.invoke")));
    }
}
