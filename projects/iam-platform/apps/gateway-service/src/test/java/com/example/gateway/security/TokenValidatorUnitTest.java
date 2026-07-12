package com.example.gateway.security;

import com.example.security.jwt.JwtVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /** The legacy custom-JWT issuer — retired by TASK-BE-398. */
    private static final String LEGACY_ISSUER = "iam";
    /** The SAS/OIDC issuer — what the other six gateways take as primary. */
    private static final String SAS_ISSUER = "http://localhost:8081";
    /** Production shape (TASK-MONO-365): an allowlist holding both, for the D2-b window. */
    private static final String ALLOWED_ISSUERS = SAS_ISSUER + "," + LEGACY_ISSUER;

    /** Kept so the existing cases read unchanged; the legacy issuer is still allowed. */
    private static final String EXPECTED_ISSUER = LEGACY_ISSUER;

    @Mock
    private JwksCache jwksCache;

    private TokenValidator tokenValidator;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        tokenValidator = new TokenValidator(jwksCache, ALLOWED_ISSUERS, new ObjectMapper());
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();
    }

    private String signedToken(String issuer, String kid, String subject) {
        return Jwts.builder()
                .header().keyId(kid).and()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
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
    @DisplayName("TASK-MONO-365 — SAS 발급자 토큰이 통과한다 (수정 전에는 401 이었다)")
    void acceptsTheSasIssuer() {
        // Before TASK-MONO-365 this edge pinned a SINGLE expected-issuer, defaulting to
        // the LEGACY `iam`, and JWT_EXPECTED_ISSUER was overridden in no compose file
        // anywhere. So SAS-issued tokens — the ones every other gateway takes as its
        // primary issuer — were rejected here. Nothing failed, because console-bff
        // reaches the IAM services directly and never crosses this edge (MONO-347).
        given(jwksCache.getPublicKey("test-kid"))
                .willReturn(Mono.just(Optional.of(keyPair.getPublic())));

        StepVerifier.create(tokenValidator.validate(signedToken(SAS_ISSUER, "test-kid", "account-123")))
                .assertNext(claims -> {
                    assertThat(claims.get("sub")).isEqualTo("account-123");
                    assertThat(claims.get("iss")).isEqualTo(SAS_ISSUER);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("TASK-MONO-365 AC-3 — 레거시 발급자가 일몰된 뒤에도 엣지가 살아있다 (BE-398 방어선)")
    void theEdgeSurvivesTheLegacyIssuerSunset() {
        // TASK-BE-398 (date-gated 2026-08-01) retires the legacy custom-JWT flow that
        // mints `iss=iam`. Under the OLD single-value config — which accepted `iam` and
        // nothing else — this gateway would then have had NO acceptable issuer at all:
        // a total edge outage, on a date, with nobody looking.
        //
        // This is the assertion that makes that impossible: with the legacy issuer
        // removed from the allowlist, SAS tokens still pass and legacy tokens do not.
        // When BE-398 lands, drop `,iam` from application.yml and this stays green.
        TokenValidator postSunset =
                new TokenValidator(jwksCache, SAS_ISSUER, new ObjectMapper());

        given(jwksCache.getPublicKey("test-kid"))
                .willReturn(Mono.just(Optional.of(keyPair.getPublic())));

        StepVerifier.create(postSunset.validate(signedToken(SAS_ISSUER, "test-kid", "account-123")))
                .assertNext(claims -> assertThat(claims.get("sub")).isEqualTo("account-123"))
                .verifyComplete();

        StepVerifier.create(postSunset.validate(signedToken(LEGACY_ISSUER, "test-kid", "account-123")))
                .expectError(JwtVerificationException.class)
                .verify();
    }

    @Test
    @DisplayName("TASK-MONO-365 — 빈 allowlist 는 기동을 실패시킨다 (조용히 아무나 통과시키지 않는다)")
    void anEmptyAllowlistFailsFastRatherThanTrustingEveryone() {
        // A security class whose empty config degrades to "accept anything" is how a
        // gate opens by accident (MONO-355's closed-by-default rule). Here an empty
        // allowlist is a configuration error, not a permissive default.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TokenValidator(jwksCache, "  ,  ", new ObjectMapper()));
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
