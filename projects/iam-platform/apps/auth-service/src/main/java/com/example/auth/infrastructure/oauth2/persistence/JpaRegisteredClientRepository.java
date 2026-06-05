package com.example.auth.infrastructure.oauth2.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * JPA-backed {@link RegisteredClientRepository} implementation.
 *
 * <p>Replaces the in-memory placeholder from TASK-BE-251. Clients are loaded from
 * the {@code oauth_clients} table via {@link OAuthClientJpaRepository}.
 *
 * <h3>Tenant carrier (Option B)</h3>
 * <p>The {@link OAuthClientMapper} injects {@code custom.tenant_id} and
 * {@code custom.tenant_type} into the returned {@link RegisteredClient}'s
 * {@code ClientSettings} so that {@link com.example.auth.infrastructure.oauth2.TenantClaimTokenCustomizer}
 * and {@link com.example.auth.infrastructure.oauth2.SasRefreshTokenAuthenticationProvider}
 * can read tenant context without parsing {@code clientName}.
 *
 * <h3>Secret storage</h3>
 * <p>On {@link #save}, if the client has a plain-text secret (prefixed {@code {noop}}),
 * it is BCrypt-hashed before persisting. If already hashed ({@code {bcrypt}}),
 * the hash is stored verbatim. Public PKCE clients (no secret) store {@code NULL}.
 *
 * <p>TASK-BE-252.
 */
@Slf4j
@Component
@Transactional(readOnly = true)
public class JpaRegisteredClientRepository implements RegisteredClientRepository {

    private final OAuthClientJpaRepository jpaRepository;
    private final OAuthClientMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public JpaRegisteredClientRepository(OAuthClientJpaRepository jpaRepository,
                                          PasswordEncoder passwordEncoder) {
        this.jpaRepository = jpaRepository;
        this.mapper = new OAuthClientMapper();
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Persists or updates a {@link RegisteredClient}.
     *
     * <p>If the client carries a plain-text secret ({@code {noop}} prefix), it is
     * BCrypt-encoded before storage. Secrets already in BCrypt format are stored as-is.
     */
    @Override
    @Transactional
    public void save(RegisteredClient registeredClient) {
        Assert.notNull(registeredClient, "registeredClient must not be null");

        OAuthClientEntity entity = mapper.toEntity(registeredClient);

        // If the secret is plain-text (noop), hash it before persisting
        String rawSecret = registeredClient.getClientSecret();
        if (rawSecret != null && rawSecret.startsWith("{noop}")) {
            String plain = rawSecret.substring("{noop}".length());
            entity.setClientSecretHash(passwordEncoder.encode(plain));
        }

        jpaRepository.save(entity);
        log.debug("OAuth2 client saved: clientId={}", registeredClient.getClientId());
    }

    /**
     * Looks up a client by its entity primary key (SAS {@code RegisteredClient.getId()}).
     *
     * @return the mapped {@link RegisteredClient}, or {@code null} if not found
     *         (SAS contract — callers handle null by throwing
     *         {@link org.springframework.security.oauth2.core.OAuth2AuthenticationException})
     */
    @Override
    public RegisteredClient findById(String id) {
        return jpaRepository.findById(id)
                .map(mapper::toRegisteredClient)
                .orElse(null);
    }

    /**
     * Looks up a client by its OAuth 2.0 {@code client_id}.
     *
     * @return the mapped {@link RegisteredClient}, or {@code null} if not found
     */
    @Override
    public RegisteredClient findByClientId(String clientId) {
        return jpaRepository.findByClientId(clientId)
                .map(mapper::toRegisteredClient)
                .orElse(null);
    }
}
