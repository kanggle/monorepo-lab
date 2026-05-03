package com.example.account.infrastructure.client;

import com.example.account.application.port.AuthServicePort;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuthServiceClient 단위 테스트")
class AuthServiceClientUnitTest {

    private static final String CREDENTIALS_PATH = "/internal/auth/credentials";

    private WireMockServer wireMockServer;
    private AuthServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        // AuthServiceClient now enforces HTTP/1.1 internally, so no workaround needed here.
        client = new AuthServiceClient(wireMockServer.baseUrl(), 3000, 5000);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("createCredential — 200 응답 → 예외 없이 완료")
    void createCredential_success_noException() {
        wireMockServer.stubFor(post(urlEqualTo(CREDENTIALS_PATH))
                .willReturn(aResponse().withStatus(200)));

        assertThatNoException().isThrownBy(() ->
                client.createCredential("acc-1", "user@example.com", "pass123"));
    }

    @Test
    @DisplayName("createCredential — 409 응답 → CredentialAlreadyExistsConflict")
    void createCredential_conflict409_throwsCredentialAlreadyExistsConflict() {
        wireMockServer.stubFor(post(urlEqualTo(CREDENTIALS_PATH))
                .willReturn(aResponse().withStatus(409)));

        assertThatThrownBy(() -> client.createCredential("acc-2", "dup@example.com", "pass123"))
                .isInstanceOf(AuthServicePort.CredentialAlreadyExistsConflict.class);
    }

    @Test
    @DisplayName("createCredential — 기타 4xx 응답 → AuthServiceUnavailable")
    void createCredential_otherClientError_throwsAuthServiceUnavailable() {
        wireMockServer.stubFor(post(urlEqualTo(CREDENTIALS_PATH))
                .willReturn(aResponse().withStatus(422)));

        assertThatThrownBy(() -> client.createCredential("acc-3", "user@example.com", "pass123"))
                .isInstanceOf(AuthServicePort.AuthServiceUnavailable.class);
    }

    @Test
    @DisplayName("createCredential — 네트워크 오류 (연결 끊김) → AuthServiceUnavailable (fail-closed, retry 후)")
    void createCredential_networkFault_throwsAuthServiceUnavailable() {
        wireMockServer.stubFor(post(urlEqualTo(CREDENTIALS_PATH))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.createCredential("acc-err", "user@example.com", "pass123"))
                .isInstanceOf(AuthServicePort.AuthServiceUnavailable.class);
    }
}
