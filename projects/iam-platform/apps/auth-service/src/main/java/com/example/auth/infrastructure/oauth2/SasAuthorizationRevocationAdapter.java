package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.OAuthAuthorizationRevocationPort;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.infrastructure.persistence.SocialIdentityJpaEntity;
import com.example.auth.infrastructure.persistence.SocialIdentityJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TASK-BE-601 — {@link OAuthAuthorizationRevocationPort} over the Spring Authorization
 * Server store.
 *
 * <p><b>How a SAS refresh is decided</b> (why this class exists).
 * {@link SasRefreshTokenAuthenticationProvider#authenticate} accepts a refresh token when
 * (1) {@code authorizationService.findByToken(token, REFRESH_TOKEN)} finds the
 * authorization and its refresh token {@code isActive()} — i.e. not invalidated, not
 * expired — and (2) the {@code refresh_tokens} mirror row, IF one exists, is not revoked
 * or expired. When the mirror row is missing the provider proceeds on (1) alone, so the
 * authorization itself is the store that has to be closed; the mirror row is closed too (by
 * jti) so the two agree.
 *
 * <p><b>Still needed after TASK-BE-603.</b> BE-603 keys new mirror rows on the account UUID,
 * so {@code revokeAllByAccountId(accountId)} now reaches them — but it only ever closes the
 * mirror row, never the authorization, and rows written before BE-603 stay keyed by the
 * login email until they expire. The authorization store's {@code principal_name} stays the
 * login email (BE-603 does not change the principal name), so this email → candidates →
 * {@code account_id}-confirmed lookup remains the only way to close the authorization.
 *
 * <p><b>Finding the account's authorizations.</b> {@code oauth2_authorization} has no
 * account column — only {@code principal_name}. The candidate names are the addresses the
 * two login paths use as the principal: the credential email (form login) and each linked
 * social identity's provider email (social login — {@code SocialLoginSteps} keeps it equal
 * to the provider's latest address). Every candidate is then CONFIRMED against the
 * {@code account_id} the login path stored in the principal's details
 * ({@link PrincipalDetailKeys#ACCOUNT_ID}); an authorization whose principal carries a
 * different account id — the same email owning an account in another tenant — is left
 * alone, and one that carries none cannot be attributed and is left alone too (logged).
 *
 * <p><b>What "revoke" means here.</b> The refresh token (and the access token) of the
 * authorization is marked invalidated and the authorization is saved — the same metadata
 * SAS's own revocation endpoint writes — rather than removing the row. The ID token stays
 * on the authorization so an RP-initiated logout with {@code id_token_hint} can still
 * resolve it.
 *
 * <p>Runs inside the caller's transaction ({@code ForceLogoutUseCase} is
 * {@code @Transactional}); the JDBC writes join it.
 */
@Slf4j
@Component
public class SasAuthorizationRevocationAdapter implements OAuthAuthorizationRevocationPort {

    /**
     * Only authorizations that still hold a refresh token that has not expired — an expired
     * one is already refused by SAS, so revoking it would only inflate the count.
     */
    static final String SELECT_CANDIDATES_SQL =
            "SELECT id FROM oauth2_authorization "
                    + "WHERE principal_name = ? "
                    + "AND refresh_token_value IS NOT NULL "
                    + "AND (refresh_token_expires_at IS NULL OR refresh_token_expires_at > ?)";

    private final JdbcOperations jdbcOperations;
    private final OAuth2AuthorizationService authorizationService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CredentialRepository credentialRepository;
    private final SocialIdentityJpaRepository socialIdentityJpaRepository;

    public SasAuthorizationRevocationAdapter(JdbcOperations jdbcOperations,
                                             OAuth2AuthorizationService authorizationService,
                                             RefreshTokenRepository refreshTokenRepository,
                                             CredentialRepository credentialRepository,
                                             SocialIdentityJpaRepository socialIdentityJpaRepository) {
        this.jdbcOperations = jdbcOperations;
        this.authorizationService = authorizationService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.credentialRepository = credentialRepository;
        this.socialIdentityJpaRepository = socialIdentityJpaRepository;
    }

    @Override
    public int revokeActiveRefreshTokens(String accountId) {
        Set<String> principalNames = principalNamesOf(accountId);
        if (principalNames.isEmpty()) {
            log.info("SAS revoke: account={} has no credential or social identity — "
                    + "no principal name to look up", accountId);
            return 0;
        }

        Timestamp now = Timestamp.from(Instant.now());
        int revoked = 0;
        for (String principalName : principalNames) {
            List<String> ids = jdbcOperations.queryForList(
                    SELECT_CANDIDATES_SQL, String.class, principalName, now);
            for (String id : ids) {
                if (revokeIfOwned(id, accountId)) {
                    revoked++;
                }
            }
        }
        return revoked;
    }

    private Set<String> principalNamesOf(String accountId) {
        Set<String> names = new LinkedHashSet<>();
        credentialRepository.findByAccountId(accountId)
                .map(Credential::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .ifPresent(names::add);
        for (SocialIdentityJpaEntity identity : socialIdentityJpaRepository.findByAccountId(accountId)) {
            String email = identity.toDomain().getProviderEmail();
            if (email != null && !email.isBlank()) {
                names.add(email);
            }
        }
        return names;
    }

    private boolean revokeIfOwned(String authorizationId, String accountId) {
        OAuth2Authorization authorization = authorizationService.findById(authorizationId);
        if (authorization == null) {
            return false;
        }
        String ownerAccountId = AuthorizationAccountId.fromPrincipalDetails(authorization);
        if (ownerAccountId == null) {
            log.warn("SAS revoke: authorization={} carries no account_id in its principal — "
                    + "cannot attribute it to account={}, left untouched", authorizationId, accountId);
            return false;
        }
        if (!ownerAccountId.equals(accountId)) {
            // Same principal name, different account (the email exists in another tenant).
            return false;
        }

        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
        if (refreshToken == null || !refreshToken.isActive()) {
            return false;
        }

        OAuth2Authorization.Builder builder = OAuth2Authorization.from(authorization)
                .token(refreshToken.getToken(), SasAuthorizationRevocationAdapter::markInvalidated);
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        if (accessToken != null && !accessToken.isInvalidated()) {
            builder.token(accessToken.getToken(), SasAuthorizationRevocationAdapter::markInvalidated);
        }
        authorizationService.save(builder.build());

        String tokenValue = refreshToken.getToken().getTokenValue();
        refreshTokenRepository.findByJti(tokenValue)
                .filter(row -> !row.isRevoked())
                .ifPresent(row -> {
                    row.revoke();
                    refreshTokenRepository.save(row);
                });
        return true;
    }

    /** The metadata SAS's own revocation writes; {@code Token#isActive()} reads it. */
    private static void markInvalidated(Map<String, Object> metadata) {
        metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true);
    }
}
