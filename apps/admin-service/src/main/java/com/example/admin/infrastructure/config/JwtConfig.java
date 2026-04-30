package com.example.admin.infrastructure.config;

import com.example.admin.infrastructure.security.AdminJwtKeyStore;
import com.example.admin.infrastructure.security.IssuerEnforcingJwtVerifier;
import com.example.admin.infrastructure.security.JwtSigner;
import com.gap.security.jwt.JwtVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Wires the admin-service operator IdP:
 * <ul>
 *   <li>{@link AdminJwtKeyStore} — kid → RSA key map, fail-fast validation</li>
 *   <li>{@link JwtSigner} — active-kid signer for 029-2/029-3 login + 2FA flows</li>
 *   <li>Primary {@link JwtVerifier} ({@code operatorJwtVerifier}) — used by
 *       {@code OperatorAuthenticationFilter}. Enforces
 *       {@code iss=admin-service} on top of RS256 signature verification.</li>
 * </ul>
 *
 * <p>See {@code specs/services/admin-service/architecture.md} — "Admin IdP
 * Boundary" for rationale (self-issuing IdP, kid rotation, JWKS endpoint).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AdminJwtProperties.class)
public class JwtConfig {

    private static final String DEV_PLACEHOLDER_MARKER = "dev-only-placeholder";

    @Bean
    public AdminJwtKeyStore adminJwtKeyStore(AdminJwtProperties properties) {
        warnIfDevPlaceholder(properties);
        return new AdminJwtKeyStore(properties.getSigningKeys(), properties.getActiveSigningKid());
    }

    @Bean
    public JwtSigner operatorJwtSigner(AdminJwtKeyStore keyStore, AdminJwtProperties properties) {
        return new JwtSigner(keyStore, properties.getIssuer());
    }

    @Bean
    @Primary
    public JwtVerifier operatorJwtVerifier(AdminJwtKeyStore keyStore, AdminJwtProperties properties) {
        return new IssuerEnforcingJwtVerifier(
                keyStore.publicKey(keyStore.activeKid())
                        .map(k -> (java.security.interfaces.RSAPublicKey) k)
                        .orElseThrow(() -> new IllegalStateException(
                                "Active kid public key missing: " + keyStore.activeKid())),
                properties.getIssuer());
    }

    private static void warnIfDevPlaceholder(AdminJwtProperties props) {
        props.getSigningKeys().forEach((kid, pem) -> {
            if (pem != null && pem.contains(DEV_PLACEHOLDER_MARKER)) {
                log.warn("admin.jwt.signing-keys[{}] is using a dev-only placeholder — "
                        + "do NOT deploy to production with this configuration", kid);
            }
        });
    }
}
