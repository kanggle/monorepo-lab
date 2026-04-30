package com.example.auth.infrastructure.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.Key;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Verifies OIDC id_tokens against a provider-published JWKS endpoint
 * (TASK-BE-145).
 *
 * <p>Performs full validation per OpenID Connect Core §3.1.3.7:
 * <ol>
 *   <li>RS256 signature against the JWK whose {@code kid} matches the JWT
 *       header's {@code kid}. Algorithm is pinned to {@code RS256} only —
 *       {@code alg: none} / {@code alg: HS256} confusion attacks are rejected
 *       both by JJWT's unsecured-disabled default and by this explicit pin.</li>
 *   <li>{@code iss} matches the configured issuer pattern (regex — supports
 *       Microsoft multi-tenant patterns like
 *       {@code https://login.microsoftonline.com/<tenantId>/v2.0}).</li>
 *   <li>{@code aud} contains the configured audience (typically the OAuth
 *       client_id).</li>
 *   <li>{@code exp} is in the future, with a 60s clock-skew tolerance for
 *       provider/host clock drift.</li>
 * </ol>
 *
 * <p>JWKS keys are cached for {@code cacheTtlMillis} (default 1 hour). On a
 * {@code kid} cache-miss the cache is force-refreshed once before failing.
 * Refresh failure on a populated cache reuses the previous keys (degraded
 * mode); refresh failure on first load propagates as
 * {@link OAuthProviderException}.
 *
 * <p>Thread-safe: cache reads/writes are synchronized on {@code this}; the
 * inner key map is replaced atomically (volatile reference).
 */
@Slf4j
public class OidcJwksVerifier {

    private static final long REFRESH_FAILURE_BACKOFF_MILLIS = 60_000L;

    private final String jwksUri;
    private final Pattern expectedIssuerPattern;
    private final String expectedAudience;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final long cacheTtlMillis;

    private volatile Map<String, RSAPublicKey> keyCache = Map.of();
    private volatile long cacheLoadedAt;
    private volatile long lastRefreshFailureAt;

    public OidcJwksVerifier(String jwksUri,
                            String expectedIssuerPattern,
                            String expectedAudience,
                            RestClient restClient,
                            ObjectMapper objectMapper,
                            long cacheTtlMillis) {
        this.jwksUri = jwksUri;
        this.expectedIssuerPattern = Pattern.compile(expectedIssuerPattern);
        this.expectedAudience = expectedAudience;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.cacheTtlMillis = cacheTtlMillis;
    }

    /**
     * Verify the id_token and return its claims. Throws
     * {@link OAuthProviderException} for any signature/issuer/audience/expiry
     * failure. The exception carries no token bytes and only a short
     * description so call-site logs cannot leak credential material.
     */
    public Map<String, Object> verify(String idToken) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(new JwksKeyLocator())
                    .clockSkewSeconds(60)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new OAuthProviderException("id_token expired");
        } catch (JwtException e) {
            throw new OAuthProviderException("id_token signature verification failed");
        }

        String iss = claims.getIssuer();
        if (iss == null || !expectedIssuerPattern.matcher(iss).matches()) {
            throw new OAuthProviderException("id_token issuer mismatch");
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(expectedAudience)) {
            throw new OAuthProviderException("id_token audience mismatch");
        }

        return new java.util.LinkedHashMap<>(claims);
    }

    private final class JwksKeyLocator implements Locator<Key> {
        @Override
        public Key locate(io.jsonwebtoken.Header header) {
            if (!(header instanceof JwsHeader jws)) {
                throw new OAuthProviderException("id_token missing JWS header");
            }
            // TASK-BE-145 W-1: explicit algorithm pinning. JJWT 0.12.x has no
            // builder-level sigAlgorithms() API in this release, so we enforce
            // RS256 here before the JWS subsystem ever uses the resolved key.
            // Defense-in-depth on top of JJWT's implicit guards:
            //  - alg:none is rejected by unsecured-disabled default
            //  - alg:HS256 is rejected when DefaultMacAlgorithm sees an RSAPublicKey
            // Pinning here makes the policy explicit and survives library upgrades.
            String alg = jws.getAlgorithm();
            if (!"RS256".equals(alg)) {
                throw new OAuthProviderException("id_token alg must be RS256");
            }
            String kid = jws.getKeyId();
            if (kid == null || kid.isBlank()) {
                throw new OAuthProviderException("id_token missing kid header");
            }
            RSAPublicKey key = currentKeys().get(kid);
            if (key == null) {
                key = forceRefresh().get(kid);
            }
            if (key == null) {
                throw new OAuthProviderException("id_token kid not in JWKS");
            }
            return key;
        }
    }

    private Map<String, RSAPublicKey> currentKeys() {
        long now = System.currentTimeMillis();
        if (cacheLoadedAt == 0) {
            return forceRefresh();
        }
        if (now - cacheLoadedAt > cacheTtlMillis) {
            try {
                return forceRefresh();
            } catch (OAuthProviderException e) {
                // Degraded mode: serve stale keys until backoff window elapses.
                log.warn("JWKS refresh failed, serving stale keys: {}", e.getMessage());
                return keyCache;
            }
        }
        return keyCache;
    }

    private synchronized Map<String, RSAPublicKey> forceRefresh() {
        long now = System.currentTimeMillis();
        // Coalesce: if another thread refreshed since we entered, reuse
        if (now - cacheLoadedAt < 1_000L && !keyCache.isEmpty()) {
            return keyCache;
        }
        if (now - lastRefreshFailureAt < REFRESH_FAILURE_BACKOFF_MILLIS && keyCache.isEmpty()) {
            throw new OAuthProviderException("JWKS endpoint repeatedly unreachable");
        }
        try {
            String body = restClient.get().uri(jwksUri).retrieve().body(String.class);
            Map<String, RSAPublicKey> fresh = parseJwks(body);
            if (fresh.isEmpty()) {
                throw new OAuthProviderException("JWKS endpoint returned no usable keys");
            }
            keyCache = fresh;
            cacheLoadedAt = now;
            lastRefreshFailureAt = 0L;
            return fresh;
        } catch (OAuthProviderException e) {
            lastRefreshFailureAt = now;
            throw e;
        } catch (Exception e) {
            lastRefreshFailureAt = now;
            throw new OAuthProviderException("JWKS endpoint unreachable");
        }
    }

    private Map<String, RSAPublicKey> parseJwks(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode keys = root.path("keys");
            Map<String, RSAPublicKey> out = new HashMap<>();
            Iterator<JsonNode> it = keys.elements();
            while (it.hasNext()) {
                JsonNode jwk = it.next();
                String kty = jwk.path("kty").asText("");
                if (!"RSA".equals(kty)) {
                    continue;
                }
                String kid = jwk.path("kid").asText(null);
                String n = jwk.path("n").asText(null);
                String e = jwk.path("e").asText(null);
                if (kid == null || n == null || e == null) {
                    continue;
                }
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
                RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(modulus, exponent));
                out.put(kid, publicKey);
            }
            return out;
        } catch (Exception e) {
            throw new OAuthProviderException("JWKS endpoint returned malformed body");
        }
    }
}
