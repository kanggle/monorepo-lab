package com.example.admin.infrastructure.security;

import com.example.admin.application.exception.InvalidBootstrapTokenException;
import com.gap.security.jwt.JwtVerifier;
import com.gap.security.jwt.Rs256JwtSigner;
import com.gap.security.jwt.Rs256JwtVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapTokenServiceTest {

    private KeyPair keyPair;
    private JwtSigner signer;
    private JwtVerifier verifier;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private BootstrapTokenService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.keyPair = gen.generateKeyPair();
        // Build a JwtSigner that reuses the admin JwtSigner type (issuer + kid injection).
        AdminJwtKeyStore keyStore = fixtureKeyStore((RSAPrivateKey) keyPair.getPrivate());
        this.signer = new JwtSigner(keyStore, "admin-service");
        this.verifier = new Rs256JwtVerifier((RSAPublicKey) keyPair.getPublic());

        this.redis = mock(StringRedisTemplate.class);
        this.valueOps = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        this.service = new BootstrapTokenService(signer, verifier, redis, "admin-service");
    }

    @Test
    void issueThenVerifyAndConsumeSucceedsOnce() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        BootstrapTokenService.Issued issued = service.issue("op-1");
        BootstrapContext ctx = service.verifyAndConsume(issued.token());

        assertThat(ctx.operatorId()).isEqualTo("op-1");
        assertThat(ctx.jti()).isEqualTo(issued.jti());
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps, times(1)).setIfAbsent(keyCaptor.capture(), eq("1"), any(Duration.class));
        assertThat(keyCaptor.getValue()).isEqualTo("admin:bootstrap:jti:" + issued.jti());
    }

    @Test
    void replayedJtiIsRejected() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false);
        BootstrapTokenService.Issued issued = service.issue("op-1");

        service.verifyAndConsume(issued.token()); // first consume ok
        assertThatThrownBy(() -> service.verifyAndConsume(issued.token()))
                .isInstanceOf(InvalidBootstrapTokenException.class)
                .hasMessageContaining("already consumed");
    }

    @Test
    void wrongTokenTypeIsRejected() {
        // Sign a token with token_type=admin (not admin_bootstrap) using the same key.
        Rs256JwtSigner rawSigner = new Rs256JwtSigner(keyPair.getPrivate(), "v1");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "op-1");
        claims.put("iss", "admin-service");
        claims.put("jti", "jti-x");
        claims.put("token_type", "admin");
        claims.put("iat", Instant.now());
        claims.put("exp", Instant.now().plus(5, ChronoUnit.MINUTES));
        String bad = rawSigner.sign(claims);

        assertThatThrownBy(() -> service.verifyAndConsume(bad))
                .isInstanceOf(InvalidBootstrapTokenException.class)
                .hasMessageContaining("token_type");
    }

    @Test
    void issueIncludesScopeClaimAndVerifiesWithMatchingScope() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        BootstrapTokenService.Issued issued = service.issue(
                "op-1",
                java.util.Set.of(BootstrapTokenService.SCOPE_ENROLL, BootstrapTokenService.SCOPE_VERIFY));
        // verify claim present via JwtVerifier
        Map<String, Object> decoded = verifier.verify(issued.token());
        assertThat(decoded.get("scope"))
                .isInstanceOf(java.util.List.class)
                .asList()
                .containsExactlyInAnyOrder(BootstrapTokenService.SCOPE_ENROLL, BootstrapTokenService.SCOPE_VERIFY);

        BootstrapContext ctx = service.verifyAndConsume(issued.token(), BootstrapTokenService.SCOPE_ENROLL);
        assertThat(ctx.operatorId()).isEqualTo("op-1");
    }

    @Test
    void verifyRejectsTokenMissingRequiredScope() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        BootstrapTokenService.Issued issued = service.issue(
                "op-1", java.util.Set.of(BootstrapTokenService.SCOPE_ENROLL));

        assertThatThrownBy(() ->
                service.verifyAndConsume(issued.token(), BootstrapTokenService.SCOPE_VERIFY))
                .isInstanceOf(InvalidBootstrapTokenException.class)
                .hasMessageContaining("scope");
    }

    @Test
    void redisOutageFailsClosed() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));

        BootstrapTokenService.Issued issued = service.issue("op-1");
        assertThatThrownBy(() -> service.verifyAndConsume(issued.token()))
                .isInstanceOf(InvalidBootstrapTokenException.class)
                .hasMessageContaining("unavailable");
    }

    private static AdminJwtKeyStore fixtureKeyStore(RSAPrivateKey pk) {
        // Build an AdminJwtKeyStore-compatible map for kid "v1".
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return new AdminJwtKeyStore(Map.of("v1", pem), "v1");
    }
}
