package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.port.OperatorInvitationMailPort;
import com.example.admin.application.port.VerifiedEmailMatchPort;
import com.example.common.resilience.ResilienceClientFactory;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TASK-MONO-772 S2 — the two account-service calls of the operator invitation (admin-to-account.md):
 * <ul>
 *   <li>{@link #match} — {@code POST /internal/accounts/{accountId}/verified-email:match}. A read: it rides the
 *       {@code accountService} retry + circuit breaker. The three definitive refusals are returned as values
 *       (never thrown), so neither the retry nor the breaker treats a refusal as a fault.</li>
 *   <li>{@link #send} — {@code POST /internal/notifications/operator-invitation}. 🔴 <b>No retry</b> (the contract's
 *       one exception to «재시도 2회»): a mail is not idempotent, and a second identical link is worse than a
 *       human pressing «다시 보내기». No breaker either — there is nothing to protect by failing fast, and any
 *       failure is already an answer ({@code FAILED_TRANSIENT}).</li>
 * </ul>
 *
 * <p>R4: the request body carries the raw token; nothing here logs a body, a URL with the token, the recipient,
 * or {@code expectedEmail}. Log lines name the endpoint and the status only.
 */
@Slf4j
@Component
public class AccountServiceInvitationClient implements VerifiedEmailMatchPort, OperatorInvitationMailPort {

    static final String NOTIFICATION_PATH = "/internal/notifications/operator-invitation";

    private final RestClient restClient;
    private final IamClientCredentialsTokenProvider tokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountServiceInvitationClient(
            @Value("${admin.account-service.base-url}") String baseUrl,
            @Value("${admin.downstream.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${admin.downstream.read-timeout-ms:10000}") int readTimeoutMs,
            IamClientCredentialsTokenProvider tokenProvider) {
        this.restClient = ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs);
        this.tokenProvider = tokenProvider;
    }

    private static final RestClient.ResponseSpec.ErrorHandler ERROR_RAISER = (req, resp) -> {
        throw HttpClientErrorException.create(
                resp.getStatusCode(), resp.getStatusText(),
                resp.getHeaders(), resp.getBody().readAllBytes(), null);
    };

    @Override
    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public MatchResult match(String accountId, String expectedEmail) {
        MatchResponse body;
        try {
            body = restClient.post()
                    .uri("/internal/accounts/{accountId}/verified-email:match", accountId)
                    .headers(h -> {
                        h.setBearerAuth(tokenProvider.currentBearer());
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(Map.of("expectedEmail", expectedEmail))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, ERROR_RAISER)
                    .body(MatchResponse.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String code = extractErrorCode(e.getResponseBodyAsByteArray());
            if (status == 404) {
                return new MatchResult(Outcome.NOT_ELIGIBLE, null);
            }
            if (status == 403 && "ACCOUNT_EMAIL_MISMATCH".equals(code)) {
                return new MatchResult(Outcome.EMAIL_MISMATCH, null);
            }
            if (status == 403 && "EMAIL_NOT_VERIFIED".equals(code)) {
                return new MatchResult(Outcome.NOT_VERIFIED, null);
            }
            log.warn("account-service returned {} (code={}) on verified-email:match", status, code);
            if (e.getStatusCode().is4xxClientError()) {
                // An unknown 4xx is not a verdict — fail-closed, and not retried (NonRetryable is ignored by retry).
                throw new NonRetryableDownstreamException("account-service error " + status, e, status, code);
            }
            throw new DownstreamFailureException("account-service error " + status, e);
        } catch (Exception e) {
            log.warn("account-service call failed on verified-email:match: {}", e.getClass().getSimpleName());
            throw new DownstreamFailureException("account-service unavailable", e);
        }
        if (body == null || body.emailVerifiedAt() == null) {
            // A 200 that does not carry the evidence is not a match (admin-to-account.md mapping).
            throw new NonRetryableDownstreamException(
                    "account-service verified-email:match answered 200 without emailVerifiedAt", null, 200, null);
        }
        return new MatchResult(Outcome.MATCHED, body.emailVerifiedAt());
    }

    @Override
    public DeliveryStatus send(InvitationMail mail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", mail.to());
        body.put("token", mail.token());
        body.put("tenantId", mail.tenantId());
        if (mail.inviterDisplayName() != null && !mail.inviterDisplayName().isBlank()) {
            body.put("inviterDisplayName", mail.inviterDisplayName());
        }
        body.put("expiresAt", mail.expiresAt() == null ? null : mail.expiresAt().toString());
        try {
            restClient.post()
                    .uri(NOTIFICATION_PATH)
                    .headers(h -> {
                        h.setBearerAuth(tokenProvider.currentBearer());
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, ERROR_RAISER)
                    .toBodilessEntity();
            return DeliveryStatus.SENT;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String code = extractErrorCode(e.getResponseBodyAsByteArray());
            log.warn("operator-invitation mail not sent: account-service {} (code={})", status, code);
            // Only the contract's permanent answer is permanent; everything else — 503, other 4xx, other 5xx — is
            // transient: calling a failure permanent tells the operator to give up on something that may work.
            return status == 422 && "INVITATION_EMAIL_UNDELIVERABLE".equals(code)
                    ? DeliveryStatus.FAILED_PERMANENT
                    : DeliveryStatus.FAILED_TRANSIENT;
        } catch (RuntimeException e) {
            log.warn("operator-invitation mail not sent: {}", e.getClass().getSimpleName());
            return DeliveryStatus.FAILED_TRANSIENT;
        }
    }

    private String extractErrorCode(byte[] body) {
        if (body == null || body.length == 0) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || root.isMissingNode() || root.isNull()) return null;
            JsonNode code = root.get("code");
            if (code != null && code.isTextual()) return code.asText();
            JsonNode error = root.get("error");
            if (error != null && error.isObject()) {
                JsonNode nested = error.get("code");
                if (nested != null && nested.isTextual()) return nested.asText();
            }
            return null;
        } catch (Exception ignore) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MatchResponse(String accountId, Instant emailVerifiedAt) {}
}
