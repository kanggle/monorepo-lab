package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.result.ConsumerSiteMembershipLookupResult;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-616 — {@code AccountServiceClient#consentToConsumerSite}: {@code PUT
 * /internal/tenants/{site}/consumer-members/{accountId}} with the workload bearer and no
 * {@code X-Tenant-Id}; the read's answer shape; every non-200 (404 included) is a failure.
 */
@DisplayName("AccountServiceClient — 첫 방문 동의 PUT (TASK-BE-616)")
class AccountServiceClientConsentTest {

    private static final String PATH = "/internal/tenants/fan-platform/consumer-members/acc-616";

    private WireMockServer wireMockServer;
    private AccountServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        client = new AccountServiceClient(wireMockServer.baseUrl(), 3000, 5000, tokenProvider);
        RestClient http11 = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
                .build();
        ReflectionTestUtils.setField(client, "cachedRestClient", http11);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("200 ACTIVE → 활성 멤버 결과 · PUT · Bearer · X-Tenant-Id 없음")
    void consent_200_activeMember() {
        wireMockServer.stubFor(put(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"accountId":"acc-616","siteTenantId":"fan-platform","consumerSite":true,
                         "siteTenantType":"B2C_CONSUMER","membershipStatus":"ACTIVE","siteRoles":[]}
                        """)));

        ConsumerSiteMembershipLookupResult result = client.consentToConsumerSite("fan-platform", "acc-616");

        assertThat(result.isActiveMember()).isTrue();
        assertThat(result.siteTenantId()).isEqualTo("fan-platform");
        wireMockServer.verify(putRequestedFor(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withHeader("X-Tenant-Id", absent()));
    }

    @Test
    @DisplayName("200 이지만 ACTIVE 아님(정지 사이트 등) → 활성 멤버 아님 (예외 아님)")
    void consent_200_notActive() {
        wireMockServer.stubFor(put(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"accountId":"acc-616","siteTenantId":"fan-platform","consumerSite":true,
                         "siteTenantType":"B2C_CONSUMER","membershipStatus":null,"siteRoles":[]}
                        """)));

        assertThat(client.consentToConsumerSite("fan-platform", "acc-616").isActiveMember()).isFalse();
    }

    @Test
    @DisplayName("404(옛 account-service — 엔드포인트 없음) → 실패")
    void consent_404_isFailure() {
        wireMockServer.stubFor(put(urlEqualTo(PATH)).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.consentToConsumerSite("fan-platform", "acc-616"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @DisplayName("consumerSite 없는 200 → 읽을 수 없는 답 = 실패")
    void consent_unreadable200_isFailure() {
        wireMockServer.stubFor(put(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{}")));

        assertThatThrownBy(() -> client.consentToConsumerSite("fan-platform", "acc-616"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }
}
