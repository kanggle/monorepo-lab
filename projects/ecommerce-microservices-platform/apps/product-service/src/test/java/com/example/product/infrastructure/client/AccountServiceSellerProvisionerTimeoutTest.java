package com.example.product.infrastructure.client;

import com.example.product.application.port.SellerAccountProvisioner.ProvisioningResult;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Timeout-specific unit test for {@link AccountServiceSellerProvisioner}
 * (ADR-MONO-058 D7 / TASK-BE-570).
 *
 * <p>Before this task the client was already non-zero-timeout (a hand-rolled
 * {@code JdkClientHttpRequestFactory}) — not a live-risk case like search/review-service —
 * but a duplicated mechanism now migrated to {@link
 * com.example.common.resilience.ResilienceClientFactory}. This test proves the migration did
 * NOT regress the operator-configurable {@code iam.downstream.read-timeout-ms} behavior: a
 * hung account-service response still fails fast within the configured bound (fail-soft, D3 —
 * caught internally, never thrown), rather than hanging forever.
 */
@DisplayName("AccountServiceSellerProvisioner 타임아웃 회귀 가드 (ADR-MONO-058 D7)")
class AccountServiceSellerProvisionerTimeoutTest {

    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("hung account-service -> provision() fails FAST within the configured read timeout (fail-soft, never hangs forever)")
    void hungAccountService_provisionFailsFastWithinConfiguredReadTimeout() {
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");

        // configured read timeout is 300ms; the stub delays 5s, far beyond that bound.
        AccountServiceSellerProvisioner provisioner = new AccountServiceSellerProvisioner(
                wireMock.baseUrl(), 2000, 300, "SELLER", tokenProvider);

        wireMock.stubFor(post(urlPathEqualTo("/internal/tenants/tenant-a/accounts"))
                .willReturn(aResponse().withFixedDelay(5000).withStatus(201)));

        long start = System.nanoTime();
        ProvisioningResult result = provisioner.provision("tenant-a", "seller-1", "Seller One");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(result.successful()).isFalse();
        // Generous upper bound (10x the configured 300ms read timeout) to absorb CI scheduling
        // jitter while still proving the call did NOT hang for the full 5s stub delay.
        assertThat(elapsedMs).isLessThan(3000);
    }
}
