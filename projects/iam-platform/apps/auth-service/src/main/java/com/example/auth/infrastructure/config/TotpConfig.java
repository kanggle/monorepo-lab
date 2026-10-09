package com.example.auth.infrastructure.config;

import com.example.auth.application.port.TotpSecretCipher;
import com.example.auth.infrastructure.totp.AesGcmTotpSecretCipher;
import com.example.auth.infrastructure.totp.TotpProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TASK-MONO-771 — wires the account-plane TOTP secret cipher from {@code auth.totp.*}. The keys are validated
 * when the bean is built, so a malformed key stops the boot instead of the first enrollment.
 */
@Configuration
@EnableConfigurationProperties(TotpProperties.class)
public class TotpConfig {

    @Bean
    public TotpSecretCipher totpSecretCipher(TotpProperties properties) {
        return new AesGcmTotpSecretCipher(properties.encryptionKeyId(), properties.encryptionKeys());
    }
}
