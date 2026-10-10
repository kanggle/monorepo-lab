package com.example.auth.infrastructure.client;

import com.example.auth.application.port.OperatorInvitationAcceptancePort;
import com.example.common.resilience.ResilienceClientFactory;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * TASK-MONO-772 S3 (auth-to-admin.md § preview · § accept) — {@link OperatorInvitationAcceptancePort} over
 * admin-service's {@code POST /internal/operator-invitations/preview · /accept}.
 *
 * <p>Same base URL ({@code auth.admin-service.base-url}), timeouts and GAP {@code client_credentials} Bearer as
 * {@link AdminAssignmentClient} / {@link AdminConsoleEligibilityClient}, with its <b>own</b> circuit breaker
 * ({@code adminOperatorInvitation}) — an invitation-page outage must not open the breakers that gate assume-tenant
 * or console issuance (contract «별도 breaker 이름»).
 *
 * <ul>
 *   <li>{@code preview} — a read: retried like the other reads. 404 is an answer ({@code NOT_FOUND}).</li>
 *   <li>{@code accept} — a write: 🔴 <b>never retried</b> (contract); the page's «다시 시도» is the retry, and the
 *       same account's resubmit is idempotent at admin. The contract's refusals are returned as values inside the
 *       breaker's supplier, so a refusal is never counted as a fault — only «no answer» is.</li>
 * </ul>
 *
 * <p>🔴 R4: the bodies are records whose {@code toString} hides the token (Spring logs a body via
 * {@code toString} at DEBUG — the 772 S2 CI lesson). The token travels in a POST body, never a URL. Log lines carry
 * the status and the code only.
 */
@Slf4j
@Component
public class AdminOperatorInvitationClient implements OperatorInvitationAcceptancePort {

    static final String PREVIEW_PATH = "/internal/operator-invitations/preview";
    static final String ACCEPT_PATH = "/internal/operator-invitations/accept";

    /** admin's error {@code code} → the page outcome (auth-to-admin.md § accept Errors). */
    static final Map<String, AcceptOutcome> ACCEPT_CODES = Map.ofEntries(
            Map.entry("EMAIL_NOT_VERIFIED", AcceptOutcome.EMAIL_NOT_VERIFIED),
            Map.entry("OPERATOR_INVITATION_EMAIL_MISMATCH", AcceptOutcome.EMAIL_MISMATCH),
            Map.entry("OPERATOR_INVITATION_ACCOUNT_NOT_ELIGIBLE", AcceptOutcome.ACCOUNT_NOT_ELIGIBLE),
            Map.entry("OPERATOR_INVITATION_NOT_FOUND", AcceptOutcome.NOT_FOUND),
            Map.entry("OPERATOR_INVITATION_ALREADY_USED", AcceptOutcome.ALREADY_USED),
            Map.entry("OPERATOR_ALREADY_PROVISIONED", AcceptOutcome.ALREADY_PROVISIONED),
            Map.entry("OPERATOR_EMAIL_CONFLICT", AcceptOutcome.EMAIL_CONFLICT),
            Map.entry("OPERATOR_INVITATION_INVALIDATED", AcceptOutcome.INVALIDATED),
            Map.entry("OPERATOR_INVITATION_EXPIRED", AcceptOutcome.EXPIRED));

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Environment environment;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final IamClientCredentialsTokenProvider tokenProvider;

    private volatile String cachedBaseUrl;
    private volatile RestClient cachedRestClient;

    public AdminOperatorInvitationClient(
            Environment environment,
            @Value("${auth.admin-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${auth.admin-service.read-timeout-ms:5000}") int readTimeoutMs,
            IamClientCredentialsTokenProvider tokenProvider) {
        this.environment = environment;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.circuitBreaker = ResilienceClientFactory.buildCircuitBreaker("adminOperatorInvitation");
        this.retry = ResilienceClientFactory.buildRetry("adminOperatorInvitation");
        this.tokenProvider = tokenProvider;
    }

