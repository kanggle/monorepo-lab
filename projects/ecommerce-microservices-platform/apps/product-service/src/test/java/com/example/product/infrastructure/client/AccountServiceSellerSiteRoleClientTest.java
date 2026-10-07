package com.example.product.infrastructure.client;

import com.example.product.domain.exception.SellerInvitationEmailMismatchException;
import com.example.product.domain.exception.SellerInvitationEmailNotVerifiedException;
import com.example.product.domain.exception.SellerMemberAccountNotEligibleException;
import com.example.product.domain.exception.SellerRoleServiceUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-752 — product-service → IAM {@code site-roles:grant / :revoke} (product-to-account.md § 5 · § 6).
 * Grant is fail-closed with a 1:1 error mapping; revoke is fail-soft.
 */
@DisplayName("AccountServiceSellerSiteRoleClient — SELLER 사이트 역할 쓰기/회수 (TASK-MONO-752)")
class AccountServiceSellerSiteRoleClientTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-00000000a752";
    private static final String GRANT = "/internal/tenants/ecommerce/accounts/" + ACCOUNT + "/site-roles:grant";
    private static final String REVOKE = "/internal/tenants/ecommerce/accounts/" + ACCOUNT + "/site-roles:revoke";

    private WireMockServer wireMock;
    private AccountServiceSellerSiteRoleClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        TenantScopedIamTokenProvider tokens = mock(TenantScopedIamTokenProvider.class);
        when(tokens.bearerFor("ecommerce")).thenReturn("jwt-for-ecommerce");
        client = new AccountServiceSellerSiteRoleClient(wireMock.baseUrl(), 3000, 5000, tokens);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private void grantAnswers(int status, String code) {
        wireMock.stubFor(patch(urlPathEqualTo(GRANT)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(code == null ? "{}" : "{\"code\":\"" + code + "\",\"message\":\"x\"}")));
    }

    @Test
    @DisplayName("grant → 테넌트 경로 · 교환 토큰 · X-Tenant-Id · 본문에 초대 이메일(expectedEmail)과 SELLER")
    void grant_sendsInvitedEmailAndTenantScopedBearer() {
        grantAnswers(200, null);

        client.grant("ecommerce", ACCOUNT, "member@example.com");

        wireMock.verify(patchRequestedFor(urlPathEqualTo(GRANT))
                .withHeader("Authorization", equalTo("Bearer jwt-for-ecommerce"))
                .withHeader("X-Tenant-Id", equalTo("ecommerce"))
                .withRequestBody(equalToJson(
                        "{\"roleName\":\"SELLER\",\"expectedEmail\":\"member@example.com\",\"operatorId\":\"product-service\"}")));
    }

    @Test
    @DisplayName("403 SITE_ROLE_EMAIL_MISMATCH → SellerInvitationEmailMismatchException")
    void grant_emailMismatch() {
        grantAnswers(403, "SITE_ROLE_EMAIL_MISMATCH");
        assertThatThrownBy(() -> client.grant("ecommerce", ACCOUNT, "member@example.com"))
                .isInstanceOf(SellerInvitationEmailMismatchException.class);
    }

    @Test
    @DisplayName("TASK-MONO-770: 403 EMAIL_NOT_VERIFIED → SellerInvitationEmailNotVerifiedException (503 으로 뭉개지 않는다)")
    void grant_emailNotVerified() {
        grantAnswers(403, "EMAIL_NOT_VERIFIED");
        assertThatThrownBy(() -> client.grant("ecommerce", ACCOUNT, "member@example.com"))
                .isInstanceOf(SellerInvitationEmailNotVerifiedException.class);
    }

    @Test
    @DisplayName("409 풀 계정 아님 · 409 멤버십 없음 · 404 계정 없음 → SellerMemberAccountNotEligibleException")
    void grant_notEligible() {
        for (String[] answer : new String[][]{
                {"409", "SITE_ROLE_REQUIRES_POOL_ACCOUNT"}, {"409", "SITE_MEMBERSHIP_REQUIRED"}, {"404", "ACCOUNT_NOT_FOUND"}}) {
            wireMock.resetAll();
            grantAnswers(Integer.parseInt(answer[0]), answer[1]);
            assertThatThrownBy(() -> client.grant("ecommerce", ACCOUNT, "member@example.com"))
                    .as(answer[1])
                    .isInstanceOf(SellerMemberAccountNotEligibleException.class);
        }
    }

    @Test
    @DisplayName("🔴 fail-closed: 500 · 다른 403(TENANT_SCOPE_DENIED) · 연결 실패 → 503 예외 (연결 없음)")
    void grant_failClosed() {
        grantAnswers(500, null);
        assertThatThrownBy(() -> client.grant("ecommerce", ACCOUNT, "member@example.com"))
                .isInstanceOf(SellerRoleServiceUnavailableException.class);

        wireMock.resetAll();
        grantAnswers(403, "TENANT_SCOPE_DENIED");
        assertThatThrownBy(() -> client.grant("ecommerce", ACCOUNT, "member@example.com"))
                .isInstanceOf(SellerRoleServiceUnavailableException.class);

        wireMock.stop();
        assertThatThrownBy(() -> client.grant("ecommerce", ACCOUNT, "member@example.com"))
                .isInstanceOf(SellerRoleServiceUnavailableException.class);
    }

    @Test
    @DisplayName("revoke → 2xx 면 true · 실패면 false (fail-soft) · 잠금 경로(/status)는 부르지 않는다")
    void revoke_failSoft() {
        wireMock.stubFor(patch(urlPathEqualTo(REVOKE)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{}")));
        assertThat(client.revoke("ecommerce", ACCOUNT)).isTrue();
        wireMock.verify(patchRequestedFor(urlPathEqualTo(REVOKE))
                .withRequestBody(equalToJson("{\"roleName\":\"SELLER\",\"operatorId\":\"product-service\"}")));
        wireMock.verify(0, patchRequestedFor(urlPathEqualTo(
                "/internal/tenants/ecommerce/accounts/" + ACCOUNT + "/status")));

        wireMock.resetAll();
        wireMock.stubFor(patch(urlPathEqualTo(REVOKE)).willReturn(aResponse().withStatus(503)));
        assertThat(client.revoke("ecommerce", ACCOUNT)).isFalse();
    }
}
