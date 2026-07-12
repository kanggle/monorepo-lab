package com.example.auth.infrastructure.oauth;

import com.example.auth.application.exception.OAuthCodeInvalidException;
import com.example.auth.application.exception.OAuthProviderException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * Shared helpers for the provider OAuth clients ({@code GoogleOAuthClient} /
 * {@code MicrosoftOAuthClient} / {@code KakaoOAuthClient}).
 *
 * <p>Centralizes two snippets that were previously duplicated verbatim across
 * those adapters: the HTTP/1.1-pinned {@link RestClient} factory and the
 * id_token string-claim reader. Behavior is byte-identical to the former copies.
 */
final class OAuthClientSupport {

    private OAuthClientSupport() {
        // utility class
    }

    /**
     * Build a {@link RestClient} pinned to HTTP/1.1.
     *
     * <p>Forces HTTP/1.1 to avoid the JDK HttpClient HTTP/2 RST_STREAM race against
     * WireMock stubs on Linux epoll event loops (TASK-BE-273 Phase 2, ADR-004).
     */
    static RestClient buildHttp11RestClient() {
        HttpClient jdkClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(jdkClient))
                .build();
    }

    /**
     * Read a string claim from a verified id_token claim set.
     *
     * @return the claim's {@code toString()}, or {@code null} when the claim is absent
     */
    static String stringClaim(Map<String, Object> claims, String key) {
        Object v = claims.get(key);
        return v == null ? null : v.toString();
    }

    /**
     * Classify a failure of the <b>token-exchange</b> call (TASK-MONO-350).
     *
     * <p>A <b>4xx</b> from a provider's token endpoint means the provider rejected the
     * <i>authorization code</i> (OAuth2 {@code invalid_grant}: expired, already redeemed,
     * or forged). That is the caller's problem, not an outage → {@link OAuthCodeInvalidException}
     * → {@code 401 INVALID_CODE}. Anything else (5xx, connect/read timeout, TLS, DNS) is a real
     * provider failure → {@link OAuthProviderException} → {@code 502 PROVIDER_ERROR}.
     *
     * <p><b>Apply this to the token-exchange call only.</b> Kakao and Naver make a second
     * call (user-info) with the access token they just obtained; a 4xx <i>there</i> means a
     * bad/insufficient access token, which is not a bad authorization code. Wrapping a whole
     * adapter method in this classifier would relabel those as {@code INVALID_CODE} and the
     * error code would lie again — in the opposite direction.
     */
    static RuntimeException classifyTokenExchangeFailure(String provider, RestClientException e) {
        if (e instanceof HttpClientErrorException clientError) {
            return new OAuthCodeInvalidException(
                    provider + " rejected the authorization code (" + clientError.getStatusCode() + ")", e);
        }
        return new OAuthProviderException(provider + " OAuth provider error", e);
    }
}
