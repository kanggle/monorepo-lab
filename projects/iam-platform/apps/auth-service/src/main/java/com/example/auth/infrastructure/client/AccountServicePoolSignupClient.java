package com.example.auth.infrastructure.client;

import com.example.auth.application.port.ConsumerPoolSignupPort;
import com.example.common.resilience.ResilienceClientFactory;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * TASK-MONO-772 S3 (auth-to-account.md § {@code POST /internal/consumer-pool/signups}) — {@link ConsumerPoolSignupPort}
 * over account-service's site-less pool signup, server-side for the IdP page {@code /operator-invitations/signup}.
 *
 * <p>Same base URL ({@code auth.account-service.base-url}) and timeouts as {@link AccountServiceClient}, with the GAP
 * {@code client_credentials} Bearer the {@code /internal/**} chain requires. 🔴 Not retried and not behind a breaker
 * (the consumer {@code /signup} proxy's stance): a signup is a non-idempotent write — the page shows «잠시 뒤 다시»
 * and the person retries deliberately.
 *
 * <p>🔴 The body is a record whose {@code toString} hides the password and the address (Spring logs a body via
 * {@code toString} at DEBUG). Log lines carry the status and the code only.
 */
@Slf4j
@Component
public class AccountServicePoolSignupClient implements ConsumerPoolSignupPort {

    static final String PATH = "/internal/consumer-pool/signups";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Environment environment;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final IamClientCredentialsTokenProvider tokenProvider;

    private volatile String cachedBaseUrl;
    private volatile RestClient cachedRestClient;

    public AccountServicePoolSignupClient(
            Environment environment,
            @Value("${auth.account-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${auth.account-service.read-timeout-ms:5000}") int readTimeoutMs,
            IamClientCredentialsTokenProvider tokenProvider) {
        this.environment = environment;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.tokenProvider = tokenProvider;
    }

    private RestClient restClient() {
        String baseUrl = environment.getProperty(AccountServiceClient.BASE_URL_PROPERTY);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Required property '" + AccountServiceClient.BASE_URL_PROPERTY + "' is not set");
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

    @Override
    public Outcome signup(String email, String password, String displayName) {
        try {
            restClient().post()
                    .uri(PATH)
                    .headers(h -> {
                        h.setBearerAuth(tokenProvider.currentBearer());
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(new SignupBody(email, password,
                            displayName == null || displayName.isBlank() ? null : displayName))
                    .retrieve()
                    .toBodilessEntity();
            return Outcome.CREATED;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String code = errorCode(e.getResponseBodyAsString());
            log.info("site-less pool signup answered {} code={}", status, code == null ? "<unparsed>" : code);
            if (status == 409 && "ACCOUNT_ALREADY_EXISTS".equals(code)) {
                return Outcome.ALREADY_EXISTS;
            }
            if (status == 409 && "CONSUMER_POOL_DISABLED".equals(code)) {
                return Outcome.NOT_POSSIBLE;
            }
            if ((status == 400 || status == 422) && "VALIDATION_ERROR".equals(code)) {
                return Outcome.INVALID;
            }
            return Outcome.UNAVAILABLE;
        } catch (RuntimeException e) {
            log.warn("site-less pool signup could not reach account-service: type={}", e.getClass().getSimpleName());
            return Outcome.UNAVAILABLE;
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

    /** 🔴 {@code toString} hides the password and the address. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SignupBody(String email, String password, String displayName) {
        @Override
        public String toString() {
            return "SignupBody[email=<masked>, password=<redacted>, displayName=" + displayName + "]";
        }
    }
}
