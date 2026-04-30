package com.example.community.infrastructure.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccountProfileClient 단위 테스트")
class AccountProfileClientUnitTest {

    private WireMockServer wireMock;
    private AccountProfileClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        // HTTP_1_1 explicit in constructor — no h2c fix needed.
        client = new AccountProfileClient(wireMock.baseUrl(), 3000, 5000);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("200 응답 → displayName 반환")
    void displayNameOf_200_returnsDisplayName() {
        wireMock.stubFor(get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-1\",\"displayName\":\"Alice\"}")));

        assertThat(client.displayNameOf("acc-1")).isEqualTo("Alice");
    }

    @Test
    @DisplayName("4xx 응답 → null 반환 (fail-silent)")
    void displayNameOf_4xx_returnsNull() {
        wireMock.stubFor(get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.displayNameOf("acc-404")).isNull();
    }

    @Test
    @DisplayName("5xx 응답 → null 반환 (fail-silent)")
    void displayNameOf_5xx_returnsNull() {
        wireMock.stubFor(get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(aResponse().withStatus(503)));

        assertThat(client.displayNameOf("acc-err")).isNull();
    }

    @Test
    @DisplayName("네트워크 오류 → null 반환 (fail-silent)")
    void displayNameOf_networkFault_returnsNull() {
        wireMock.stubFor(get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThat(client.displayNameOf("acc-fault")).isNull();
    }

    @Test
    @DisplayName("null accountId 입력 → null 반환 (HTTP 호출 없음)")
    void displayNameOf_null_returnsNull() {
        assertThat(client.displayNameOf(null)).isNull();
        assertThat(wireMock.getServeEvents().getRequests()).isEmpty();
    }
}
