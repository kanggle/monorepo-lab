package com.example.auth.application;

import com.example.auth.application.command.OAuthCallbackCommand;
import com.example.auth.application.exception.*;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.OAuthClient;
import com.example.auth.application.port.OAuthClientProvider;
import com.example.auth.application.port.OAuthProviderConfig;
import com.example.auth.application.port.OAuthProviderConfigPort;
import com.example.auth.application.result.AccountStatusWithTenantLookupResult;
import com.example.auth.application.result.BrowserLoginResolution;
import com.example.auth.application.result.OAuthAuthorizeResult;
import com.example.auth.application.result.SocialSignupResult;
import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.oauth.OAuthUserInfo;
import com.example.auth.domain.repository.OAuthStateStore;
import com.example.auth.domain.repository.SocialIdentityRepository;
import com.example.auth.domain.session.SessionContext;
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
    // TASK-BE-602: auth.login.* for the social path — the same recorder the form path uses.
    private final LoginEventRecorder loginEventRecorder;

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
     * socialSignup-or-existing → {@code getAccountStatusAndTenant}), then runs ONLY the
     * social-identity upsert + account-status check via
     * {@link SocialIdentityPersistStep} — it does NOT issue a custom JWT, register a
     * device session, or persist a refresh token.
     *
     * <p>TASK-BE-602: it DOES record {@code auth.login.attempted} and then
     * {@code succeeded} / {@code failed} through {@link LoginEventRecorder}, keyed on the
     * account's own tenant as account-service reported it — and only when account-service
     * answered (a 404 leaves no tenant to report, so no events). Recording is telemetry: its
     * failure never changes the login outcome.
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

        // TASK-BE-602: login events carry the account's OWN tenant, which only the status lookup
        // knows. Without an answer (404) there is no tenant to put on them, so none are emitted —
        // a guessed tenant (the client's) is exactly what is wrong for pre-BE-507 accounts.
        Optional<LoginTelemetry> telemetry = resolved.account()
                .map(account -> new LoginTelemetry(
                        resolved.accountId(), account.tenantId(),
                        LoginHashes.emailHash(resolved.userInfo().email()),
                        command.sessionContext(),
                        resolved.provider().loginMethod()));
        telemetry.ifPresent(t -> recordTelemetry("attempted", () ->
                loginEventRecorder.recordAttempted(t.accountId(), t.emailHash(), t.tenantId(), t.ctx())));

        // Session-establishing transactional tail — social_identity upsert + status
        // check ONLY. No JWT / device session / refresh token.
        try {
            socialIdentityPersistStep.persistIdentityAndCheckStatus(
                    resolved.provider(), resolved.userInfo(),
                    resolved.accountId(), tenantId,
                    resolved.account().map(AccountStatusWithTenantLookupResult::accountStatus));
        } catch (AccountLockedException | AccountStatusException rejection) {
            String reason = AccountStatusRule.eventFailureReason(rejection);
            // A status outside the contract enum is rejected all the same, but has no
            // failureReason to carry — attempted stands alone (the form path's rule).
            if (reason != null) {
                telemetry.ifPresent(t -> recordTelemetry("failed", () ->
                        loginEventRecorder.recordFailed(t.accountId(), t.emailHash(), t.tenantId(),
                                reason, t.ctx())));
            }
            throw rejection;
        }

        telemetry.ifPresent(t -> recordTelemetry("succeeded", () ->
                loginEventRecorder.recordSucceeded(t.accountId(), t.tenantId(), t.ctx(), t.loginMethod())));

        return new BrowserLoginResolution(
                resolved.accountId(), resolved.userInfo().email(), resolved.isNewAccount());
    }

    /**
     * Runs a login-telemetry side effect (TASK-BE-602). Any failure (e.g. the outbox write) is
     * logged and swallowed: telemetry must never change the login outcome — the same rule the form
     * path follows (TASK-BE-599). The catch sits here, outside {@link LoginEventRecorder}'s
     * transactional proxy, so a failed write cannot leave a rollback-only transaction behind.
     */
    private static void recordTelemetry(String what, Runnable sideEffect) {
        try {
            sideEffect.run();
        } catch (RuntimeException e) {
            log.warn("social-login telemetry '{}' failed — login outcome unaffected", what, e);
        }
    }

    /** What the social-login events need, known only once account-service has answered. */
    private record LoginTelemetry(
            String accountId,
            String tenantId,
            String emailHash,
            SessionContext ctx,
            String loginMethod
    ) {
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
     *   <li>Status lookup outcome (TASK-BE-600, replacing the BE-063 semantics; since TASK-BE-602
     *       the lookup is {@code getAccountStatusAndTenant}, which also returns the account's
     *       own tenant): an empty {@code account} now means ONLY that account-service answered
     *       404 — the
     *       status guard is skipped for that case, exactly as before. Under BE-063 "empty"
     *       also covered a non-404 4xx and an unreadable 200, i.e. failed lookups, and those
     *       silently skipped the guard (fail-open). They now throw
     *       {@link AccountServiceUnavailableException} out of this method, so a failed lookup
     *       rejects the social login — fail-closed, the owner decision shared with the
     *       password form (TASK-BE-600 AC-2). A 5xx / timeout / open circuit already threw
     *       before BE-600; that is unchanged. New-account and first-login flows are
     *       unaffected: {@code socialSignup} has already created the account, so its status
     *       lookup answers 200 (or 404, which still proceeds).</li>
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

        // Pre-fetched account status AND the account's own tenant (TASK-BE-602 — replaces the
        // header-less, fan-platform-pinned getAccountStatus call; still one round trip).
        // Empty = 404 only → the status guard is skipped (TASK-BE-600 — never turn it into a
        // rejection). A failed lookup throws AccountServiceUnavailableException → the login fails
        // closed (TASK-BE-600 AC-2; see the method javadoc for what changed from BE-063).
        // Why not send a tenant instead: every candidate we hold is wrong for some accounts — the
        // identity row and the initiating client say ecommerce for a pre-BE-507 account that lives
        // in fan-platform, and the pinned lookup cannot see a post-BE-507 store account at all
        // (a LOCKED store account used to get in). account-service reads the row by its id and
        // reports the row's tenant (owner decision, TASK-BE-602 AC-0).
        Optional<AccountStatusWithTenantLookupResult> account =
                accountServicePort.getAccountStatusAndTenant(accountId);

        return new ResolvedSocialLogin(provider, userInfo, accountId, isNewAccount, account);
    }

    /** Internal holder for the shared pre-resolution result. */
    private record ResolvedSocialLogin(
            OAuthProvider provider,
            OAuthUserInfo userInfo,
            String accountId,
            boolean isNewAccount,
            Optional<AccountStatusWithTenantLookupResult> account
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
