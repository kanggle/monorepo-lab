package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.AssumeTenantDeniedException;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-327 (ADR-MONO-020 D2) — unit tests for {@link AdminAssignmentClient}.
 *
 * <p>Asserts the defining <b>fail-CLOSED</b> property: {@code true} ONLY on
 * {@code {assigned:true}}; EVERY other outcome ({@code {assigned:false}}, 4xx,
 * 5xx, network fault) throws {@link AssumeTenantDeniedException}. This is the
 * opposite of the account-service {@code entitled_domains} fail-soft.
 */
@DisplayName("AdminAssignmentClient 단위 테스트 (TASK-BE-327, fail-closed)")
class AdminAssignmentClientUnitTest {

    private static final String CHECK_PATH = "/internal/operator-assignments/check";
    private static final String SUBJECT = "00000000-0000-7000-8000-0000000000a1";
    private static final String TENANT = "acme-corp";

    private WireMockServer wireMockServer;
    private AdminAssignmentClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        GapClientCredentialsTokenProvider tokenProvider = mock(GapClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        client = new AdminAssignmentClient(wireMockServer.baseUrl(), 3000, 5000, tokenProvider);

        HttpClient jdkHttp11 = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        RestClient http11RestClient = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(jdkHttp11))
                .build();
        ReflectionTestUtils.setField(client, "cachedRestClient", http11RestClient);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private void stubAssigned(boolean assigned) {
        wireMockServer.stubFor(get(urlPathEqualTo(CHECK_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"assigned\":" + assigned + "}")));
    }

    @Test
    @DisplayName("assigned=true → true")
    void assignedTrue_returnsTrue() {
        stubAssigned(true);
        assertThat(client.isAssigned(SUBJECT, TENANT)).isTrue();
    }

    // ── TASK-BE-338: orgScope parsing ───────────────────────────────────────────

    @Test
    @DisplayName("BE-338: orgScope 배열 파싱 → AssignmentResult.orgScope=[dept-sales]")
    void parsesOrgScope_populated() {
        wireMockServer.stubFor(get(urlPathEqualTo(CHECK_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"assigned\":true,\"orgScope\":[\"dept-sales\",\"dept-ops\"]}")));

        var result = client.resolveAssignment(SUBJECT, TENANT);

        assertThat(result.assigned()).isTrue();
        assertThat(result.orgScope()).containsExactly("dept-sales", "dept-ops");
    }

    @Test
    @DisplayName("BE-338 net-zero: orgScope=null → AssignmentResult.orgScope=null (→ [*])")
    void parsesOrgScope_null() {
        wireMockServer.stubFor(get(urlPathEqualTo(CHECK_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"assigned\":true,\"orgScope\":null}")));

        var result = client.resolveAssignment(SUBJECT, TENANT);

        assertThat(result.assigned()).isTrue();
        assertThat(result.orgScope()).isNull();
    }

    @Test
    @DisplayName("BE-338 graceful: orgScope 필드 부재(구버전 admin) → orgScope=null (→ [*])")
    void parsesOrgScope_absent() {
        stubAssigned(true); // body = {"assigned":true} — no orgScope field
        var result = client.resolveAssignment(SUBJECT, TENANT);

        assertThat(result.assigned()).isTrue();
        assertThat(result.orgScope()).isNull();
    }

    @Test
    @DisplayName("BE-338: orgScope=[] (명시적 zero-scope) → [] (null 과 구분)")
    void parsesOrgScope_emptyArray() {
        wireMockServer.stubFor(get(urlPathEqualTo(CHECK_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"assigned\":true,\"orgScope\":[]}")));

        var result = client.resolveAssignment(SUBJECT, TENANT);

        assertThat(result.assigned()).isTrue();
        assertThat(result.orgScope()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Authorization: Bearer JWT 첨부 + query 파라미터 전달")
    void attachesBearerAndParams() {
        stubAssigned(true);
        client.isAssigned(SUBJECT, TENANT);
        wireMockServer.verify(getRequestedFor(urlPathEqualTo(CHECK_PATH))
                .withQueryParam("oidcSubject", equalTo(SUBJECT))
                .withQueryParam("tenantId", equalTo(TENANT))
                .withHeader("Authorization", equalTo("Bearer test-jwt")));
    }

    @Test
    @DisplayName("assigned=false → AssumeTenantDeniedException (미할당 deny)")
    void assignedFalse_denies() {
        stubAssigned(false);
        assertThatThrownBy(() -> client.isAssigned(SUBJECT, TENANT))
                .isInstanceOf(AssumeTenantDeniedException.class);
    }

    @Test
    @DisplayName("admin 4xx (not-assigned/unauthorized) → AssumeTenantDeniedException (fail-closed)")
    void clientError_failClosed() {
        wireMockServer.stubFor(get(urlPathEqualTo(CHECK_PATH))
                .willReturn(aResponse().withStatus(401)));
        assertThatThrownBy(() -> client.isAssigned(SUBJECT, TENANT))
                .isInstanceOf(AssumeTenantDeniedException.class);
    }

    @Test
    @DisplayName("admin 5xx → AssumeTenantDeniedException (fail-closed, NOT fail-soft)")
    void serverError_failClosed() {
        wireMockServer.stubFor(get(urlPathEqualTo(CHECK_PATH))
                .willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> client.isAssigned(SUBJECT, TENANT))
                .isInstanceOf(AssumeTenantDeniedException.class);
    }

    @Test
    @DisplayName("admin 네트워크 오류 (down/timeout) → AssumeTenantDeniedException (fail-closed)")
    void networkFault_failClosed() {
        wireMockServer.stubFor(get(urlPathEqualTo(CHECK_PATH))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));
        assertThatThrownBy(() -> client.isAssigned(SUBJECT, TENANT))
                .isInstanceOf(AssumeTenantDeniedException.class);
    }
}
