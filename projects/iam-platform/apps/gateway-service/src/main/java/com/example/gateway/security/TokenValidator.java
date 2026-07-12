package com.example.gateway.security;

import com.example.security.jwt.JwtVerificationException;
import com.example.security.jwt.Rs256JwtVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Validates JWT tokens using JWKS-resolved public keys.
 * Extracts kid from JWT header, resolves the public key via JwksCache,
 * and verifies signature + exp/nbf claims + an {@code iss} allowlist.
 *
 * <p><strong>Allowlist, not a single value (TASK-MONO-365).</strong> IAM mints
 * <em>two</em> issuers during the D2-b deprecation window: the SAS/OIDC issuer URL
 * (auth-service {@code application.yml} L177) and the legacy {@code "iam"} string
 * (L114). Every other gateway accepts both, via a CSV allowlist.
 *
 * <p>This edge used to pin a <em>single</em> {@code expected-issuer}, defaulting to
 * the <em>legacy</em> value — and {@code JWT_EXPECTED_ISSUER} was overridden in no
 * compose file anywhere. So SAS-issued tokens, the ones the rest of the fleet takes
 * as primary, were rejected here with a 401. It went unnoticed because console-bff
 * reaches the IAM services directly and never crosses this edge (TASK-MONO-347).
 *
 * <p>Worse, {@code TASK-BE-398} retires the legacy custom-JWT flow that mints
 * {@code iss=iam}. Under the old single-value config, that would have left this
 * gateway with <strong>no acceptable issuer at all</strong>.
 */
@Slf4j
@Component
public class TokenValidator {

    private final JwksCache jwksCache;
    private final List<String> allowedIssuers;
    private final ObjectMapper objectMapper;

    /**
     * @param allowedIssuersCsv comma-separated {@code iss} values. Must be non-empty:
     *                          an empty allowlist would accept nothing, and silently
     *                          defaulting to "accept anything" in a security class is
     *                          how a gate opens by accident (TASK-MONO-355).
     */
    public TokenValidator(JwksCache jwksCache,
                          @org.springframework.beans.factory.annotation.Value("${gateway.jwt.allowed-issuers}") String allowedIssuersCsv,
                          ObjectMapper objectMapper) {
        this.jwksCache = jwksCache;
        this.allowedIssuers = parseCsv(allowedIssuersCsv);
        this.objectMapper = objectMapper;
    }

    private static List<String> parseCsv(String csv) {
        List<String> issuers = csv == null ? List.of()
                : Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        if (issuers.isEmpty()) {
            throw new IllegalArgumentException(
                    "gateway.jwt.allowed-issuers must not be empty — an edge that trusts no issuer "
                            + "rejects every token, and one that trusts any issuer is not an edge");
        }
        return issuers;
    }

    /**
     * Validates the JWT token and returns the claims if valid.
     *
     * @param token the compact JWT string (without "Bearer " prefix)
     * @return Mono of claims map on success, Mono.error on failure
     */
    public Mono<Map<String, Object>> validate(String token) {
        return Mono.fromCallable(() -> extractKid(token))
                .flatMap(kid -> jwksCache.getPublicKey(kid)
                        .flatMap(optKey -> {
                            if (optKey.isEmpty()) {
                                // kid not found, try refresh
                                return jwksCache.refreshJwks()
                                        .flatMap(keys -> {
                                            PublicKey key = keys.get(kid);
                                            if (key == null) {
                                                return Mono.error(new JwtVerificationException(
                                                        "No matching key found for kid: " + kid));
                                            }
                                            return verifyWithKey(token, key);
                                        });
                            }
                            return verifyWithKey(token, optKey.get());
                        }));
    }

    private String extractKid(String token) {
        // Parse the header without verification to get kid
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new JwtVerificationException("Invalid JWT format");
        }
        try {
            String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
            @SuppressWarnings("unchecked")
            Map<String, Object> header = objectMapper.readValue(headerJson, Map.class);
            String kid = (String) header.get("kid");
            if (kid == null || kid.isBlank()) {
                throw new JwtVerificationException("JWT missing kid header");
            }
            return kid;
        } catch (JwtVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtVerificationException("Failed to extract kid from JWT header", e);
        }
    }

    /**
     * Signature + expiry are verified by the shared {@link Rs256JwtVerifier}; the
     * {@code iss} check is done here because that verifier pins a <em>single</em>
     * expected issuer and this edge needs an allowlist. A missing or blank {@code iss}
     * is rejected — the allowlist decides which issuers are trusted, not whether the
     * claim is required.
     */
    private Mono<Map<String, Object>> verifyWithKey(String token, PublicKey publicKey) {
        return Mono.fromCallable(() -> {
            Map<String, Object> claims = new Rs256JwtVerifier(publicKey).verify(token);

            Object rawIss = claims.get("iss");
            String iss = rawIss instanceof String s ? s.trim() : null;
            if (iss == null || iss.isEmpty() || !allowedIssuers.contains(iss)) {
                throw new JwtVerificationException(
                        "iss '" + iss + "' is not in the allowed list");
            }
            return claims;
        });
    }
}
