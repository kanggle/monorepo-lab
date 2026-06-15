package com.example.auth.infrastructure.oauth;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

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
}
