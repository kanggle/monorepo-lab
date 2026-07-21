package com.example.auth.application;

import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.oauth.OAuthUserInfo;
import com.example.auth.domain.repository.SocialIdentityRepository;
import com.example.auth.domain.social.SocialIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The two DB-touching steps shared by both social-login transactional boundaries
 * — {@link OAuthLoginTransactionalStep} (legacy custom-JWT flow) and
 * {@link SocialIdentityPersistStep} (SAS browser-session flow).
 *
 * <p>Before this bean the social-identity upsert and the account-status gate were
 * maintained as byte-for-byte copies in both step classes (documented as
 * "mirrors ... byte-for-byte semantics" in comments). Extracting them here makes
 * the shared-semantics invariant compiler-enforced rather than comment-enforced.
 *
 * <p>These methods carry <b>no</b> {@code @Transactional} annotation: each caller
 * already owns the transaction, so the writes here participate in the caller's
 * boundary exactly as the inlined code did.
 */
@Component
@RequiredArgsConstructor
class SocialLoginSteps {

    private final SocialIdentityRepository socialIdentityRepository;

    /**
     * Upserts the local social identity for the authenticated provider user:
     * {@code updateLastUsedAt} (+ {@code updateProviderEmail} when the provider
     * email changed) on the existing-identity path, {@code create} on the new one.
     *
     * @param tenantId tenant the new identity row is attributed to — the default
     *                 tenant for the legacy flow, the initiating-client tenant for
     *                 the SAS flow (resolved by each caller).
     */
    void upsertIdentity(OAuthProvider provider, OAuthUserInfo userInfo,
                        String accountId, String tenantId) {
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
    }

    /**
     * Rejects a non-ACTIVE account status. ACTIVE proceeds; LOCKED / DORMANT /
     * DELETED and any unknown value map to the corresponding account exceptions.
     */
    void checkAccountStatus(String status) {
        switch (status) {
            case "ACTIVE" -> { /* proceed */ }
            case "LOCKED" -> throw new AccountLockedException();
            case "DORMANT" -> throw new AccountStatusException("DORMANT", "ACCOUNT_DORMANT");
            case "DELETED" -> throw new AccountStatusException("DELETED", "ACCOUNT_DELETED");
            default -> throw new AccountStatusException(status, "ACCOUNT_STATUS_UNKNOWN");
        }
    }
}
