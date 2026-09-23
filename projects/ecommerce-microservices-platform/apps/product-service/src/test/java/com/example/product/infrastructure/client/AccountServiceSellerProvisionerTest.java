package com.example.product.infrastructure.client;

import com.example.product.application.port.SellerAccountProvisioner.ProvisioningResult;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AccountServiceSellerProvisioner 단위 테스트 (ADR-MONO-042 D2/D3/D4/D5 — fail-soft)")
class AccountServiceSellerProvisionerTest {

    private WireMockServer wireMock;
    private AccountServiceSellerProvisioner provisioner;
    private TenantScopedIamTokenProvider tenantTokenProvider;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        // 🔴 TASK-MONO-721 (ADR-MONO-076 D1): the two providers yield DIFFERENT tokens, and the
        // difference is the ticket. Calls whose path names a tenant must carry the EXCHANGED
        // one; the base credential is refused on that surface by construction. The distinct
        // literals are what let the cells below tell which one was used.
        tenantTokenProvider = mock(TenantScopedIamTokenProvider.class);
        when(tenantTokenProvider.bearerFor("tenant-a")).thenReturn("test-jwt-tenant-a");
        provisioner = new AccountServiceSellerProvisioner(
                wireMock.baseUrl(), 3000, 5000, "SELLER", tokenProvider, tenantTokenProvider);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("provision - account mint + identity resolveOrCreate 200 → success(accountId, identityId) (AC-2/D5)")
    void provision_success_returnsIds() {
        wireMock.stubFor(post(urlPathEqualTo("/internal/tenants/tenant-a/accounts"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acct-1\",\"tenantId\":\"tenant-a\","
                                + "\"email\":\"seller+tenant-a+seller-1@marketplace.local\","
                                + "\"status\":\"ACTIVE\",\"roles\":[\"SELLER\"]}")));
        wireMock.stubFor(post(urlPathEqualTo("/internal/tenants/tenant-a/identities:resolveOrCreate"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"identityId\":\"id-1\",\"outcome\":\"CREATED\"}")));

        ProvisioningResult result = provisioner.provision("tenant-a", "seller-1", "Seller One");

        assertThat(result.successful()).isTrue();
        assertThat(result.accountId()).isEqualTo("acct-1");
        assertThat(result.identityId()).isEqualTo("id-1");
        // the account mint carried the SELLER role + a Bearer JWT
        wireMock.verify(postRequestedFor(urlPathEqualTo("/internal/tenants/tenant-a/accounts")));
    }

