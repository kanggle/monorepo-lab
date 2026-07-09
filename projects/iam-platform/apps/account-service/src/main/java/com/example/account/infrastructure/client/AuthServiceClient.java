package com.example.account.infrastructure.client;

import com.example.account.application.port.AuthServicePort;
import com.example.common.resilience.ResilienceClientFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP client adapter for auth-service. Mirrors the resilience pattern used by
 * auth-service's AccountServiceClient: 3s connect / 15s read, 2 retries with
 * exponential-random backoff (no retry on 4xx), circuit breaker 50% / 10s.
 *
 * <p>On 409 the signup transaction should abort with a duplicate-account error;
 * everything else surfaces as {@link AuthServicePort.AuthServiceUnavailable} so
 * the @Transactional signup rolls back fail-closed.</p>
 *
 * <p><b>HTTP/1.1 enforcement</b>: The JDK {@link HttpClient} defaults to
 * HTTP/2 negotiation (NEGOTIATE mode). WireMock in HTTP (non-TLS) mode does not
 * reliably handle HTTP/2 H2C upgrade frames and responds with RST_STREAM, causing
 * spurious {@code AuthServiceUnavailable} in integration tests. Forcing
 * {@link HttpClient.Version#HTTP_1_1} removes this ambiguity in all environments
 * — internal service-to-service traffic does not require HTTP/2.</p>
 */
@Slf4j
@Component
public class AuthServiceClient implements AuthServicePort {

    private static final String CREDENTIALS_PATH = "/internal/auth/credentials";
    private static final String CREDENTIAL_IDENTITY_BACKFILL_PATH = "/internal/auth/credentials/identity-backfill";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    // TASK-BE-487 (ADR-005 단계 4): auth-service /internal/auth/** now requires a GAP
    // client_credentials Bearer JWT (was permitAll). This mints/caches that token (client_id
    // account-service-client).
    private final IamClientCredentialsTokenProvider tokenProvider;

    public AuthServiceClient(
            @Value("${account.auth-service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${account.auth-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${account.auth-service.read-timeout-ms:15000}") int readTimeoutMs,
            IamClientCredentialsTokenProvider tokenProvider) {
        // Force HTTP/1.1: the JDK HttpClient defaults to HTTP/2 negotiation which
        // causes RST_STREAM failures against WireMock (integration tests) and adds
        // unnecessary upgrade round-trips for cleartext internal traffic.
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.circuitBreaker = ResilienceClientFactory.buildCircuitBreaker("authService");
        this.retry = ResilienceClientFactory.buildRetry("authService");
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void createCredential(String accountId, String email, String password, String tenantId, String identityId) {
        Runnable op = () -> doCreateCredential(accountId, email, password, tenantId, identityId);
        Runnable retrying = Retry.decorateRunnable(retry, op);
        Runnable resilient = CircuitBreaker.decorateRunnable(circuitBreaker, retrying);

        try {
            resilient.run();
        } catch (HttpClientErrorException.Conflict e) {
            throw new CredentialAlreadyExistsConflict(accountId);
        } catch (HttpClientErrorException e) {
            // Any other 4xx is a contract violation — treat as unavailable so the signup
            // transaction rolls back rather than silently continuing with a bad payload.
            log.error("auth-service credential write returned 4xx {}: {}",
                    e.getStatusCode(), e.getMessage());
            throw new AuthServiceUnavailable("auth-service rejected credential create", e);
        } catch (Exception e) {
            log.error("auth-service credential write failed after retries: {}", e.getMessage());
            throw new AuthServiceUnavailable("auth-service is unavailable", e);
        }
    }

    @Override
    public int backfillCredentialIdentities(List<AuthServicePort.CredentialIdentityBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return 0;
        }
        List<Map<String, String>> items = bindings.stream()
                .map(b -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("accountId", b.accountId());
                    item.put("identityId", b.identityId());
                    return item;
                })
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        try {
            BackfillResponse response = restClient.post()
                    .uri(CREDENTIAL_IDENTITY_BACKFILL_PATH)
                    // TASK-BE-487: GAP client_credentials Bearer JWT (auth /internal/auth/** is JWT-only).
                    .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), "auth-service 4xx",
                                resp.getHeaders(), new byte[0], null);
                    })
                    .body(BackfillResponse.class);
            return response != null ? response.updated() : 0;
        } catch (HttpClientErrorException e) {
            log.error("auth-service credential identity backfill returned 4xx {}: {}",
                    e.getStatusCode(), e.getMessage());
            throw new AuthServiceUnavailable("auth-service rejected credential identity backfill", e);
        } catch (Exception e) {
            log.error("auth-service credential identity backfill failed: {}", e.getMessage());
            throw new AuthServiceUnavailable("auth-service is unavailable", e);
        }
    }

    /** Minimal view of auth-service's backfill response body. */
    private record BackfillResponse(int requested, int updated) {
    }

    private void doCreateCredential(String accountId, String email, String password, String tenantId,
                                    String identityId) {
        // TASK-BE-313: omit tenantId from body when null so auth-service applies its
        // own fallback ("fan-platform"); when non-null, include it so the credential
        // row matches the account row's tenant scope.
        // TASK-MONO-263 (ADR-032 D5 step 4): accountType is no longer sent — the
        // account_type claim/column is gone.
        Map<String, String> body = new LinkedHashMap<>();
        body.put("accountId", accountId);
        body.put("email", email);
        body.put("password", password);
        if (tenantId != null) {
            body.put("tenantId", tenantId);
        }
        // TASK-BE-384 (ADR-036 M2/P3): propagate the born-unified central identity so the
        // credential row is born linked. Omitted when null (mint failed → born unlinked).
        if (identityId != null) {
            body.put("identityId", identityId);
        }
        restClient.post()
                .uri(CREDENTIALS_PATH)
                // TASK-BE-487: GAP client_credentials Bearer JWT (auth /internal/auth/** is JWT-only).
                .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw HttpClientErrorException.create(
                            resp.getStatusCode(), "auth-service 4xx",
                            resp.getHeaders(), new byte[0], null);
                })
                .toBodilessEntity();
    }
}
