package com.example.auth.infrastructure.oauth2.persistence;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * JPA-backed {@link OAuth2AuthorizationService} implementation.
 *
 * <p>Delegates all operations to SAS's built-in {@link JdbcOAuth2AuthorizationService},
 * which operates against the canonical {@code oauth2_authorization} table defined in
 * the Flyway migration V0008. The JDBC implementation handles serialization of token
 * values, metadata, and claims — no custom serialization is needed here.
 *
 * <p>This class is <em>not</em> directly exposed as the primary
 * {@link OAuth2AuthorizationService} bean; instead it is wrapped by
 * {@link com.example.auth.infrastructure.oauth2.DomainSyncOAuth2AuthorizationService}
 * which synchronises refresh-token issuance into the domain
 * {@link com.example.auth.domain.repository.RefreshTokenRepository}.
 *
 * <p>TASK-BE-252.
 */
public class JpaOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final JdbcOAuth2AuthorizationService delegate;

    public JpaOAuth2AuthorizationService(JdbcOperations jdbcOperations,
                                          RegisteredClientRepository registeredClientRepository) {
        this.delegate = new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }
}
