package com.example.gateway.security;

import com.example.security.jwt.JwtVerificationException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenValidator 단위 테스트")
class TokenValidatorUnitTest {

    private static final String EXPECTED_ISSUER = "global-account-platform";

    @Mock
    private JwksCache jwksCache;

    private TokenValidator tokenValidator;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        tokenValidator = new TokenValidator(jwksCache, EXPECTED_ISSUER);
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();
    }

    @Test
    @DisplayName("유효한 JWT 토큰 검증 성공")
    void validate_validToken_returnsClaims() {
        String token = Jwts.builder()
                .header().keyId("test-kid").and()
                .subject("account-123")
                .issuer(EXPECTED_ISSUER)
                .claim("email", "test@example.com")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        given(jwksCache.getPublicKey("test-kid"))
                .willReturn(Mono.just(Optional.of(keyPair.getPublic())));

        StepVerifier.create(tokenValidator.validate(token))
                .assertNext(claims -> {
                    assertThat(claims.get("sub")).isEqualTo("account-123");
                    assertThat(claims.get("email")).isEqualTo("test@example.com");
                    assertThat(claims.get("iss")).isEqualTo(EXPECTED_ISSUER);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("만료된 JWT 토큰 검증 실패")
    void validate_expiredToken_throwsJwtVerificationException() {
        String token = Jwts.builder()
                .header().keyId("test-kid").and()
                .subject("account-123")
                .issuer(EXPECTED_ISSUER)
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        given(jwksCache.getPublicKey("test-kid"))
                .willReturn(Mono.just(Optional.of(keyPair.getPublic())));

        StepVerifier.create(tokenValidator.validate(token))
                .expectError(JwtVerificationException.class)
                .verify();
    }

    @Test
    @DisplayName("변조된 JWT 토큰 검증 실패")
    void validate_tamperedToken_throwsJwtVerificationException() throws Exception {
        // Sign with a different key
        KeyPairGenerator otherKeyGen = KeyPairGenerator.getInstance("RSA");
        otherKeyGen.initialize(2048);
        KeyPair otherKeyPair = otherKeyGen.generateKeyPair();

        String token = Jwts.builder()
                .header().keyId("test-kid").and()
                .subject("account-123")
                .issuer(EXPECTED_ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Provide the original key, not the one used for signing
        given(jwksCache.getPublicKey("test-kid"))
                .willReturn(Mono.just(Optional.of(keyPair.getPublic())));

        StepVerifier.create(tokenValidator.validate(token))
                .expectError(JwtVerificationException.class)
                .verify();
    }

    @Test
    @DisplayName("kid 불일치 시 JWKS 리페치 후 검증 성공")
    void validate_kidMismatch_refreshesAndSucceeds() {
        String token = Jwts.builder()
                .header().keyId("new-kid").and()
                .subject("account-456")
                .issuer(EXPECTED_ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // First lookup returns empty
        given(jwksCache.getPublicKey("new-kid"))
                .willReturn(Mono.just(Optional.empty()));

        // Refresh returns the key
        given(jwksCache.refreshJwks())
                .willReturn(Mono.just(Map.of("new-kid", keyPair.getPublic())));

        StepVerifier.create(tokenValidator.validate(token))
                .assertNext(claims -> {
                    assertThat(claims.get("sub")).isEqualTo("account-456");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("kid 불일치 + 리페치 후에도 키 없음 시 실패")
    void validate_kidMismatch_refreshFailsToFindKey_throwsException() {
        String token = Jwts.builder()
                .header().keyId("unknown-kid").and()
                .subject("account-789")
                .issuer(EXPECTED_ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        given(jwksCache.getPublicKey("unknown-kid"))
                .willReturn(Mono.just(Optional.empty()));

        given(jwksCache.refreshJwks())
                .willReturn(Mono.just(Map.of()));

        StepVerifier.create(tokenValidator.validate(token))
                .expectError(JwtVerificationException.class)
                .verify();
    }

    @Test
    @DisplayName("iss claim 불일치 시 검증 실패 (TASK-BE-143)")
    void validate_wrongIssuer_throwsJwtVerificationException() {
        String token = Jwts.builder()
                .header().keyId("test-kid").and()
                .subject("account-123")
                .issuer("attacker-issuer")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        given(jwksCache.getPublicKey("test-kid"))
                .willReturn(Mono.just(Optional.of(keyPair.getPublic())));

        StepVerifier.create(tokenValidator.validate(token))
                .expectError(JwtVerificationException.class)
                .verify();
    }

    @Test
    @DisplayName("iss claim 누락 시 검증 실패 (TASK-BE-143)")
    void validate_missingIssuer_throwsJwtVerificationException() {
        String token = Jwts.builder()
                .header().keyId("test-kid").and()
                .subject("account-123")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        given(jwksCache.getPublicKey("test-kid"))
                .willReturn(Mono.just(Optional.of(keyPair.getPublic())));

        StepVerifier.create(tokenValidator.validate(token))
                .expectError(JwtVerificationException.class)
                .verify();
    }
}
