package com.example.auth.infrastructure.security;

import com.example.auth.domain.entity.User;
import com.example.auth.domain.service.ParsedToken;
import com.example.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtTokenParser 단위 테스트")
class JwtTokenParserTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes!!";
    private static final long TTL_SECONDS = 3600L;
    private static final String ISSUER = "auth-service";
    private static final String AUDIENCE = "api";

    private JwtTokenParser parser;
    private JwtTokenGenerator generator;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setAccessTokenTtlSeconds(TTL_SECONDS);
        props.setRefreshTokenTtlSeconds(2592000L);
        props.setIssuer(ISSUER);
        props.setAudience(AUDIENCE);
        parser = new JwtTokenParser(props);
        generator = new JwtTokenGenerator(props);
    }

    @Test
    @DisplayName("parse - 생성된 토큰에서 userId와 email을 한 번에 복원")
    void parse_roundtrip() {
        User user = User.create("test@example.com", "hash", "홍길동");
        String token = generator.generateAccessToken(user);
        ParsedToken parsed = parser.parse(token);
        assertThat(parsed.userId()).isEqualTo(user.getId());
        assertThat(parsed.email()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("parseUserId - 생성된 토큰에서 userId 복원")
    void parseUserId_roundtrip() {
        User user = User.create("test@example.com", "hash", "홍길동");
        String token = generator.generateAccessToken(user);
        UUID parsed = parser.parseUserId(token);
        assertThat(parsed).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("parseUserId - 만료된 토큰이면 JwtException 발생")
    void parseUserId_expiredToken_throws() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuer(ISSUER)
            .audience().add(AUDIENCE).and()
            .issuedAt(new Date(System.currentTimeMillis() - 10_000))
            .expiration(new Date(System.currentTimeMillis() - 5_000))
            .signWith(key)
            .compact();

        assertThatThrownBy(() -> parser.parseUserId(expiredToken))
            .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("parseUserId - 잘못된 서명이면 JwtException 발생")
    void parseUserId_wrongSecret_throws() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
            "another-secret-key-at-least-32-bytes!!!!!".getBytes(StandardCharsets.UTF_8));
        String tokenWithWrongSecret = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuer(ISSUER)
            .audience().add(AUDIENCE).and()
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(wrongKey)
            .compact();

        assertThatThrownBy(() -> parser.parseUserId(tokenWithWrongSecret))
            .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("parseUserId - issuer 불일치이면 JwtException 발생")
    void parseUserId_wrongIssuer_throws() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithWrongIssuer = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuer("wrong-issuer")
            .audience().add(AUDIENCE).and()
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(key)
            .compact();

        assertThatThrownBy(() -> parser.parseUserId(tokenWithWrongIssuer))
            .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("parseEmail - 생성된 토큰에서 email 복원")
    void parseEmail_roundtrip() {
        User user = User.create("test@example.com", "hash", "홍길동");
        String token = generator.generateAccessToken(user);
        String parsed = parser.parseEmail(token);
        assertThat(parsed).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("parseEmail - email claim 없는 토큰이면 JwtException 발생")
    void parseEmail_missingClaim_throws() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithoutEmail = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuer(ISSUER)
            .audience().add(AUDIENCE).and()
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(key)
            .compact();

        assertThatThrownBy(() -> parser.parseEmail(tokenWithoutEmail))
            .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("parseUserId - audience 불일치이면 JwtException 발생")
    void parseUserId_wrongAudience_throws() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithWrongAudience = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuer(ISSUER)
            .audience().add("wrong-audience").and()
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(key)
            .compact();

        assertThatThrownBy(() -> parser.parseUserId(tokenWithWrongAudience))
            .isInstanceOf(JwtException.class);
    }
}
