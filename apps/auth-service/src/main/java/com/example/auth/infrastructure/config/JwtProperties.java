package com.example.auth.infrastructure.config;

import com.example.auth.domain.service.TokenProperties;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties implements TokenProperties {

    @NotEmpty(message = "jwt.secret must not be empty — set the JWT_SECRET environment variable")
    @Size(min = 32, message = "jwt.secret must be at least 32 characters for HMAC-SHA256")
    private String secret;
    @Positive(message = "jwt.access-token-ttl-seconds must be positive")
    private long accessTokenTtlSeconds;
    @Positive(message = "jwt.refresh-token-ttl-seconds must be positive")
    private long refreshTokenTtlSeconds;
    @NotBlank(message = "jwt.issuer must not be blank")
    private String issuer;
    @NotBlank(message = "jwt.audience must not be blank")
    private String audience;

    @Setter(AccessLevel.NONE)
    private SecretKey secretKey;

    @PostConstruct
    public void initSecretKey() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                "jwt.secret must not be empty — set the JWT_SECRET environment variable");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException(
                String.format("jwt.secret must be at least 32 bytes when UTF-8 encoded, but got %d bytes. " +
                    "Multibyte characters count as multiple bytes.", secretBytes.length)
            );
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    }

    @Override
    public long refreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    @Override
    public long accessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }
}
