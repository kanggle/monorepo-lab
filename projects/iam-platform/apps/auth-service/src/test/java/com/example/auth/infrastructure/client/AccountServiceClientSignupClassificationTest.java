package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.exception.SignupEmailConflictException;
import com.example.auth.application.exception.SignupInvalidException;
import com.example.auth.application.exception.SignupNotPossibleException;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-580 AC-1/AC-4 — {@code signup()} splits account-service's 4xx into "retrying helps"
 * and "retrying cannot help".
 *
 * <h2>The defect</h2>
 * Every unclassified 4xx was folded into {@link AccountServiceUnavailableException}, so a
 * permanent {@code 404 TENANT_NOT_FOUND} reached the visitor as
 * <i>"잠시 후 다시 시도해 주세요. 인증 서비스가 일시적으로 불가합니다."</i> — advice that can
 * never come true. The comment on that branch aimed at {@code 429}, which is correct; the
 * condition said "any other 4xx", which is not.
 *
 * <h2>🔴 The status code is not the discriminator — AC-0 measured why</h2>
 * {@code 409} carries two opposite meanings on this endpoint:
 * {@code ACCOUNT_ALREADY_EXISTS} (log in instead) and {@code TENANT_SUSPENDED} (retrying is
 * pointless). The ticket predicted the suspended case would arrive as {@code 403}; the handler
 * returns {@code 409}. That is exactly why it stayed hidden — it landed in the branch that
 * already looked right, and told visitors on a suspended tenant that their <b>email was
 * already registered</b>, sending them to a login that also fails. A false statement that
 * looks actionable is worse than the vague one this ticket started from.
 *
 * <h2>🔴 Control cells are not optional</h2>
 * Without cell (1) this suite cannot tell a correct fix from "turned everything permanent",
 * and that mistake tells a rate-limited visitor to stop trying. Without the unparseable-body
 * cells it cannot tell "classified" from "guessed".
 */
@DisplayName("TASK-BE-580 — signup 4xx 분류")
class AccountServiceClientSignupClassificationTest {

    private static final String SIGNUP_PATH = "/api/accounts/signup";

    private WireMockServer wireMockServer;
    private AccountServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        client = new AccountServiceClient(wireMockServer.baseUrl(), 3000, 5000, tokenProvider);
        // Same HTTP/1.1 pin as AccountServiceClientUnitTest — JDK HttpClient's H2C default
        // produces RST_STREAM against WireMock.
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

    private void stubSignup(int status, String body) {
        var response = aResponse().withStatus(status).withHeader("Content-Type", "application/json");
        wireMockServer.stubFor(post(urlEqualTo(SIGNUP_PATH))
                .willReturn(body == null ? response : response.withBody(body)));
    }

    // ── bite ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BITE (2) — 404 TENANT_NOT_FOUND 는 영구 실패다 (일시적 아님)")
    void notFoundTenantIsPermanent() {
        stubSignup(404, "{\"code\":\"TENANT_NOT_FOUND\",\"message\":\"Tenant not found: iam\"}");

        assertThatThrownBy(this::callSignup)
                .isInstanceOf(SignupNotPossibleException.class)
                .extracting(t -> ((SignupNotPossibleException) t).getErrorCode())
                .isEqualTo("TENANT_NOT_FOUND");
    }

    @Test
    @DisplayName("BITE (AC-0 발견) — 409 TENANT_SUSPENDED 는 영구 실패다 "
            + "(이메일 중복으로 오분류되고 있었다)")
    void suspendedTenantIsPermanentNotAnEmailConflict() {
        stubSignup(409, "{\"code\":\"TENANT_SUSPENDED\",\"message\":\"Tenant is suspended: acme\"}");

        assertThatThrownBy(this::callSignup)
                .as("409 를 상태 코드만으로 읽으면 '이미 가입된 이메일' 이 되고, 그것은 거짓인 "
                        + "데다 실패할 로그인으로 사용자를 보낸다")
                .isInstanceOf(SignupNotPossibleException.class)
                .extracting(t -> ((SignupNotPossibleException) t).getErrorCode())
                .isEqualTo("TENANT_SUSPENDED");
    }

    // ── controls ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CONTROL (1) — 429 는 지금 그대로 일시적이다")
    void rateLimitedStaysTransient() {
        stubSignup(429, "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");

        assertThatThrownBy(this::callSignup)
                .as("429 를 영구로 넘기면 rate limit 에 걸린 사용자에게 '다시 시도하지 마세요' 가 된다")
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @DisplayName("CONTROL (3) — 409 ACCOUNT_ALREADY_EXISTS 는 지금 그대로 이메일 중복이다")
    void accountAlreadyExistsStaysEmailConflict() {
        stubSignup(409, "{\"code\":\"ACCOUNT_ALREADY_EXISTS\",\"message\":\"exists\"}");

        assertThatThrownBy(this::callSignup).isInstanceOf(SignupEmailConflictException.class);
    }

    @Test
    @DisplayName("CONTROL (3) — 400 / 422 는 지금 그대로 입력값 문제다 (BE-472)")
    void validationErrorsStayInvalid() {
        stubSignup(400, "{\"code\":\"BAD_REQUEST\",\"message\":\"bad\"}");
        assertThatThrownBy(this::callSignup).isInstanceOf(SignupInvalidException.class);

        stubSignup(422, "{\"code\":\"VALIDATION_ERROR\",\"message\":\"weak password\"}");
        assertThatThrownBy(this::callSignup).isInstanceOf(SignupInvalidException.class);
    }

    @Test
    @DisplayName("CONTROL (4) — 5xx 는 지금 그대로 일시적이다")
    void serverErrorStaysTransient() {
        stubSignup(503, "{\"code\":\"AUTH_SERVICE_UNAVAILABLE\"}");

        assertThatThrownBy(this::callSignup).isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @DisplayName("CONTROL (4) — 연결 실패는 지금 그대로 일시적이다")
    void connectionFaultStaysTransient() {
        wireMockServer.stubFor(post(urlEqualTo(SIGNUP_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(this::callSignup).isInstanceOf(AccountServiceUnavailableException.class);
    }

    // ── "cannot judge" is not "permanent" ─────────────────────────────────────

    @Test
    @DisplayName("판정 불가 — 본문이 비어 있으면 영구로 단정하지 않고 기존 동작을 유지한다")
    void emptyBodyIsNotJudgedPermanent() {
        stubSignup(404, null);

        assertThatThrownBy(this::callSignup)
                .as("추출 실패는 판정 불가다. 영구라고 잘못 말하면 될 일을 포기하라고 안내하게 된다")
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @DisplayName("판정 불가 — 본문이 JSON 이 아니면 기존 동작을 유지한다")
    void nonJsonBodyIsNotJudgedPermanent() {
        stubSignup(404, "<html>404 Not Found</html>");

        assertThatThrownBy(this::callSignup).isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @DisplayName("판정 불가 — 처음 보는 code 는 영구 목록에 없으므로 기존 동작을 유지한다")
    void unknownCodeIsNotJudgedPermanent() {
        // The allowlist is deliberately a code list, not a status rule: a 4xx nobody has looked
        // at yet must not be classified as permanent on the strength of its status alone.
        stubSignup(404, "{\"code\":\"SOME_FUTURE_CODE\",\"message\":\"?\"}");

        assertThatThrownBy(this::callSignup).isInstanceOf(AccountServiceUnavailableException.class);
    }

    /** Invokes signup and lets the thrown exception propagate to AssertJ. */
    private void callSignup() {
        client.signup("visitor@example.com", "Str0ng!pass", "Visitor", "iam");
    }
}
