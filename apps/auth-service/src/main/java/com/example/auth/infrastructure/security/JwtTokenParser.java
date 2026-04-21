package com.example.auth.infrastructure.security;

import com.example.auth.domain.service.ParsedToken;
import com.example.auth.domain.service.TokenParser;
import com.example.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtTokenParser implements TokenParser {

    private final JwtParser jwtParser;

    public JwtTokenParser(JwtProperties jwtProperties) {
        if (jwtProperties.getSecretKey() == null) {
            jwtProperties.initSecretKey();
        }
        this.jwtParser = Jwts.parser()
            .verifyWith(jwtProperties.getSecretKey())
            .requireIssuer(jwtProperties.getIssuer())
            .requireAudience(jwtProperties.getAudience())
            .build();
    }

    @Override
    public ParsedToken parse(String token) {
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new JwtException("Token subject is missing");
        }
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Token subject is not a valid UUID", e);
        }

        String email = claims.get("email", String.class);
        if (email == null || email.isBlank()) {
            throw new JwtException("Token email claim is missing");
        }

        return new ParsedToken(userId, email);
    }
}