    /** Resolved per call (like {@link AdminAssignmentClient}) so a test-time property is honoured. */
    private RestClient restClient() {
        String baseUrl = environment.getProperty(AdminAssignmentClient.BASE_URL_PROPERTY);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Required property '" + AdminAssignmentClient.BASE_URL_PROPERTY + "' is not set");
        }
        RestClient existing = this.cachedRestClient;
        if (existing != null && baseUrl.equals(this.cachedBaseUrl)) {
            return existing;
        }
        synchronized (this) {
            if (this.cachedRestClient != null && baseUrl.equals(this.cachedBaseUrl)) {
                return this.cachedRestClient;
            }
            RestClient fresh = ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs);
            this.cachedBaseUrl = baseUrl;
            this.cachedRestClient = fresh;
            return fresh;
        }
    }

    // ── preview ─────────────────────────────────────────────────────────────

    @Override
    public PreviewResult preview(String token) {
        Supplier<PreviewResult> call = () -> doPreview(token);
        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(retry, call)).get();
        } catch (RuntimeException e) {
            log.warn("operator-invitation preview unavailable: type={}", e.getClass().getSimpleName());
            return PreviewResult.unavailable();
        }
    }

    private PreviewResult doPreview(String token) {
        PreviewBody body;
        try {
            body = restClient().post()
                    .uri(PREVIEW_PATH)
                    .headers(h -> {
                        h.setBearerAuth(tokenProvider.currentBearer());
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(new PreviewRequest(token))
                    .retrieve()
                    .body(PreviewBody.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return PreviewResult.notFound();
            }
            throw e;
        }
        if (body == null || body.tenantId() == null || body.status() == null) {
            throw new IllegalStateException("admin operator-invitation preview returned an unreadable body");
        }
        return new PreviewResult(PreviewOutcome.FOUND, new Preview(body.tenantId(), body.tenantDisplayName(),
                body.maskedEmail(), body.roles() == null ? List.of() : body.roles(), body.status(),
                Boolean.TRUE.equals(body.expired()), parseInstant(body.expiresAt())));
    }

    // ── accept ──────────────────────────────────────────────────────────────

    @Override
    public AcceptResult accept(String token, String accountId) {
        Supplier<AcceptResult> call = () -> doAccept(token, accountId);
        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, call).get();
        } catch (RuntimeException e) {
            log.warn("operator-invitation accept unavailable (nothing written): type={}", e.getClass().getSimpleName());
            return new AcceptResult(AcceptOutcome.UNAVAILABLE, null);
        }
    }

    private AcceptResult doAccept(String token, String accountId) {
        AcceptBody body;
        try {
            body = restClient().post()
                    .uri(ACCEPT_PATH)
                    .headers(h -> {
                        h.setBearerAuth(tokenProvider.currentBearer());
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(new AcceptRequest(token, accountId))
                    .retrieve()
                    .body(AcceptBody.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String code = errorCode(e.getResponseBodyAsString());
            AcceptOutcome refusal = e.getStatusCode().is4xxClientError() ? ACCEPT_CODES.get(code) : null;
            log.info("operator-invitation accept answered {} code={}", status, code == null ? "<unparsed>" : code);
            if (refusal != null) {
                return new AcceptResult(refusal, null);
            }
            throw e; // 5xx or an unknown 4xx — «no answer», counted by the breaker
        }
        if (body == null || body.tenantId() == null || body.alreadyAccepted() == null) {
            throw new IllegalStateException("admin operator-invitation accept returned an unreadable body");
        }
        return new AcceptResult(body.alreadyAccepted() ? AcceptOutcome.ALREADY_ACCEPTED : AcceptOutcome.ACCEPTED,
                body.tenantId());
    }

    /** The page only displays it — an unparsable value is shown as absent, not an outage. */
    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String errorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = JSON.readTree(body);
            JsonNode code = root == null ? null : root.get("code");
            return code != null && code.isTextual() ? code.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── bodies ──────────────────────────────────────────────────────────────

    /** {@code {"token": …}} — 🔴 {@code toString} hides the token. */
    record PreviewRequest(String token) {
        @Override
        public String toString() {
            return "PreviewRequest[token=<redacted>]";
        }
    }

    /** {@code {"token": …, "accountId": …}} — 🔴 {@code toString} hides the token. */
    record AcceptRequest(String token, String accountId) {
        @Override
        public String toString() {
            return "AcceptRequest[token=<redacted>, accountId=" + accountId + "]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PreviewBody(String tenantId, String tenantDisplayName, String maskedEmail, List<String> roles,
                       String status, Boolean expired, String expiresAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AcceptBody(String operatorId, String tenantId, List<String> roles, Boolean alreadyAccepted) {}
}