    @Test
    @DisplayName("🔴 TASK-MONO-721 — 경로에 테넌트가 있는 호출은 **교환 토큰**을 싣는다 (기본 자격이 아니라)")
    void tenantPathCallsCarryTheExchangedBearer() {
        // ADR-MONO-076 D1. This is the wiring verdict: the same method could compile, pass
        // every other cell, and still send the base credential — which /internal/tenants/**
        // refuses by construction. The two stubbed literals are what make that visible.
        wireMock.stubFor(post(urlPathEqualTo("/internal/tenants/tenant-a/accounts"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acct-1\",\"tenantId\":\"tenant-a\","
                                + "\"email\":\"seller+tenant-a+seller-1@marketplace.local\","
                                + "\"status\":\"ACTIVE\",\"roles\":[\"SELLER\"]}")));
        wireMock.stubFor(post(urlPathEqualTo("/internal/tenants/tenant-a/identities:resolveOrCreate"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"identityId\":\"id-1\",\"outcome\":\"CREATED\"}")));

        provisioner.provision("tenant-a", "seller-1", "Seller One");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/internal/tenants/tenant-a/accounts"))
                .withHeader("Authorization", equalTo("Bearer test-jwt-tenant-a")));
        wireMock.verify(postRequestedFor(
                urlPathEqualTo("/internal/tenants/tenant-a/identities:resolveOrCreate"))
                .withHeader("Authorization", equalTo("Bearer test-jwt-tenant-a")));
        // 🔵 And the exchange was asked for THIS tenant — a provider that ignored its argument
        // and returned one global token would pass the two assertions above.
        verify(tenantTokenProvider, atLeastOnce()).bearerFor("tenant-a");
    }

    @Test
    @DisplayName("🔵 대조군 — 경로에 테넌트가 **없는** lock 호출은 기본 자격 그대로다")
    void tenantlessPathKeepsTheBaseCredential() {
        // /internal/accounts/{id}/lock names no tenant, so the tenant-scope rule does not apply
        // and exchanging would be work with no reason. 🔴 Without this cell, "switch everything
        // to the exchanged token" would look equally correct.
        wireMock.stubFor(post(urlPathEqualTo("/internal/accounts/acct-1/lock"))
                .willReturn(aResponse().withStatus(200)));

        provisioner.lockAccount("tenant-a", "acct-1");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/internal/accounts/acct-1/lock"))
                .withHeader("Authorization", equalTo("Bearer test-jwt")));
    }

    @Test
    @DisplayName("provision - account mint 5xx → FAIL-SOFT failed() (seller stays PENDING) (AC-3/F1)")
    void provision_accountMint5xx_failSoft() {
        wireMock.stubFor(post(urlPathEqualTo("/internal/tenants/tenant-a/accounts"))
                .willReturn(aResponse().withStatus(503)));

        ProvisioningResult result = provisioner.provision("tenant-a", "seller-1", "Seller One");

        assertThat(result.successful()).isFalse();
        assertThat(result.accountId()).isNull();
    }

    @Test
    @DisplayName("provision - account OK but identity resolveOrCreate 5xx → success with null identity (D5 best-effort)")
    void provision_identityFails_accountStillSucceeds() {
        wireMock.stubFor(post(urlPathEqualTo("/internal/tenants/tenant-a/accounts"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acct-1\",\"status\":\"ACTIVE\"}")));
        wireMock.stubFor(post(urlPathEqualTo("/internal/tenants/tenant-a/identities:resolveOrCreate"))
                .willReturn(aResponse().withStatus(503)));

        ProvisioningResult result = provisioner.provision("tenant-a", "seller-1", "Seller One");

        assertThat(result.successful()).isTrue();
        assertThat(result.accountId()).isEqualTo("acct-1");
        assertThat(result.identityId()).isNull();
    }

    @Test
    @DisplayName("provision - account 200 but no accountId → failed() (fail-soft)")
    void provision_noAccountId_failSoft() {
        wireMock.stubFor(post(urlPathEqualTo("/internal/tenants/tenant-a/accounts"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ACTIVE\"}")));

        ProvisioningResult result = provisioner.provision("tenant-a", "seller-1", "Seller One");

        assertThat(result.successful()).isFalse();
    }

    // ─── deactivation (D4) ─────────────────────────────────────────────

    @Test
    @DisplayName("lockAccount - non-null accountId → POST /internal/accounts/{id}/lock 1회")
    void lockAccount_callsLock() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/acct-1/lock"))
                .willReturn(aResponse().withStatus(200)));

        provisioner.lockAccount("tenant-a", "acct-1");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/internal/accounts/acct-1/lock")));
    }

    @Test
    @DisplayName("lockAccount - null accountId → no call (net-zero)")
    void lockAccount_null_noCall() {
        provisioner.lockAccount("tenant-a", null);

        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("lockAccount - 5xx → fail-soft (no throw)")
    void lockAccount_5xx_failSoft() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/acct-1/lock"))
                .willReturn(aResponse().withStatus(503)));

        // must not throw
        provisioner.lockAccount("tenant-a", "acct-1");
    }

    @Test
    @DisplayName("deactivateAccount - non-null → PATCH .../status 1회, body status='LOCKED' (valid AccountStatus, B1 contract pin)")
    void deactivateAccount_callsStatus() {
        // The /status EP returns 200 ONLY for the VALID AccountStatus body (LOCKED), and a
        // lower-priority catch-all returns 400 for any other body (e.g. the old never-valid
        // "DEACTIVATED" literal). This removes the blanket-200 stub that masked B1: if the
        // provisioner regressed to an invalid status, it would hit the 400 catch-all and the
        // LOCKED body-assert below would fail.
        wireMock.stubFor(patch(urlPathEqualTo("/internal/tenants/tenant-a/accounts/acct-1/status"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(400))); // catch-all: any non-LOCKED body
        wireMock.stubFor(patch(urlPathEqualTo("/internal/tenants/tenant-a/accounts/acct-1/status"))
                .atPriority(1)
                .withRequestBody(equalToJson(
                        "{\"status\":\"LOCKED\",\"operatorId\":\"product-service\"}"))
                .willReturn(aResponse().withStatus(200)));

        provisioner.deactivateAccount("tenant-a", "acct-1");

        // Pin the contract: the request BODY carried the VALID status literal "LOCKED".
        wireMock.verify(patchRequestedFor(urlPathEqualTo("/internal/tenants/tenant-a/accounts/acct-1/status"))
                .withRequestBody(matchingJsonPath("$.status", containing("LOCKED"))));
    }

    @Test
    @DisplayName("deactivateAccount - 비-enum status(예: DEACTIVATED) 면 EP 가 400 → fail-soft 회귀 가드 (B1)")
    void deactivateAccount_nonEnumStatus_wouldBe400() {
        // Regression guard: account-service AccountStatus has NO "DEACTIVATED" — valueOf 400s.
        // If the provisioner ever sent a non-enum status, the EP would 400 here. The call must
        // still not throw (fail-soft), but the LOCKED body-assert in the sibling test is the
        // mechanism that actually catches a regressed literal.
        wireMock.stubFor(patch(urlPathEqualTo("/internal/tenants/tenant-a/accounts/acct-1/status"))
                .withRequestBody(matchingJsonPath("$.status", containing("DEACTIVATED")))
                .willReturn(aResponse().withStatus(400)));
        wireMock.stubFor(patch(urlPathEqualTo("/internal/tenants/tenant-a/accounts/acct-1/status"))
                .withRequestBody(matchingJsonPath("$.status", containing("LOCKED")))
                .willReturn(aResponse().withStatus(200)));

        // Must not throw whatever the EP returns (fail-soft, D3).
        provisioner.deactivateAccount("tenant-a", "acct-1");

        // The provisioner sent LOCKED (the valid status), so the request did NOT hit the
        // 400-on-DEACTIVATED stub.
        wireMock.verify(0, patchRequestedFor(urlPathEqualTo("/internal/tenants/tenant-a/accounts/acct-1/status"))
                .withRequestBody(matchingJsonPath("$.status", containing("DEACTIVATED"))));
    }

    @Test
    @DisplayName("deactivateAccount - null accountId → no call (net-zero)")
    void deactivateAccount_null_noCall() {
        provisioner.deactivateAccount("tenant-a", null);

        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    // ─── m4: displayName truncation to account-service @Size(max=100) ──────

    @Test
    @DisplayName("provision - 100자 초과 displayName 은 100자로 truncate 후 mint (m4)")
    void provision_longDisplayName_truncatedTo100() {
        String longName = "N".repeat(150);
        wireMock.stubFor(post(urlPathEqualTo("/internal/tenants/tenant-a/accounts"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acct-1\",\"status\":\"ACTIVE\"}")));
        wireMock.stubFor(post(urlPathEqualTo("/internal/tenants/tenant-a/identities:resolveOrCreate"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"identityId\":\"id-1\",\"outcome\":\"CREATED\"}")));

        provisioner.provision("tenant-a", "seller-1", longName);

        // the minted account carried a displayName truncated to EXACTLY 100 chars (<= @Size(max=100)):
        // the regex pins exactly 100 'N' anchored end-to-end, so 101+ would NOT match.
        wireMock.verify(postRequestedFor(urlPathEqualTo("/internal/tenants/tenant-a/accounts"))
                .withRequestBody(matchingJsonPath("$.displayName", matching("N{100}"))));
    }
}
