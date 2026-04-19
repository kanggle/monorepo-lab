package com.wms.master.integration.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Integration-test JWT helper: generates an RSA keypair per test run, serves
 * the public key as a JWKS endpoint from a local MockWebServer, and issues
 * signed tokens with configurable roles.
 *
 * <p>The {@code role} claim matches the claim name that {@link
 * com.wms.master.config.SecurityConfig} extracts. Tokens produced here are
 * accepted by the running {@code JwtAuthenticationProvider} with no special
 * cases.
 */
public final class JwtTestHelper implements AutoCloseable {

    private static final String KEY_ID = "master-test-key";
    private static final String ISSUER = "master-service-integration-test";

    private final MockWebServer jwksServer;
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    private JwtTestHelper(MockWebServer server, RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        this.jwksServer = server;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public static JwtTestHelper start() throws Exception {
        KeyPair pair = generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
        RSAPrivateKey priv = (RSAPrivateKey) pair.getPrivate();

        JWKSet jwkSet = new JWKSet(new RSAKey.Builder(pub)
                .keyID(KEY_ID)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build());
        String jwksJson = jwkSet.toString();

        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().contains("jwks")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(jwksJson);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();

        return new JwtTestHelper(server, priv, pub);
    }

    public String jwkSetUri() {
        return jwksServer.url("/.well-known/jwks.json").toString();
    }

    /**
     * Issue a token with a single role in the {@code role} claim. TTL 1 hour.
     */
    public String issueToken(String subject, String role) {
        return issueToken(subject, List.of(role), Duration.ofHours(1));
    }

    public String issueToken(String subject, List<String> roles, Duration ttl) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(ISSUER)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plus(ttl)));
        if (roles == null || roles.isEmpty()) {
            // no role claim
        } else if (roles.size() == 1) {
            claims.claim("role", roles.get(0));
        } else {
            claims.claim("role", roles);
        }
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build();
        SignedJWT jwt = new SignedJWT(header, claims.build());
        try {
            jwt.sign(new RSASSASigner(privateKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign test JWT", e);
        }
        return jwt.serialize();
    }

    public String bearerHeader(String subject, String role) {
        return "Bearer " + issueToken(subject, role);
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    @Override
    public void close() {
        try {
            jwksServer.shutdown();
        } catch (Exception e) {
            // best-effort on test teardown
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA keypair generator unavailable", e);
        }
    }
}
