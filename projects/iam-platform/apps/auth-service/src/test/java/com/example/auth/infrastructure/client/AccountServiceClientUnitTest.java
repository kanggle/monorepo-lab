package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.result.AccountProfileResult;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.application.result.SocialSignupResult;

import java.util.List;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AccountServiceClient 단위 테스트")
class AccountServiceClientUnitTest {

    private static final String STATUS_PATH = "/internal/accounts/acc-1/status";
    private static final String SOCIAL_SIGNUP_PATH = "/internal/accounts/social-signup";

    private WireMockServer wireMockServer;
    private AccountServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        // TASK-BE-318c: token provider mocked — returns a fixed bearer for the header assertion.
        // The per-call setBearerAuth(...) is applied at the request-builder level, so it survives
        // the cachedRestClient reflection-replacement below.
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        client = new AccountServiceClient(wireMockServer.baseUrl(), 3000, 5000, tokenProvider);
        // JDK HttpClient defaults to HTTP/2 (H2C) which causes RST_STREAM with WireMock.
        // Replace with an HTTP/1.1-only client so stubs are served predictably.
        HttpClient jdkHttp11 = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        RestClient http11RestClient = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(jdkHttp11))
                .build();
        // TASK-MONO-046-1 Cluster C: AccountServiceClient now caches the RestClient
        // under the field {@code cachedRestClient}, keyed by base URL. Override that
        // field with our HTTP/1.1 client so WireMock serves stubs predictably.
        ReflectionTestUtils.setField(client, "cachedRestClient", http11RestClient);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ── getAccountStatus ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getAccountStatus — 200 응답 → accountId/status 반환")
    void getAccountStatus_200_returnsResult() {
        wireMockServer.stubFor(get(urlEqualTo(STATUS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-1\",\"status\":\"ACTIVE\"}")));

        Optional<AccountStatusLookupResult> result = client.getAccountStatus("acc-1");

        assertThat(result).isPresent();
        assertThat(result.get().accountId()).isEqualTo("acc-1");
        assertThat(result.get().accountStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("TASK-BE-318c: status 호출에 Authorization: Bearer 헤더를 첨부한다")
    void getAccountStatus_attachesBearerHeader() {
        wireMockServer.stubFor(get(urlEqualTo(STATUS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-1\",\"status\":\"ACTIVE\"}")));

        client.getAccountStatus("acc-1");

        wireMockServer.verify(getRequestedFor(urlEqualTo(STATUS_PATH))
                .withHeader("Authorization", equalTo("Bearer test-jwt")));
    }

    @Test
    @DisplayName("getAccountStatus — 404 응답 → Optional.empty()")
    void getAccountStatus_404_returnsEmpty() {
        wireMockServer.stubFor(get(urlEqualTo(STATUS_PATH))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.getAccountStatus("acc-1")).isEmpty();
    }

    @Test
    @DisplayName("getAccountStatus — 기타 4xx 응답 → Optional.empty()")
    void getAccountStatus_otherClientError_returnsEmpty() {
        wireMockServer.stubFor(get(urlEqualTo(STATUS_PATH))
                .willReturn(aResponse().withStatus(422)));

        assertThat(client.getAccountStatus("acc-1")).isEmpty();
    }

    @Test
    @DisplayName("getAccountStatus — 네트워크 오류 → AccountServiceUnavailableException (retry 후)")
    void getAccountStatus_networkFault_throwsAccountServiceUnavailable() {
        wireMockServer.stubFor(get(urlEqualTo(STATUS_PATH))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.getAccountStatus("acc-1"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    // ── socialSignup ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("socialSignup — 200 응답 → SocialSignupResult 반환")
    void socialSignup_200_returnsSocialSignupResult() {
        wireMockServer.stubFor(post(urlEqualTo(SOCIAL_SIGNUP_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-new\",\"accountStatus\":\"ACTIVE\",\"newAccount\":true}")));

        SocialSignupResult result = client.socialSignup(
                "user@example.com", "GOOGLE", "google-uid-1", "Alice");

        assertThat(result.accountId()).isEqualTo("acc-new");
        assertThat(result.accountStatus()).isEqualTo("ACTIVE");
        assertThat(result.newAccount()).isTrue();
    }

    @Test
    @DisplayName("socialSignup — 4xx 응답 → AccountServiceUnavailableException")
    void socialSignup_4xx_throwsAccountServiceUnavailable() {
        wireMockServer.stubFor(post(urlEqualTo(SOCIAL_SIGNUP_PATH))
                .willReturn(aResponse().withStatus(422)));

        assertThatThrownBy(() -> client.socialSignup(
                "user@example.com", "GOOGLE", "google-uid-2", "Bob"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @DisplayName("socialSignup — 네트워크 오류 → AccountServiceUnavailableException (retry 후)")
    void socialSignup_networkFault_throwsAccountServiceUnavailable() {
        wireMockServer.stubFor(post(urlEqualTo(SOCIAL_SIGNUP_PATH))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.socialSignup(
                "user@example.com", "GOOGLE", "google-uid-err", "Carol"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    // ── getAccountProfile ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getAccountProfile — 200 응답 → AccountProfileResult 반환")
    void getAccountProfile_200_returnsProfile() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/accounts/acc-1/profile"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "accountId": "acc-1",
                                  "email": "user@example.com",
                                  "emailVerified": true,
                                  "displayName": "Hong Gildong",
                                  "preferredUsername": "gildongh",
                                  "locale": "ko-KR",
                                  "tenantId": "fan-platform",
                                  "tenantType": "B2C"
                                }
                                """)));

        Optional<AccountProfileResult> result = client.getAccountProfile("acc-1");

        assertThat(result).isPresent();
        AccountProfileResult profile = result.get();
        assertThat(profile.accountId()).isEqualTo("acc-1");
        assertThat(profile.email()).isEqualTo("user@example.com");
        assertThat(profile.emailVerified()).isTrue();
        assertThat(profile.displayName()).isEqualTo("Hong Gildong");
        assertThat(profile.preferredUsername()).isEqualTo("gildongh");
        assertThat(profile.locale()).isEqualTo("ko-KR");
        assertThat(profile.tenantId()).isEqualTo("fan-platform");
        assertThat(profile.tenantType()).isEqualTo("B2C");
    }

    @Test
    @DisplayName("getAccountProfile — 404 응답 → Optional.empty()")
    void getAccountProfile_404_returnsEmpty() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/accounts/acc-999/profile"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.getAccountProfile("acc-999")).isEmpty();
    }

    @Test
    @DisplayName("getAccountProfile — 네트워크 오류 → AccountServiceUnavailableException (retry 후)")
    void getAccountProfile_networkFault_throwsAccountServiceUnavailable() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/accounts/acc-err/profile"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.getAccountProfile("acc-err"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    // ── listAccountRoles ───────────────────────────────────────────────────────

    @Test
    @DisplayName("listAccountRoles — 200 응답 + roles 배열 → role 이름 목록 반환")
    void listAccountRoles_200_returnsRoles() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/tenants/wms/accounts/acc-1/roles"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-1\",\"tenantId\":\"wms\",\"roles\":[\"CUSTOMER\"]}")));

        List<String> roles = client.listAccountRoles("wms", "acc-1");

        assertThat(roles).containsExactly("CUSTOMER");
    }

    @Test
    @DisplayName("listAccountRoles — 200 응답 + 빈 roles → 빈 목록 반환")
    void listAccountRoles_200_emptyRoles_returnsEmptyList() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/tenants/wms/accounts/acc-no-role/roles"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-no-role\",\"tenantId\":\"wms\",\"roles\":[]}")));

        List<String> roles = client.listAccountRoles("wms", "acc-no-role");

        assertThat(roles).isEmpty();
    }

    @Test
    @DisplayName("listAccountRoles — 500 응답 → AccountServiceUnavailableException")
    void listAccountRoles_500_throwsAccountServiceUnavailable() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/tenants/wms/accounts/acc-err/roles"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.listAccountRoles("wms", "acc-err"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @DisplayName("listAccountRoles — 네트워크 오류 → AccountServiceUnavailableException (retry 후)")
    void listAccountRoles_networkFault_throwsAccountServiceUnavailable() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/tenants/wms/accounts/acc-fault/roles"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.listAccountRoles("wms", "acc-fault"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    // ── getTenantType (TASK-BE-407) ─────────────────────────────────────────────

    @Test
    @DisplayName("getTenantType — 200 응답 → tenantType 문자열 반환")
    void getTenantType_200_returnsTenantType() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/tenants/ecommerce"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "tenantId": "ecommerce",
                                  "displayName": "E-commerce",
                                  "tenantType": "B2C_CONSUMER",
                                  "status": "ACTIVE"
                                }
                                """)));

        Optional<String> result = client.getTenantType("ecommerce");

        assertThat(result).contains("B2C_CONSUMER");
    }

    @Test
    @DisplayName("getTenantType — 404 응답 → Optional.empty()")
    void getTenantType_404_returnsEmpty() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/tenants/unknown"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.getTenantType("unknown")).isEmpty();
    }

    @Test
    @DisplayName("getTenantType — 503 응답 → AccountServiceUnavailableException")
    void getTenantType_503_throwsAccountServiceUnavailable() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/tenants/svc-down"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.getTenantType("svc-down"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @DisplayName("getTenantType — 401 응답(비-404 4xx) → AccountServiceUnavailableException (empty 로 삼키지 않음)")
    void getTenantType_401_throwsAccountServiceUnavailable() {
        // Regression: a non-404 4xx must NOT be swallowed as Optional.empty(), which
        // would let the resolver fall back to the B2C default and misclassify the
        // tenant_type claim — the exact bug class TASK-BE-407 fixes.
        wireMockServer.stubFor(get(urlEqualTo("/internal/tenants/unauthorized"))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client.getTenantType("unauthorized"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @DisplayName("getTenantType — 403 응답(비-404 4xx) → AccountServiceUnavailableException (empty 로 삼키지 않음)")
    void getTenantType_403_throwsAccountServiceUnavailable() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/tenants/forbidden"))
                .willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> client.getTenantType("forbidden"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @DisplayName("getTenantType — 연결 오류 → AccountServiceUnavailableException (retry 후)")
    void getTenantType_connectionFault_throwsAccountServiceUnavailable() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/tenants/fault"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.getTenantType("fault"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }
}
