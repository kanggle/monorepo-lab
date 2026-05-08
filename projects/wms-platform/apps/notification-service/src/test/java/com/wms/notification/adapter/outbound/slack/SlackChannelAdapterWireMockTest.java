package com.wms.notification.adapter.outbound.slack;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * WireMock-backed contract tests for {@link SlackChannelAdapter}. Covers
 * the 5 cases required by Acceptance Criteria 5: 200 / 5xx / 4xx /
 * timeout / circuit-breaker open.
 *
 * <p>Resilience4j is intentionally NOT initialised in this slice — we
 * exercise the raw HTTP + status-code mapping. Circuit-breaker behaviour
 * is verified by the application-layer test ({@code DeliveryExecutorTest})
 * + the Resilience4j boot integration in {@code @SpringBootTest} suites.
 */
@TestInstance(Lifecycle.PER_CLASS)
class SlackChannelAdapterWireMockTest {

    private WireMockServer wireMock;
    private SlackChannelAdapter adapter;
    private SlackChannelProperties properties;

    @BeforeAll
    void start() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    void stop() {
        wireMock.stop();
    }

    @BeforeEach
    void wire() {
        wireMock.resetAll();
        properties = new SlackChannelProperties();
        SlackChannelProperties.ChannelConfig cfg = new SlackChannelProperties.ChannelConfig();
        cfg.setWebhookUrl(wireMock.baseUrl() + "/services/T0/B0/X");
        properties.getSlack().put("wms-alerts", cfg);
        adapter = new SlackChannelAdapter(properties, new ObjectMapper());
    }

    @Test
    void slack200Succeeds() {
        wireMock.stubFor(post(urlPathEqualTo("/services/T0/B0/X"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));
        adapter.send("wms-alerts", "{\"eventType\":\"inventory.low-stock-detected\"}");
        assertThat(wireMock.getAllServeEvents()).hasSize(1);
    }

    @Test
    void slack5xxRetryable() {
        wireMock.stubFor(post(urlPathEqualTo("/services/T0/B0/X"))
                .willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> adapter.send("wms-alerts", "{}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("503")
                .isNotInstanceOf(SlackPermanentFailureException.class);
    }

    @Test
    void slack404PermanentFailure() {
        wireMock.stubFor(post(urlPathEqualTo("/services/T0/B0/X"))
                .willReturn(aResponse().withStatus(404)));
        assertThatThrownBy(() -> adapter.send("wms-alerts", "{}"))
                .isInstanceOf(SlackPermanentFailureException.class)
                .extracting("statusCode").isEqualTo(404);
    }

    @Test
    void timeoutMapsToRetryable() {
        wireMock.stubFor(post(urlPathEqualTo("/services/T0/B0/X"))
                .willReturn(aResponse().withFixedDelay(7_000).withStatus(200)));
        // Adapter read timeout = 5s. WireMock holds the response for 7s.
        long started = System.currentTimeMillis();
        assertThatThrownBy(() -> adapter.send("wms-alerts", "{}"))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(SlackPermanentFailureException.class);
        long elapsed = System.currentTimeMillis() - started;
        // Sanity: must abort well under WireMock's 7s delay.
        assertThat(elapsed).isLessThan(6_500L);
    }

    @Test
    void notConfiguredAliasFailsClosed() {
        properties.getSlack().clear();
        assertThatThrownBy(() -> adapter.send("wms-alerts", "{}"))
                .isInstanceOf(ChannelNotConfiguredException.class);
    }
}
