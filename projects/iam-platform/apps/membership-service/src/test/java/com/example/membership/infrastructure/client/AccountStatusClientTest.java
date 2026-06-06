package com.example.membership.infrastructure.client;

import com.example.membership.application.exception.AccountStatusUnavailableException;
import com.example.membership.domain.account.AccountStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AccountStatusClient 단위 테스트")
class AccountStatusClientTest {

    private static final String STATUS_PATH = "/internal/accounts/acc-1/status";

    private WireMockServer wireMock;
    private AccountStatusClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        // TASK-BE-318d: token provider mocked — returns a fixed bearer for the header assertion.
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        client = new AccountStatusClient(wireMock.baseUrl(), 2000, 3000, tokenProvider);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("200 응답 → AccountStatus 반환")
    void check_200_returnsStatus() {
        wireMock.stubFor(get(urlPathMatching(STATUS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-1\",\"status\":\"ACTIVE\"}")));

        assertThat(client.check("acc-1")).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("TASK-BE-318d: status 호출에 Authorization: Bearer 헤더를 첨부하고 X-Internal-Token 은 보내지 않는다")
    void check_attachesBearerHeader_noXInternalToken() {
        wireMock.stubFor(get(urlPathMatching(STATUS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-1\",\"status\":\"ACTIVE\"}")));

        client.check("acc-1");

        wireMock.verify(getRequestedFor(urlPathMatching(STATUS_PATH))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withoutHeader("X-Internal-Token"));
    }

    @Test
    @DisplayName("5xx 응답 → AccountStatusUnavailableException (fail-closed)")
    void check_5xx_throwsUnavailable() {
        wireMock.stubFor(get(urlPathMatching(STATUS_PATH))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.check("acc-1"))
                .isInstanceOf(AccountStatusUnavailableException.class);
    }

    @Test
    @DisplayName("네트워크 오류 → AccountStatusUnavailableException")
    void check_networkFault_throwsUnavailable() {
        wireMock.stubFor(get(urlPathMatching(STATUS_PATH))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.check("acc-1"))
                .isInstanceOf(AccountStatusUnavailableException.class);
    }
}
