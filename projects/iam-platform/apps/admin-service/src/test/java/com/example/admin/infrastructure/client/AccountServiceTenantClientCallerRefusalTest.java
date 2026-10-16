package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-640 — account-service now refuses a caller that is not on the tenant lifecycle allow-list with
 * {@code 403 FORBIDDEN}. For admin-service that 403 can only mean a mis-set allow-list (it IS the named
 * caller), and a mis-configuration must not be retried nor counted against the shared {@code accountService}
 * circuit breaker — every account-service call shares that breaker, so a tripped one would take the whole
 * admin → account surface down with it.
 *
 * <p>The retry / breaker {@code ignore-exceptions} lists name {@link NonRetryableDownstreamException}
 * (admin {@code application.yml}). create / update / get already threw it for a 4xx; {@code list()} threw
 * the plain {@code DownstreamFailureException} — retried three times and counted. The admin API answer
 * is the same either way (the subclass is handled as 503 {@code DOWNSTREAM_ERROR}).
 */
@DisplayName("AccountServiceTenantClient — 라이프사이클 403 은 재시도 · 차단기 대상이 아니다 (TASK-BE-640)")
class AccountServiceTenantClientCallerRefusalTest {

    private static final String FORBIDDEN_BODY =
            "{\"code\":\"FORBIDDEN\",\"message\":\"The caller is not allowed on this internal resource\"}";

    private WireMockServer wireMock;
    private AccountServiceTenantClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        wireMock.stubFor(any(urlPathMatching("/internal/tenants.*")).willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json").withBody(FORBIDDEN_BODY)));
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        client = new AccountServiceTenantClient(wireMock.baseUrl(), 3000, 5000, tokenProvider);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("🔴 list() — 403 → NonRetryableDownstreamException(403, FORBIDDEN)")
    void list_403_isNonRetryable() {
        assertThatThrownBy(() -> client.list(null, null, 0, 20))
                .isInstanceOf(NonRetryableDownstreamException.class)
                .satisfies(e -> {
                    NonRetryableDownstreamException n = (NonRetryableDownstreamException) e;
                    org.assertj.core.api.Assertions.assertThat(n.getHttpStatus()).isEqualTo(403);
                    org.assertj.core.api.Assertions.assertThat(n.getErrorCode()).isEqualTo("FORBIDDEN");
                });
    }

    @Test
    @DisplayName("대조군: create · update · get — 403 → NonRetryableDownstreamException (지금 그대로)")
    void createUpdateGet_403_areNonRetryable() {
        assertThatThrownBy(() -> client.create("acme", "Acme", "B2B_ENTERPRISE"))
                .isInstanceOf(NonRetryableDownstreamException.class);
        assertThatThrownBy(() -> client.update("acme", null, "SUSPENDED"))
                .isInstanceOf(NonRetryableDownstreamException.class);
        assertThatThrownBy(() -> client.get("acme"))
                .isInstanceOf(NonRetryableDownstreamException.class);
    }
}
