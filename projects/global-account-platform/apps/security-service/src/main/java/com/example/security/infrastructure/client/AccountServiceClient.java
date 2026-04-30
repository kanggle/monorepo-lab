package com.example.security.infrastructure.client;

import com.example.security.domain.detection.AccountLockClient;
import com.example.security.domain.suspicious.SuspiciousEvent;
import com.example.security.infrastructure.config.DetectionProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal HTTP client for the account-service auto-lock command.
 *
 * <p>Contract: {@code POST /internal/accounts/{id}/lock} with
 * {@code Idempotency-Key = suspicious_event_id}. 3 attempts on
 * timeout/5xx with exponential backoff + jitter. 409 is terminal (invalid
 * transition, e.g. deleted account) and must not be retried. 200 is terminal
 * (lock applied or already-locked idempotent response).</p>
 */
@Slf4j
@Component
public class AccountServiceClient implements AccountLockClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DetectionProperties.AutoLock cfg;
    private final String internalToken;
    private final Counter retryCounter;
    private final Counter failureCounter;

    public AccountServiceClient(DetectionProperties properties,
                                ObjectMapper objectMapper,
                                MeterRegistry meterRegistry,
                                @Value("${security-service.internal-token:}") String internalToken) {
        this.cfg = properties.getAutoLock();
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(cfg.getConnectTimeoutMs()))
                .build();
        this.retryCounter = Counter.builder("security_auto_lock_retries_total")
                .description("Retries when calling account-service /lock")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("security_auto_lock_failures_total")
                .description("Exhausted retries for account-service /lock — requires operator intervention")
                .register(meterRegistry);
    }

    @Override
    public LockResult lock(SuspiciousEvent event) {
        String url = cfg.getAccountServiceBaseUrl() + "/internal/accounts/" + event.getAccountId() + "/lock";
        String body;
        try {
            body = objectMapper.writeValueAsString(buildPayload(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize lock payload", e);
        }

        int maxAttempts = Math.max(1, cfg.getMaxAttempts());
        long backoff = cfg.getInitialBackoffMs();
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(cfg.getReadTimeoutMs()))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", event.getId())
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                if (internalToken != null && !internalToken.isBlank()) {
                    reqBuilder.header("X-Internal-Token", internalToken);
                }
                HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code == 200) {
                    String bodyStr = resp.body() == null ? "" : resp.body();
                    boolean alreadyLocked = false;
                    if (!bodyStr.isBlank()) {
                        try {
                            LockResponse parsed = objectMapper.readValue(bodyStr, LockResponse.class);
                            alreadyLocked = "LOCKED".equals(parsed.previousStatus());
                        } catch (JsonProcessingException e) {
                            log.warn("Unable to parse account-service lock response as JSON; treating as SUCCESS. suspiciousEventId={}, body={}",
                                    event.getId(), bodyStr, e);
                        }
                    }
                    return new LockResult(alreadyLocked ? Status.ALREADY_LOCKED : Status.SUCCESS, code, bodyStr);
                }
                if (code == 409) {
                    return new LockResult(Status.INVALID_TRANSITION, code, resp.body());
                }
                // 4xx (other than 409) is a caller error — do not retry
                if (code >= 400 && code < 500) {
                    log.warn("Auto-lock non-retryable 4xx: status={}, suspiciousEventId={}, body={}",
                            code, event.getId(), resp.body());
                    failureCounter.increment();
                    return new LockResult(Status.FAILURE, code, resp.body());
                }
                // 5xx — retry
                lastError = new RuntimeException("HTTP " + code + ": " + resp.body());
                log.warn("Auto-lock attempt {} failed with status {}, suspiciousEventId={}",
                        attempt, code, event.getId());
            } catch (Exception e) {
                lastError = e;
                log.warn("Auto-lock attempt {} threw: {}, suspiciousEventId={}",
                        attempt, e.toString(), event.getId());
            }

            if (attempt < maxAttempts) {
                retryCounter.increment();
                sleepWithJitter(backoff);
                backoff *= 2;
            }
        }

        failureCounter.increment();
        log.error("Auto-lock failed after {} attempts; will be routed to outbox pending. suspiciousEventId={}",
                maxAttempts, event.getId(), lastError);
        return new LockResult(Status.FAILURE, 0, lastError == null ? "unknown" : lastError.toString());
    }

    private Map<String, Object> buildPayload(SuspiciousEvent event) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("reason", "AUTO_DETECT");
        p.put("ruleCode", event.getRuleCode());
        p.put("riskScore", event.getRiskScore());
        p.put("suspiciousEventId", event.getId());
        p.put("detectedAt", event.getDetectedAt().toString());
        return p;
    }

    /**
     * Typed view of the account-service lock response (see
     * {@code specs/contracts/http/internal/security-to-account.md}). Unknown
     * fields are ignored so the contract can evolve additively without breaking
     * this client.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LockResponse(String accountId,
                                String previousStatus,
                                String currentStatus,
                                String lockedAt) {}

    private static void sleepWithJitter(long baseMs) {
        long jitter = (long) (Math.random() * (baseMs / 2.0 + 1));
        try {
            Thread.sleep(baseMs + jitter);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
