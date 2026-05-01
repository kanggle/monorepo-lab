package com.example.auth.infrastructure.oauth2.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA-backed {@link OAuth2AuthorizationConsentService}.
 *
 * <p>Persists per-principal consent grants in the {@code oauth_consent} table.
 * Revocation sets {@code revoked_at} rather than deleting the row, providing
 * an audit trail of consent history.
 *
 * <p>TASK-BE-252.
 */
@Slf4j
@Component
@Transactional
public class JpaOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private final OAuthConsentJpaRepository consentRepository;
    private final RegisteredClientRepository registeredClientRepository;

    public JpaOAuth2AuthorizationConsentService(OAuthConsentJpaRepository consentRepository,
                                                 RegisteredClientRepository registeredClientRepository) {
        this.consentRepository = consentRepository;
        this.registeredClientRepository = registeredClientRepository;
    }

    /**
     * Upserts the consent record.
     *
     * <p>If an active consent already exists for {@code (principalName, registeredClientId)},
     * it is overwritten with the new scope set. If no active consent exists, a new row is
     * inserted.
     */
    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        Assert.notNull(authorizationConsent, "authorizationConsent must not be null");

        String clientId = authorizationConsent.getRegisteredClientId();
        String principalId = authorizationConsent.getPrincipalName();

        String tenantId = resolveTenantId(clientId);

        OAuthConsentEntity entity = consentRepository
                .findActiveByClientIdAndPrincipalId(clientId, principalId)
                .orElseGet(() -> {
                    OAuthConsentEntity e = new OAuthConsentEntity();
                    e.setClientId(clientId);
                    e.setPrincipalId(principalId);
                    e.setGrantedAt(Instant.now());
                    return e;
                });

        entity.setTenantId(tenantId);
        entity.setGrantedScopes(
                authorizationConsent.getAuthorities().stream()
                        .map(a -> a.getAuthority().replace("SCOPE_", ""))
                        .collect(Collectors.toList()));
        entity.setRevokedAt(null); // re-activate if previously revoked

        consentRepository.save(entity);
        log.debug("OAuth2 consent saved: principalId={}, clientId={}", principalId, clientId);
    }

    /**
     * Marks the consent as revoked (soft-delete via {@code revoked_at}).
     */
    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        Assert.notNull(authorizationConsent, "authorizationConsent must not be null");

        String clientId = authorizationConsent.getRegisteredClientId();
        String principalId = authorizationConsent.getPrincipalName();

        consentRepository.findActiveByClientIdAndPrincipalId(clientId, principalId)
                .ifPresent(entity -> {
                    entity.setRevokedAt(Instant.now());
                    consentRepository.save(entity);
                    log.debug("OAuth2 consent revoked: principalId={}, clientId={}", principalId, clientId);
                });
    }

    /**
     * Finds the active consent for the given client and principal.
     *
     * @return the consent, or {@code null} if none exists (SAS contract)
     */
    @Override
    @Transactional(readOnly = true)
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        return consentRepository
                .findActiveByClientIdAndPrincipalId(registeredClientId, principalName)
                .map(entity -> toConsent(entity, registeredClientId, principalName))
                .orElse(null);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private OAuth2AuthorizationConsent toConsent(OAuthConsentEntity entity,
                                                  String registeredClientId,
                                                  String principalName) {
        OAuth2AuthorizationConsent.Builder builder =
                OAuth2AuthorizationConsent.withId(registeredClientId, principalName);

        List<String> scopes = entity.getGrantedScopes();
        if (scopes != null) {
            scopes.forEach(builder::scope);
        }
        return builder.build();
    }

    private String resolveTenantId(String clientId) {
        var client = registeredClientRepository.findByClientId(clientId);
        if (client != null) {
            Object tenantId = client.getClientSettings().getSetting(
                    OAuthClientMapper.SETTING_TENANT_ID);
            if (tenantId instanceof String tid && !tid.isBlank()) {
                return tid;
            }
        }
        return "fan-platform"; // safe default per multi-tenancy policy
    }
}
