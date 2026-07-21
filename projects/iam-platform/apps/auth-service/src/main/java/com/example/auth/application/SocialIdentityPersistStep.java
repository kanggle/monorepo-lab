package com.example.auth.application;

import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.oauth.OAuthUserInfo;
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
 * steps shared with the legacy custom-JWT flow, both delegated to the shared
 * {@link SocialLoginSteps} bean:
 * <ol>
 *   <li><b>social_identity upsert</b> — {@link SocialLoginSteps#upsertIdentity};</li>
 *   <li><b>account-status check</b> — reject LOCKED / DORMANT / DELETED via
 *       {@link SocialLoginSteps#checkAccountStatus}.</li>
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

    private final SocialLoginSteps socialLoginSteps;

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
        // Upsert local social identity + account-status gate — the shared steps,
        // identical to the legacy OAuthLoginTransactionalStep path.
        socialLoginSteps.upsertIdentity(provider, userInfo, accountId, tenantId);
        accountStatus.ifPresent(socialLoginSteps::checkAccountStatus);
    }
}
