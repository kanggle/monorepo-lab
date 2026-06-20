package com.example.order.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

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

/**
 * Integration-test JWT helper for the order-service {@code /api/internal/**} resource
 * server (TASK-BE-412). Generates an RSA keypair per test run, serves the public key as
 * a JWKS endpoint from a local {@link MockWebServer}, and mints {@code client_credentials}-
 * style access tokens signed by that key. The order-service resource-server chain points
 * its {@code order.internal.oauth2.jwk-set-uri} at {@link #jwkSetUri()} (via
 * {@code @DynamicPropertySource}), so a token issued here verifies end-to-end exactly as a
 * real GAP-issued one would — no special cases.
 *
 * <p>Mirrors the wms master-service {@code JwtTestHelper} (TASK-MONO-019). A wrong-issuer
 * or wrong-audience token can be minted via {@link #issueToken} to prove the fail-closed
 * validators.
 */
public final class InternalJwtTestHelper implements AutoCloseable {

    private static final String KEY_ID = "order-internal-test-key";

    public static final String ISSUER = "http://test-iam";
    public static final String AUDIENCE = "order-service";

    private final MockWebServer jwksServer;
    private final RSAPrivateKey privateKey;

    private InternalJwtTestHelper(MockWebServer server, RSAPrivateKey privateKey) {
        this.jwksServer = server;
        this.privateKey = privateKey;
    }

    public static InternalJwtTestHelper start() throws Exception {
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

        return new InternalJwtTestHelper(server, priv);
    }

    public String jwkSetUri() {
        return jwksServer.url("/oauth2/jwks").toString();
    }

    /** A valid client_credentials token: configured issuer + audience, 1h TTL. */
    public String validToken() {
        return issueToken("ecommerce-internal-services-client", ISSUER, AUDIENCE, Duration.ofHours(1));
    }

    public String issueToken(String subject, String issuer, String audience, Duration ttl) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plus(ttl)));
        if (audience != null) {
            claims.audience(List.of(audience));
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
