package com.example.auth.application.command;

import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.infrastructure.oauth.OAuthUserInfo;

import java.util.Optional;

/**
 * Input to {@link com.example.auth.application.OAuthLoginTransactionalStep}.
 *
 * <p>Carries the result of the external provider HTTP exchange AND of the
 * pre-txn internal HTTP calls to account-service so that the transactional
 * step only performs DB writes. All HTTP (external provider + internal
 * account-service) MUST have completed before constructing this command
 * (TASK-BE-072 follow-up to TASK-BE-069).
 *
 * @param provider        OAuth provider
 * @param userInfo        provider userinfo response
 * @param sessionContext  request session context
 * @param accountId       account id resolved either from a pre-existing
 *                        {@code SocialIdentityJpaEntity} or from
 *                        {@code accountServicePort.socialSignup(...)}
 * @param isNewAccount    whether the resolution above created a new account
 * @param accountStatus   pre-fetched account status (empty if account-service
 *                        returned no status — treated as "unavailable, proceed"
 *                        to preserve existing behaviour)
 */
public record OAuthCallbackTxnCommand(
        OAuthProvider provider,
        OAuthUserInfo userInfo,
        SessionContext sessionContext,
        String accountId,
        boolean isNewAccount,
        Optional<String> accountStatus
) {
}
