package com.example.admin.infrastructure.security;

import com.example.admin.application.exception.SubjectTokenInvalidException;
import com.example.admin.application.port.GapOidcSubjectTokenValidator;
import com.example.admin.infrastructure.config.GapOidcProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TASK-BE-298 / ADR-MONO-014 — infrastructure adapter that validates a GAP
 * OIDC {@code platform-console-web} subject token against the <b>auth-service
 * JWKS</b> and enforces the policy defined in
 * {@code specs/services/admin-service/security.md} §GAP OIDC Subject-Token
 * Validation.
 *
 * <p>Servlet-side (admin-service is MVC, not WebFlux — the gateway's reactive
 * {@code JwksCache} cannot be reused), so this is a focused validator:
 * a {@link RestClient} JWKS fetcher with an in-memory kid→key cache that
 * refreshes once on an unknown {@code kid}, plus JJWT RS256 verification.
 *
 * <p><b>Fail-closed everywhere</b> (task Implementation Notes): JWKS
 * unreachable, kid unknown after refresh, signature/iss/aud/exp/nbf failure,
 * a present {@code token_type} claim (= an admin-service-minted token, not a
 * GAP OIDC access token), or a missing {@code sub} all raise
 * {@link SubjectTokenInvalidException} — never a partial trust / minted token.
 * This validator's key space (auth-service JWKS) is strictly separate from the
 * admin-service operator IdP signing key.
 */
@Slf4j
public class GapOidcJwksSubjectTokenValidator implements GapOidcSubjectTokenValidator {

    private final GapOidcProperties properties;
    private final RestClient jwksRestClient;
    private final ObjectMapper objectMapper;

    /** kid → RSA public key. Refreshed wholesale on an unknown kid. */
    private final ConcurrentHashMap<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();
    private final AtomicReference<Long> lastRefreshEpochMs = new AtomicReference<>(0L);
    /** Don't hammer auth-service: at most one forced refresh per this window. */
    private static final long MIN_REFRESH_INTERVAL_MS = 5_000L;

    public GapOidcJwksSubjectTokenValidator(GapOidcProperties properties,
                                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.jwksRestClient = RestClient.builder()
                .baseUrl(properties.getJwksUri())
                .requestFactory(factory)
                .build();
    }

    @Override
    public String validateAndExtractSubject(String subjectToken) {
        if (subjectToken == null || subjectToken.isBlank()) {
            throw new SubjectTokenInvalidException("subject_token is missing");
        }

        String kid = extractKid(subjectToken);
        RSAPublicKey key = resolveKey(kid);

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .requireAudience(properties.getAudience())
                    .clockSkewSeconds(properties.getClockSkewSeconds())
                    .build()
                    .parseSignedClaims(subjectToken)
                    .getPayload();
        } catch (JwtException e) {
            // Signature / iss / aud / exp / nbf — any failure is fail-closed.
            log.debug("GAP OIDC subject token rejected: {}", e.getMessage());
            throw new SubjectTokenInvalidException("Subject token verification failed");
        }

