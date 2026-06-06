package com.example.admin.infrastructure.config;

import com.example.admin.infrastructure.security.BootstrapTokenService;
import com.example.admin.infrastructure.security.JwtSigner;
import com.example.admin.infrastructure.security.TotpSecretCipher;
import com.example.security.jwt.JwtVerifier;
import com.example.security.password.Argon2idPasswordHasher;
import com.example.security.password.PasswordHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires the TOTP primitives (cipher + generator), the Argon2id
 * {@link PasswordHasher} used to hash recovery codes, and the
 * {@link BootstrapTokenService} for the 2FA sub-tree.
 *
 * <p>The production filter registration for {@link com.example.admin.infrastructure.security.BootstrapAuthenticationFilter}
 * lives in {@code SecurityConfig} so the two filters share a single chain.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AdminTotpProperties.class)
public class TotpConfig {

    // Base64 encoding of the dev-only 32-byte literal
    // "DEV_ONLY_TOTP_KEY_32_BYTES_!!!XX" shipped in application.yml.
    private static final String DEV_PLACEHOLDER_MARKER = "REVWX09OTFlfVE9UUF9LRVlfMzJfQllURVNfISEhWFg=";

    @Bean
    public TotpSecretCipher totpSecretCipher(AdminTotpProperties properties) {
        warnIfDevPlaceholder(properties);
        return new TotpSecretCipher(properties);
    }

    @Bean
    public PasswordHasher passwordHasher() {
        return new Argon2idPasswordHasher();
    }

    @Bean
    public BootstrapTokenService bootstrapTokenService(JwtSigner signer,
                                                       JwtVerifier operatorJwtVerifier,
                                                       StringRedisTemplate redis,
                                                       AdminJwtProperties jwtProperties) {
        return new BootstrapTokenService(signer, operatorJwtVerifier, redis, jwtProperties.getIssuer());
    }

    private static void warnIfDevPlaceholder(AdminTotpProperties props) {
        if (props.getEncryptionKeys() == null) return;
        props.getEncryptionKeys().forEach((kid, b64) -> {
            if (b64 != null && b64.contains(DEV_PLACEHOLDER_MARKER)) {
                log.warn("admin.totp.encryption-keys[{}] is using a dev-only placeholder — "
                        + "do NOT deploy to production with this configuration", kid);
            }
        });
    }
}
