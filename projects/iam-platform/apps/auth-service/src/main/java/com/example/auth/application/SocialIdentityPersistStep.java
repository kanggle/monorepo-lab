package com.example.auth.application;

import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.oauth.OAuthUserInfo;
import com.example.auth.domain.repository.SocialIdentityRepository;
import com.example.auth.domain.social.SocialIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Transactional boundary for the <b>SAS browser-session</b> social-login flow
 * (TASK-BE-396, ADR-006 option B).
 *
 * <p>This is the session-establishing counterpart to
 * {@link OAuthLoginTransactionalStep}. It performs the SAME two DB-touching
 * side effects shared with the legacy custom-JWT flow:
 * <ol>
 *   <li><b>social_identity upsert</b> — create on the new-identity path,
 *       {@code updateLastUsedAt} + {@code updateProviderEmail} on the existing
 *       path (mirrors {@code OAuthLoginTransactionalStep.persistLogin} lines
 *       ~61-78, byte-for-byte semantics).</li>
 *   <li><b>account-status check</b> — reject LOCKED / DORMANT / DELETED
 *       (mirrors {@code OAuthLoginTransactionalStep.checkAccountStatus},
 *       lines ~130-138).</li>
 * </ol>
 *
 * <p>It deliberately does <b>NOT</b>:
 * <ul>
 *   <li>issue a custom JWT (no {@code TokenGeneratorPort});</li>
 *   <li>register/update a device session;</li>
 *   <li>persist a refresh token;</li>
 *   <li>publish {@code auth.login.succeeded} / {@code auth.session.created}
 *       events.</li>
 * </ul>
 * Those are the JWT-flow tail (ADR-006 spec steps g~i) replaced here by SAS
 * session establishment in the presentation layer. The standard SAS tokens are
 * issued later by {@code /oauth2/token} after the saved {@code /oauth2/authorize}
 * request resumes, with the {@code roles} claim auto-seeded by the existing
 * {@code RoleSeedPolicy} / {@code TenantClaimTokenCustomizer} keyed on the
 * initiating client's platform.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialIdentityPersistStep {

    private final SocialIdentityRepository socialIdentityRepository;

    /**
     * Upserts the social identity for the authenticated provider user and
     * enforces the account-status gate, all inside one transaction.
     *
     * @param provider      the OAuth provider
     * @param userInfo      the provider userinfo response
     * @param accountId     the resolved (born-unified) account id
     * @param tenantId      the tenant the new identity row is attributed to
     *                      (derived from the initiating OIDC client; falls back
     *                      to the default tenant by the caller)
     * @param accountStatus pre-fetched account status (empty → status guard
     *                      skipped, mirroring the legacy flow's "unavailable,
     *                      proceed" semantics)
     */
    @Transactional
    public void persistIdentityAndCheckStatus(OAuthProvider provider,
                                              OAuthUserInfo userInfo,
                                              String accountId,
                                              String tenantId,
                                              Optional<String> accountStatus) {
        // Upsert local social identity — identical semantics to
        // OAuthLoginTransactionalStep.persistLogin (lines ~61-78).
        Optional<SocialIdentity> existingIdentity =
                socialIdentityRepository.findByProviderAndProviderUserId(
                        provider.name(), userInfo.providerUserId());

        if (existingIdentity.isPresent()) {
            var identity = existingIdentity.get();
            identity.updateLastUsedAt();
            if (userInfo.email() != null && !userInfo.email().equals(identity.getProviderEmail())) {
                identity.updateProviderEmail(userInfo.email());
            }
            socialIdentityRepository.save(identity);
        } else {
            var newIdentity = SocialIdentity.create(
                    accountId, tenantId,
                    provider.name(), userInfo.providerUserId(), userInfo.email());
            socialIdentityRepository.save(newIdentity);
        }

        // Account-status check against the pre-fetched value (no HTTP here) —
        // identical semantics to OAuthLoginTransactionalStep.checkAccountStatus
        // (lines ~130-138).
        accountStatus.ifPresent(this::checkAccountStatus);
    }

    private void checkAccountStatus(String status) {
        switch (status) {
            case "ACTIVE" -> { /* proceed */ }
            case "LOCKED" -> throw new AccountLockedException();
            case "DORMANT" -> throw new AccountStatusException("DORMANT", "ACCOUNT_DORMANT");
            case "DELETED" -> throw new AccountStatusException("DELETED", "ACCOUNT_DELETED");
            default -> throw new AccountStatusException(status, "ACCOUNT_STATUS_UNKNOWN");
        }
    }
}