        // A GAP OIDC access token never carries a `token_type` custom claim
        // (auth-api.md Token Claims). Presence means an admin-service-minted
        // operator/bootstrap token is being smuggled as a subject token —
        // reject (security.md validation #5).
        if (claims.get("token_type") != null) {
            throw new SubjectTokenInvalidException(
                    "Subject token is not a GAP OIDC access token");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new SubjectTokenInvalidException("Subject token has no sub claim");
        }
        return subject;
    }

    /** Reads the JWS header {@code kid} without trusting the (yet-unverified) body. */
    private String extractKid(String token) {
        try {
            int firstDot = token.indexOf('.');
            if (firstDot <= 0) {
                throw new SubjectTokenInvalidException("Malformed subject token");
            }
            byte[] headerJson = Base64.getUrlDecoder()
                    .decode(token.substring(0, firstDot));
            JsonNode header = objectMapper.readTree(headerJson);
            JsonNode kidNode = header.get("kid");
            // kid may be absent — resolveKey then uses the sole/any key.
            return kidNode != null && kidNode.isTextual() ? kidNode.asText() : null;
        } catch (SubjectTokenInvalidException e) {
            throw e;
        } catch (RuntimeException | java.io.IOException e) {
            throw new SubjectTokenInvalidException("Malformed subject token header");
        }
    }

    private RSAPublicKey resolveKey(String kid) {
        RSAPublicKey cached = lookup(kid);
        if (cached != null) {
            return cached;
        }
        // Unknown kid (or empty cache): refresh once (rate-limited), fail-closed.
        refreshJwks();
        RSAPublicKey afterRefresh = lookup(kid);
        if (afterRefresh == null) {
            throw new SubjectTokenInvalidException(
                    "No JWKS key matches the subject token kid");
        }
        return afterRefresh;
    }

    private RSAPublicKey lookup(String kid) {
        if (kid != null) {
            return keyCache.get(kid);
        }
        // No kid in header: only unambiguous if exactly one key is cached.
        if (keyCache.size() == 1) {
            return keyCache.values().iterator().next();
        }
        return null;
    }

    private synchronized void refreshJwks() {
        long now = System.currentTimeMillis();
        Long last = lastRefreshEpochMs.get();
        if (last != null && now - last < MIN_REFRESH_INTERVAL_MS && !keyCache.isEmpty()) {
            // Already refreshed very recently and we have keys — don't hammer
            // auth-service. The caller's lookup-after-refresh will fail-closed.
            return;
        }
        try {
            String body = jwksRestClient.get()
                    .retrieve()
                    .body(String.class);
            Map<String, RSAPublicKey> parsed = parseJwks(body);
            if (parsed.isEmpty()) {
                throw new IllegalStateException("auth-service JWKS contained no usable RSA keys");
            }
            keyCache.clear();
            keyCache.putAll(parsed);
            lastRefreshEpochMs.set(now);
            log.debug("Refreshed GAP OIDC JWKS: {} key(s)", parsed.size());
        } catch (RuntimeException e) {
            // Fail-closed: do NOT trust the token without a verified key.
            log.warn("GAP OIDC JWKS fetch failed (fail-closed): {}", e.getMessage());
            throw new SubjectTokenInvalidException(
                    "auth-service JWKS unavailable; subject token cannot be verified");
        }
    }

    private Map<String, RSAPublicKey> parseJwks(String json) {
        try {
            Map<String, RSAPublicKey> out = new LinkedHashMap<>();
            JsonNode root = objectMapper.readTree(json);
            JsonNode keys = root.get("keys");
            if (keys == null || !keys.isArray()) {
                return out;
            }
            int anon = 0;
            for (JsonNode k : keys) {
                JsonNode kty = k.get("kty");
                if (kty == null || !"RSA".equals(kty.asText())) {
                    continue;
                }
                JsonNode nNode = k.get("n");
                JsonNode eNode = k.get("e");
                if (nNode == null || eNode == null) {
                    continue;
                }
                java.math.BigInteger modulus = new java.math.BigInteger(
                        1, Base64.getUrlDecoder().decode(nNode.asText()));
                java.math.BigInteger exponent = new java.math.BigInteger(
                        1, Base64.getUrlDecoder().decode(eNode.asText()));
                PublicKey pk = KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(modulus, exponent));
                JsonNode kidNode = k.get("kid");
                String kid = kidNode != null && kidNode.isTextual()
                        ? kidNode.asText()
                        : "__nokid_" + (anon++);
                out.put(kid, (RSAPublicKey) pk);
            }
            return out;
        } catch (RuntimeException | java.security.GeneralSecurityException
                 | java.io.IOException e) {
            throw new IllegalStateException("Failed to parse auth-service JWKS", e);
        }
    }

    /** Visible for tests: claims this validator recognizes as non-OIDC. */
    static final Set<String> ADMIN_TOKEN_TYPE_MARKERS =
            Set.of("admin", "admin_refresh", "admin_bootstrap");
}
