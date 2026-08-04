package com.example.auth.application;

import com.example.auth.application.command.OAuthCallbackCommand;
import com.example.auth.application.exception.*;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.OAuthClient;
import com.example.auth.application.port.OAuthClientProvider;
import com.example.auth.application.port.OAuthProviderConfig;
import com.example.auth.application.port.OAuthProviderConfigPort;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.application.result.BrowserLoginResolution;
import com.example.auth.application.result.OAuthAuthorizeResult;
import com.example.auth.application.result.SocialSignupResult;
import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.oauth.OAuthUserInfo;
import com.example.auth.domain.repository.OAuthStateStore;
import com.example.auth.domain.repository.SocialIdentityRepository;
import com.example.auth.domain.social.SocialIdentity;
import com.example.common.id.UuidV7;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthLoginUseCase {

    private final OAuthProviderConfigPort oAuthProviderConfigPort;
    private final OAuthClientProvider oAuthClientProvider;
    private final OAuthStateStore oAuthStateStore;
    private final AccountServicePort accountServicePort;
    private final SocialIdentityRepository socialIdentityRepository;
    // TASK-BE-396 (ADR-006 option B): the session-establishing transactional tail
    // for the SAS browser flow (social_identity upsert + status check only — no JWT).
    private final SocialIdentityPersistStep socialIdentityPersistStep;

    /**
     * Generates an authorization URL for the given OAuth provider.
     * Stores a random state in Redis with a 10-minute TTL for CSRF protection.
     */
    public OAuthAuthorizeResult authorize(String providerStr, String redirectUri) {
        OAuthProvider provider = parseProvider(providerStr);
        OAuthProviderConfig config = oAuthProviderConfigPort.get(provider);

        String effectiveRedirectUri = (redirectUri != null && !redirectUri.isBlank())
                ? redirectUri : config.defaultRedirectUri();

        validateRedirectUri(config, effectiveRedirectUri);

        String state = UuidV7.randomString();

        // Persist via the domain port — key prefix + TTL live in the adapter.
        oAuthStateStore.store(state, provider);

        String authorizationUrl = buildAuthorizationUrl(config, effectiveRedirectUri, state);

        return new OAuthAuthorizeResult(authorizationUrl, state);
    }

    /**
     * SAS browser-flow account resolution (TASK-BE-396, ADR-006 option B).
     *
     * <p>Since TASK-BE-398 retired the legacy custom-JWT JSON callback, this is the
     * ONLY social-login callback path. It runs the shared pre-resolution
     * ({@link #resolveSocialLogin}: state {@code consumeAtomic} →
     * {@code exchangeCodeForUserInfo} → email validate → identity lookup →
     * socialSignup-or-existing → {@code getAccountStatus}), then runs ONLY the
     * social-identity upsert + account-status check via
     * {@link SocialIdentityPersistStep} — it does NOT issue a custom JWT, register a
     * device session, persist a refresh token, or publish login events.
     *
     * <p>The caller (presentation layer) takes the returned {@code accountId}/{@code email}
     * and establishes a SAS-consumed authenticated HTTP session, then resumes the saved
     * {@code /oauth2/authorize} request → standard SAS tokens.
     *
     * @param command the browser callback command (carries provider, code, state,
     *                browser callback URI, and request session context)
     * @param tenantId the tenant the new social-identity row is attributed to,
     *                derived from the initiating OIDC client by the caller
     * @return the resolved account id, email, and new-account flag (no tokens)
     */
    public BrowserLoginResolution resolveBrowserLogin(OAuthCallbackCommand command, String tenantId) {
        // TASK-BE-507: the same client-derived tenant that attributes the social-identity row
        // (below) and the token now also reaches socialSignup — so the ACCOUNT row agrees with
        // them. Before BE-507 it stopped here and the account was born fan-platform.
        ResolvedSocialLogin resolved = resolveSocialLogin(command, tenantId);

        // Session-establishing transactional tail — social_identity upsert + status
        // check ONLY. No JWT / device session / refresh token / login events.
        socialIdentityPersistStep.persistIdentityAndCheckStatus(
                resolved.provider(), resolved.userInfo(),
                resolved.accountId(), tenantId, resolved.accountStatus());

        return new BrowserLoginResolution(
                resolved.accountId(), resolved.userInfo().email(), resolved.isNewAccount());
    }

    /**
     * Shared account pre-resolution for the social-login callback.
     *
     * <p>Design rationale, carried over from the (now removed) legacy custom-JWT
     * callback this body was hoisted out of:
     *
     * <ul>
     *   <li>TASK-BE-069 moved the external provider HTTP out of {@code @Transactional};
     *       TASK-BE-072 additionally moved the account-service internal HTTP calls out.
     *       Both previously held a Hikari connection open across network I/O. This
     *       method is intentionally NOT {@code @Transactional} — the transactional tail
     *       ({@link SocialIdentityPersistStep}) receives already-fetched data.</li>
     *   <li>Compensation: if the DB transaction fails after the provider +
     *       account-service HTTP succeeded, the user sees a login failure while the
     *       provider may have recorded an authorization and account-service may have
     *       created an account. {@code socialSignup} is idempotent for the same
     *       (email, provider), so retries are safe. No provider-side revoke is
     *       performed.</li>
     *   <li>TOCTOU: the identity existence check is a non-txn DB read. The transactional
     *       step still upserts the identity, and the DB unique key on
     *       {@code (provider, provider_user_id)} prevents duplicate rows.</li>
     *   <li>TASK-BE-063 empty-status semantics: an empty {@code accountStatus} means the
     *       account lookup was unavailable — the status guard is skipped and the rest of
     *       the flow proceeds.</li>
     * </ul>
     */
    private ResolvedSocialLogin resolveSocialLogin(OAuthCallbackCommand command, String tenantId) {
        OAuthProvider provider = parseProvider(command.provider());

        // Verify state via the domain port (GETDEL for atomic check-and-delete).
        // Done outside txn — state check is an auth prerequisite, not a DB write.
        // Note: state is consumed (single-use) BEFORE redirect_uri validation. A
        // brute-forced state with a wrong redirect_uri will burn that state slot.
        // Acceptable because state is 128-bit UUIDv7 and not enumerable; deferring
        // state consumption past validation would re-enable replay of expired
        // attempts that fail validation.
        //
        // TASK-BE-521 (item B) — enforce the state↔provider binding the store
        // returns. consumeAtomic returns the OAuthProvider the state was minted for
        // (OAuthStateStore#consumeAtomic); a state issued on provider A's authorize
        // must not be consumable on provider B's callback. The store already tracks
        // the binding — the previous .isEmpty()-only check discarded it.
        Optional<OAuthProvider> boundProvider = oAuthStateStore.consumeAtomic(command.state());
        if (boundProvider.isEmpty() || boundProvider.get() != provider) {
            throw new InvalidOAuthStateException();
        }

        OAuthProviderConfig config = oAuthProviderConfigPort.get(provider);
        String effectiveRedirectUri = (command.redirectUri() != null && !command.redirectUri().isBlank())
                ? command.redirectUri() : config.defaultRedirectUri();
        validateRedirectUri(config, effectiveRedirectUri);

        // External HTTP: token exchange + userinfo. OUTSIDE @Transactional (TASK-BE-069).
        OAuthClient client = oAuthClientProvider.getClient(provider);
        OAuthUserInfo userInfo;
        try {
            userInfo = client.exchangeCodeForUserInfo(command.code(), effectiveRedirectUri);
        } catch (OAuthCodeInvalidException e) {
            // TASK-MONO-350: a rejected authorization code is a user/client fault (stale or
            // replayed callback), not an incident. WARN, not ERROR — otherwise log-based
            // alerting pages an operator for someone re-opening an old callback URL. Must be
            // caught before OAuthProviderException: it is a subclass.
            log.warn("OAuth authorization code rejected by {}: {}", provider, e.getMessage());
            throw e;
        } catch (OAuthProviderException e) {
            log.error("OAuth provider error for {}: {}", provider, e.getMessage());
            throw e;
        }

        // Validate email
        if (userInfo.email() == null || userInfo.email().isBlank()) {
            throw new OAuthEmailRequiredException();
        }

        // Non-txn DB read: does a local social identity already exist for this provider user?
        Optional<SocialIdentity> existingIdentity =
                socialIdentityRepository.findByProviderAndProviderUserId(
                        provider.name(), userInfo.providerUserId());

        // Internal HTTP to account-service. OUTSIDE @Transactional (TASK-BE-072).
        String accountId;
        boolean isNewAccount;
        if (existingIdentity.isPresent()) {
            accountId = existingIdentity.get().getAccountId();
            isNewAccount = false;
        } else {
            SocialSignupResult signupResult = accountServicePort.socialSignup(
                    userInfo.email(), provider.name(), userInfo.providerUserId(), userInfo.name(),
                    tenantId);
            accountId = signupResult.accountId();
            isNewAccount = signupResult.newAccount();
        }

        // Pre-fetched account status (TASK-BE-063 semantics: empty → account unavailable,
        // status guard is skipped and the rest of the flow proceeds — social signup path
        // would have created the account).
        Optional<String> accountStatus = accountServicePort.getAccountStatus(accountId)
                .map(AccountStatusLookupResult::accountStatus);

        return new ResolvedSocialLogin(provider, userInfo, accountId, isNewAccount, accountStatus);
    }

    /** Internal holder for the shared pre-resolution result. */
    private record ResolvedSocialLogin(
            OAuthProvider provider,
            OAuthUserInfo userInfo,
            String accountId,
            boolean isNewAccount,
            Optional<String> accountStatus
    ) {
    }

    private OAuthProvider parseProvider(String providerStr) {
        try {
            return OAuthProvider.from(providerStr);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedProviderException(providerStr);
        }
    }

    private void validateRedirectUri(OAuthProviderConfig config, String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new InvalidOAuthRedirectUriException();
        }
        if (!config.allowedRedirectUris().contains(redirectUri)) {
            throw new InvalidOAuthRedirectUriException();
        }
    }

    private String buildAuthorizationUrl(OAuthProviderConfig config,
                                          String redirectUri, String state) {
        return config.authUri()
                + "?client_id=" + encode(config.clientId())
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(config.scopes().replace(",", " "))
                + "&state=" + encode(state);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
