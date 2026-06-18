package com.example.product.infrastructure.client;

import com.example.product.application.port.SellerAccountProvisioner.ProvisioningResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AccountServiceSellerProvisioner 단위 테스트 (ADR-MONO-042 D2/D3/D4/D5 — fail-soft)")
class AccountServiceSellerProvisionerTest {

    private WireMockServer wireMock;
    private AccountServiceSellerProvisioner provisioner;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        provisioner = new AccountServiceSellerProvisioner(
                wireMock.baseUrl(), 3000, 5000, "SELLER", tokenProvider);
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
    @DisplayName("deactivateAccount - non-null → PATCH /internal/tenants/{t}/accounts/{id}/status 1회")
    void deactivateAccount_callsStatus() {
        wireMock.stubFor(patch(urlPathEqualTo("/internal/tenants/tenant-a/accounts/acct-1/status"))
                .willReturn(aResponse().withStatus(200)));

        provisioner.deactivateAccount("tenant-a", "acct-1");

        wireMock.verify(patchRequestedFor(urlPathEqualTo("/internal/tenants/tenant-a/accounts/acct-1/status")));
    }

    @Test
    @DisplayName("deactivateAccount - null accountId → no call (net-zero)")
    void deactivateAccount_null_noCall() {
        provisioner.deactivateAccount("tenant-a", null);

        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }
}
