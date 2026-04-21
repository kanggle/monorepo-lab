package com.example.auth.infrastructure.security;

import com.example.auth.domain.entity.User;
import com.example.auth.domain.service.TokenGenerator;
import com.example.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenGenerator implements TokenGenerator {

    private final SecretKey secretKey;
    private final long ttlSeconds;
    private final String issuer;
    private final String audience;

    public JwtTokenGenerator(JwtProperties jwtProperties) {
        if (jwtProperties.getSecretKey() == null) {
            jwtProperties.initSecretKey();
        }
        this.secretKey = jwtProperties.getSecretKey();
        this.ttlSeconds = jwtProperties.getAccessTokenTtlSeconds();
        this.issuer = jwtProperties.getIssuer();
        this.audience = jwtProperties.getAudience();
    }

    @Override
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail().value())
            .claim("role", user.getRole().name())
            .issuer(issuer)
            .audience().add(audience).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
            .signWith(secretKey)
            .compact();
    }

    @Override
    public long accessTokenTtlSeconds() {
        return ttlSeconds;
    }
}
