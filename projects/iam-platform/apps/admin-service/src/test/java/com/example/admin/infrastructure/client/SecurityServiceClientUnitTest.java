package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SecurityServiceClient 단위 테스트")
class SecurityServiceClientUnitTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO   = Instant.parse("2026-01-02T00:00:00Z");

    private WireMockServer wireMock;
    private SecurityServiceClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        // TASK-BE-318b: token provider mocked — returns a fixed bearer for the header assertion.
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        // Constructor explicitly sets HTTP_1_1 — no h2c fix needed.
        client = new SecurityServiceClient(wireMock.baseUrl(), 3000, 5000, tokenProvider);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("queryLoginHistory — 200 응답 → LoginHistoryEntry 리스트 반환")
    void queryLoginHistory_200_returnsList() {
        wireMock.stubFor(get(urlPathMatching("/internal/security/login-history.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"content\":[{\"eventId\":\"evt-1\",\"accountId\":\"acc-1\"," +
                                "\"outcome\":\"SUCCESS\",\"ipMasked\":\"1.2.3.*\"," +
                                "\"geoCountry\":\"KR\",\"occurredAt\":\"2026-01-01T12:00:00Z\"}]}")));

        List<SecurityServiceClient.LoginHistoryEntry> entries =
                client.queryLoginHistory("ecommerce", "acc-1", FROM, TO);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).eventId()).isEqualTo("evt-1");
        assertThat(entries.get(0).outcome()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("🔴 TASK-BE-656: querySuspiciousEvents — the contract's body (id · ruleCode · detectedAt, security-query-api.md) is read, not nulls")
    void querySuspiciousEvents_200_contractShape_returnsList() {
        // the body security-service actually sends (SuspiciousEventView) — before TASK-BE-656 this stub used
        // eventId/signalType/occurredAt, a shape security-service never sends, so the test stayed green while
        // every real row reached the audit view with a null id, type and time
        wireMock.stubFor(get(urlPathMatching("/internal/security/suspicious-events.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"content\":[{\"id\":\"evt-2\",\"accountId\":\"acc-1\"," +
                                "\"ruleCode\":\"GEO_ANOMALY\",\"riskScore\":85,\"actionTaken\":\"AUTO_LOCK\"," +
                                "\"evidence\":{\"previousCountry\":\"KR\",\"currentCountry\":\"US\"}," +
                                "\"detectedAt\":\"2026-01-01T12:00:00Z\"}]," +
                                "\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}")));

        List<SecurityServiceClient.SuspiciousEventEntry> entries =
                client.querySuspiciousEvents("ecommerce", "acc-1", FROM, TO);

        assertThat(entries).hasSize(1);
        SecurityServiceClient.SuspiciousEventEntry e = entries.get(0);
        assertThat(e.id()).isEqualTo("evt-2");
        assertThat(e.ruleCode()).isEqualTo("GEO_ANOMALY");
        assertThat(e.riskScore()).isEqualTo(85);
        assertThat(e.actionTaken()).isEqualTo("AUTO_LOCK");
        assertThat(e.detectedAt()).isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));
    }

    @Test
    @DisplayName("🔴 TASK-BE-656: both reads send the gated tenant as the tenantId query parameter")
    void queries_sendTenantId() {
        wireMock.stubFor(get(urlPathMatching("/internal/security/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"content\":[]}")));

        client.queryLoginHistory("consumer-pool", "acc-1", FROM, TO);
        client.querySuspiciousEvents("ecommerce", "acc-2", FROM, TO);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/internal/security/login-history"))
                .withQueryParam("tenantId", equalTo("consumer-pool"))
                .withQueryParam("accountId", equalTo("acc-1")));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/internal/security/suspicious-events"))
                .withQueryParam("tenantId", equalTo("ecommerce"))
                .withQueryParam("accountId", equalTo("acc-2")));
    }

    @Test
    @DisplayName("TASK-BE-656: the platform-scope '*' arrives as '*'")
    void queries_sendWildcardTenant() {
        wireMock.stubFor(get(urlPathMatching("/internal/security/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"content\":[]}")));

        client.queryLoginHistory("*", "acc-1", FROM, TO);
        client.querySuspiciousEvents("*", "acc-1", FROM, TO);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/internal/security/login-history"))
                .withQueryParam("tenantId", equalTo("*")));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/internal/security/suspicious-events"))
                .withQueryParam("tenantId", equalTo("*")));
    }

    @Test
    @DisplayName("TASK-BE-318b: 호출에 Authorization: Bearer 헤더를 첨부하고 X-Internal-Token 은 보내지 않는다")
    void query_attachesBearerHeader_noXInternalToken() {
        wireMock.stubFor(get(urlPathMatching("/internal/security/login-history.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"content\":[]}")));

        client.queryLoginHistory("ecommerce", "acc-1", FROM, TO);

        wireMock.verify(getRequestedFor(urlPathMatching("/internal/security/login-history.*"))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withoutHeader("X-Internal-Token"));
    }

    @Test
    @DisplayName("queryLoginHistory — 4xx 응답 → NonRetryableDownstreamException")
    void queryLoginHistory_4xx_throwsNonRetryableDownstreamException() {
        wireMock.stubFor(get(urlPathMatching("/internal/security/login-history.*"))
                .willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> client.queryLoginHistory("ecommerce", "acc-1", FROM, TO))
                .isInstanceOf(NonRetryableDownstreamException.class);
    }

    @Test
    @DisplayName("queryLoginHistory — 5xx 응답 → DownstreamFailureException (NonRetryable 아님)")
    void queryLoginHistory_5xx_throwsDownstreamFailureException() {
        wireMock.stubFor(get(urlPathMatching("/internal/security/login-history.*"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.queryLoginHistory("ecommerce", "acc-1", FROM, TO))
                .isInstanceOf(DownstreamFailureException.class)
                .isNotInstanceOf(NonRetryableDownstreamException.class);
    }

    @Test
    @DisplayName("네트워크 오류 → DownstreamFailureException")
    void networkFault_throwsDownstreamFailureException() {
        wireMock.stubFor(get(urlPathMatching("/internal/security/login-history.*"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.queryLoginHistory("ecommerce", "acc-1", FROM, TO))
                .isInstanceOf(DownstreamFailureException.class);
    }
}
