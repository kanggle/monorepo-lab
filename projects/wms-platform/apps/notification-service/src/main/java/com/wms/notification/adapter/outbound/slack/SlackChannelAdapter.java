package com.wms.notification.adapter.outbound.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.notification.application.port.out.SlackChannelPort;
import com.wms.notification.domain.error.ChannelNotConfiguredException;
import com.wms.notification.domain.error.ChannelPermanentFailureException;
import com.wms.notification.domain.routing.ChannelType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Slack incoming-webhook adapter. Wraps a JDK {@link HttpClient} with
 * bounded timeouts (3s connect / 5s read), Resilience4j circuit breaker,
 * and exponential-backoff retry.
 *
 * <h2>Failure mapping</h2>
 *
 * <ul>
 *   <li>Slack 2xx → success (return).</li>
 *   <li>Slack 4xx → {@link ChannelPermanentFailureException} (404 channel
 *       not found, 410 token revoked). Resilience4j retry is configured
 *       with this exception in {@code ignoreExceptions} so retry doesn't
 *       fire — the caller transitions the delivery to FAILED.</li>
 *   <li>Slack 5xx, IO error, timeout → generic {@link RuntimeException}
 *       so the retry annotation catches and back-off-retries.</li>
 *   <li>Webhook URL unset → {@link ChannelNotConfiguredException}, treated
 *       as permanent (fail-closed).</li>
 * </ul>
 *
 * <h2>Message body</h2>
 *
 * <p>The application service hands us a serialised {@link
 * com.wms.notification.domain.alert.AlertEnvelope}. We render a minimal
 * Slack-compatible payload {@code { "text": "..." }} — full {@code blocks}
 * templating is v2 (architecture.md § Channel Templates).
 */
@Component
public class SlackChannelAdapter implements SlackChannelPort {

    private static final Logger log = LoggerFactory.getLogger(SlackChannelAdapter.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final SlackChannelProperties properties;
    private final ObjectMapper objectMapper;

    public SlackChannelAdapter(SlackChannelProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.SLACK;
    }

    @Override
    @CircuitBreaker(name = "slack")
    @Retry(name = "slack")
    public void send(String channelAlias, String payloadJson) {
        String webhookUrl = properties.webhookUrlFor(channelAlias);
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new ChannelNotConfiguredException(
                    "Slack webhook URL not configured for channel alias: " + channelAlias);
        }
        String slackBody = SlackBodyRenderer.render(objectMapper, payloadJson, channelAlias);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(READ_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(slackBody))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException io) {
            throw new RuntimeException("Slack webhook IO failure for alias " + channelAlias, io);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sending to Slack alias " + channelAlias, ie);
        }
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            log.info("Slack webhook accepted alias={} status={}", channelAlias, status);
            return;
        }
        if (status >= 400 && status < 500) {
            // 4xx is contract-permanent — never retry (vendor policy I3).
            // Body intentionally NOT logged in production: incoming-webhooks
            // can echo the channel id, and the URL itself contains a token.
            throw new ChannelPermanentFailureException(status,
                    "Slack permanent failure status=" + status + " alias=" + channelAlias);
        }
        // 5xx or unexpected → retryable.
        throw new RuntimeException("Slack transient failure status=" + status + " alias=" + channelAlias);
    }

}
