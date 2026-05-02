package com.example.community.infrastructure.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class MembershipAccessClientTest {

    private WireMockServer wm;
    private MembershipAccessClient client;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(wireMockConfig().dynamicPort());
        wm.start();
        WebClient webClient = WebClient.builder().baseUrl("http://localhost:" + wm.port()).build();
        client = new MembershipAccessClient(webClient);
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void allowed_true_returns_true() {
        wm.stubFor(get(urlPathEqualTo("/internal/membership/access"))
                .willReturn(okJson("""
                        {"accountId":"fan-1","requiredPlanLevel":"FAN_CLUB","allowed":true,"activePlanLevel":"FAN_CLUB"}
                        """)));

        assertThat(client.check("fan-1", "FAN_CLUB")).isTrue();
    }

    @Test
    void allowed_false_returns_false() {
        wm.stubFor(get(urlPathEqualTo("/internal/membership/access"))
                .willReturn(okJson("""
                        {"accountId":"fan-1","requiredPlanLevel":"FAN_CLUB","allowed":false,"activePlanLevel":"FREE"}
                        """)));

        assertThat(client.check("fan-1", "FAN_CLUB")).isFalse();
    }

    @Test
    void server_error_throws_for_cb_to_handle() {
        wm.stubFor(get(urlPathEqualTo("/internal/membership/access"))
                .willReturn(aResponse().withStatus(503)));

        // Without Spring-managed CB, the raw call throws; production fallback returns false.
        // Ensure we surface the error so CB can count it.
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> client.check("fan-1", "FAN_CLUB"));
    }
}
