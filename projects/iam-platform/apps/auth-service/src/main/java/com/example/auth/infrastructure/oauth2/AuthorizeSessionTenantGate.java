package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.result.ConsumerSiteMembershipLookupResult;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import com.example.auth.infrastructure.security.PendingSiteConsentStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * TASK-BE-605 (owner decision ① (b), 2026-09-26) — an authenticated IAM browser session is
 * reused for a {@code /oauth2/authorize} request only when the code it would yield carries the
 * requesting client's tenant. Otherwise this request is treated as unauthenticated, and the
 * chain sends the browser to {@code /login} for that client.
 *
 * <p><b>Why.</b> TASK-BE-604 (decision D) limited the form-login cross-tenant credential fallback
 * to the console client, but that rule runs only when a password is submitted. A session opened
 * through one client passed straight through another client's authorize, and the token carried
 * the session's tenant: logged into the store as {@code ecommerce}, then opening a fan client,
 * the user got {@code tenant_id=ecommerce} with no roles (roles are looked up in the session
 * tenant; the seed fires only when that tenant is the client's platform) and never used the fan
 * credential they hold (BE-604 § ⑧, measured locally).
 *
 * <p><b>The rule</b> ({@code multi-tenancy.md} § 로그인 가능한 계정과 client):
 * <ul>
 *   <li>no authenticated principal, no {@code client_id}, unknown client, or a client without a
 *       tenant setting → untouched (SAS answers these as it always did);</li>
 *   <li>the client's tenant is {@link TenantContext#CONSOLE_TENANT_ID} → untouched, UNLESS the
 *       session is of another tenant and its email holds a credential in the console tenant
 *       (TASK-BE-610) — then re-authentication. ADR-MONO-044 D5 operators, who have no
 *       {@code iam} credential and reach the console only with a consumer-tenant session, are
 *       untouched as before;</li>
 *   <li>TASK-BE-615 — a consumer-pool principal on a non-console client → untouched when the
 *       client's tenant is a consumer site the account is a member of (or was removed from by the site's
 *       operator — issuance refuses), re-authentication when it is not a consumer site; TASK-BE-616 — and the
 *       first-visit consent page when it is a consumer site the account has never joined, TASK-BE-619 — or
 *       left itself ({@link #consumerSiteDecision});</li>
 *   <li>session tenant ({@link AuthorizationSessionTenant} — the rule the token claim uses)
 *       equals the client's tenant → untouched;</li>
 *   <li>anything else, including the platform scope {@code '*'} → re-authentication.</li>
 * </ul>
 *
 * <p><b>What "re-authentication" does, precisely.</b> Only this request's
 * {@code SecurityContextHolder} is emptied. SAS's authorization endpoint then sees no
 * authenticated principal and passes the request down the chain, the {@code authenticated()}
 * rule rejects it, and {@code ExceptionTranslationFilter} saves THIS authorize request and
 * invokes the entry point — {@code /login} (or {@code /signup} for the registration hint).
 * Nothing is written back to the HTTP session, so:
 * <ul>
 *   <li>the saved request is the new client's authorize: the form login's scoped credential
 *       lookup (BE-604) is keyed on it and picks the credential in the client's tenant, and the
 *       social path stamps the client's tenant — either way the resumed authorize passes this
 *       gate, so it cannot loop;</li>
 *   <li>if the user abandons the login page, the session they had is intact: the client they
 *       came from still gets single sign-on;</li>
 *   <li>once they log in, the session holds the new principal (login replaces the security
 *       context and rotates the session id). Tokens already issued to the other client are not
 *       touched — only its NEXT authorize asks for that client's login again.</li>
 * </ul>
 * Invalidating the HTTP session instead would have discarded the saved request the resumed
 * flow depends on, and logged the user out of the client they came from for no gain.
 *
 * <p>{@code prompt=none} composes as OIDC requires: with the principal emptied, SAS answers
 * {@code login_required} to the client (SAS 1.4.1 {@code OAuth2AuthorizationCodeRequestAuthenticationProvider}).
 * SAS 1.4.1 does not implement {@code prompt=login} (it validates the value only), so this gate
 * cannot be expressed through a prompt parameter.
 */
@Slf4j
final class AuthorizeSessionTenantGate extends OncePerRequestFilter {

    private final RequestMatcher authorizationEndpoint;
    private final RegisteredClientRepository registeredClientRepository;
    private final CredentialRepository credentialRepository;
    /** TASK-BE-615 — asked only for a consumer-pool principal: is the client's tenant a consumer site. */
    private final AccountServicePort accountServicePort;
    /** TASK-BE-616 — where a pool principal's authorize waits for the site's first-visit consent. */
    private final PendingSiteConsentStore pendingSiteConsentStore;
    private final SecurityContextHolderStrategy securityContextHolderStrategy;

    /** TASK-BE-616 — the first-visit consent page ({@code SiteConsentPageController}). */
    static final String CONSENT_PATH = "/consent";

    /** What the gate does with one authorize request. */
    enum Decision {
        /** Reuse the session — SAS answers as it always does. */
        PASS,
        /** Treat this request as unauthenticated → the client's login page. */
        REAUTHENTICATE,
        /** TASK-BE-616 — a pool principal on a consumer site it has never joined → the consent page. */
        CONSENT
    }

    AuthorizeSessionTenantGate(String authorizationEndpointUri,
                               RegisteredClientRepository registeredClientRepository,
                               CredentialRepository credentialRepository,
                               AccountServicePort accountServicePort) {
        this(authorizationEndpointUri, registeredClientRepository, credentialRepository, accountServicePort,
                new PendingSiteConsentStore(registeredClientRepository));
    }

    AuthorizeSessionTenantGate(String authorizationEndpointUri,
                               RegisteredClientRepository registeredClientRepository,
                               CredentialRepository credentialRepository,
                               AccountServicePort accountServicePort,
                               PendingSiteConsentStore pendingSiteConsentStore) {
        this(authorizationEndpointUri, registeredClientRepository, credentialRepository, accountServicePort,
                pendingSiteConsentStore, SecurityContextHolder.getContextHolderStrategy());
    }

    AuthorizeSessionTenantGate(String authorizationEndpointUri,
                               RegisteredClientRepository registeredClientRepository,
                               CredentialRepository credentialRepository,
                               AccountServicePort accountServicePort,
                               PendingSiteConsentStore pendingSiteConsentStore,
                               SecurityContextHolderStrategy securityContextHolderStrategy) {
        this.authorizationEndpoint = new AntPathRequestMatcher(
                Objects.requireNonNull(authorizationEndpointUri, "authorizationEndpointUri"));
        this.registeredClientRepository = Objects.requireNonNull(
                registeredClientRepository, "registeredClientRepository");
        this.credentialRepository = Objects.requireNonNull(
                credentialRepository, "credentialRepository");
        this.accountServicePort = Objects.requireNonNull(accountServicePort, "accountServicePort");
        this.pendingSiteConsentStore = Objects.requireNonNull(pendingSiteConsentStore, "pendingSiteConsentStore");
        this.securityContextHolderStrategy = Objects.requireNonNull(
                securityContextHolderStrategy, "securityContextHolderStrategy");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !authorizationEndpoint.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication principal = securityContextHolderStrategy.getContext().getAuthentication();
        String clientId = request.getParameter("client_id");
        Decision decision = decide(principal, clientId);
        if (decision == Decision.REAUTHENTICATE) {
            // Replace, never mutate: the context in the holder IS the object the HTTP session
            // stores, so setAuthentication(null) on it would log the user out of the client they
            // came from as well (bite-checked, TASK-BE-605).
            securityContextHolderStrategy.setContext(securityContextHolderStrategy.createEmptyContext());
        } else if (decision == Decision.CONSENT && askForConsent(request, response, clientId)) {
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * @return {@code true} when the session must not be reused for this client's authorize
     */
    boolean requiresReauthentication(Authentication principal, String clientId) {
        return decide(principal, clientId) == Decision.REAUTHENTICATE;
    }

    Decision decide(Authentication principal, String clientId) {
        if (principal == null || principal instanceof AnonymousAuthenticationToken
                || !principal.isAuthenticated()) {
            return Decision.PASS;
        }
        if (clientId == null || clientId.isBlank()) {
            return Decision.PASS;
        }
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client == null) {
            return Decision.PASS;
        }
        String clientTenant = clientTenant(client);
        if (clientTenant == null) {
            return Decision.PASS;
        }
        if (AuthorizationSessionTenant.isPoolPrincipal(principal)
                && AuthorizationSessionTenant.mapsPoolPrincipalTo(clientTenant)) {
            return consumerSiteDecision(principal, clientTenant, clientId);
        }
        String sessionTenant = AuthorizationSessionTenant.of(principal, clientTenant);
        if (clientTenant.equals(sessionTenant)) {
            return Decision.PASS;
        }
        if (TenantContext.CONSOLE_TENANT_ID.equals(clientTenant)) {
            return holdsConsoleCredential(principal, sessionTenant, clientId)
                    ? Decision.REAUTHENTICATE : Decision.PASS;
        }
        log.info("authorize: session tenant {} does not match client {} tenant {} — "
                + "re-authentication required (TASK-BE-605)", sessionTenant, clientId, clientTenant);
        return Decision.REAUTHENTICATE;
    }

    /**
     * TASK-BE-616 — sends a pool principal that has never joined this consumer site to the one-screen
     * consent, with this authorize request parked so «accept» can resume it exactly.
     *
     * <ul>
     *   <li>{@code GET} (the browser's authorize) → park + {@code 302 /consent}.</li>
     *   <li>{@code prompt=none} → no screen may be shown (OIDC Core § 3.1.2.1): the client gets
     *       {@code consent_required} on its registered redirect URI. If that URI cannot be trusted the
     *       request continues as before (code, then {@code invalid_grant} at the token endpoint).</li>
     *   <li>Anything else (a {@code POST} authorize) → continues as before: a POSTed request cannot be
     *       resumed by a redirect, and the issuer still refuses a token without membership — no token,
     *       no loop.</li>
     * </ul>
     *
     * @return {@code true} when this method wrote the response (the chain must stop)
     */
    private boolean askForConsent(HttpServletRequest request, HttpServletResponse response, String clientId)
            throws IOException {
        if (isPromptNone(request)) {
            RegisteredClient client = registeredClientRepository.findByClientId(clientId);
            Optional<String> error = client == null ? Optional.empty()
                    : PendingSiteConsentStore.errorRedirect(client, request.getParameter("redirect_uri"),
                            request.getParameter("state"), "consent_required",
                            "first visit to this site needs the user's consent");
            if (error.isEmpty()) {
                return false;
            }
            response.sendRedirect(error.get());
            return true;
        }
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        pendingSiteConsentStore.save(request, response);
        response.sendRedirect(request.getContextPath() + CONSENT_PATH);
        return true;
    }

    private static boolean isPromptNone(HttpServletRequest request) {
        String prompt = request.getParameter("prompt");
        if (prompt == null) {
            return false;
        }
        for (String value : prompt.trim().split("\\s+")) {
            if ("none".equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * TASK-BE-610 (owner decision ①, 2026-09-26) — the console exemption holds only for a person
     * with no credential in the console tenant. A consumer session reused for the console became
     * the console token, the operator exchange found no operator, and {@code demo@demo.com} landed
     * on {@code /onboarding} although they hold an {@code iam} credential (16th demo window).
     *
     * <p>The key is the session's email — the key the form login selects a credential by. Re-
     * authentication grants nothing: the console login page then picks the {@code iam} credential
     * by its scoped lookup and asks for ITS password, so the resumed authorize carries tenant
     * {@code iam} and passes this gate (no loop). A person without one — an ADR-MONO-044 D5
     * self-onboarded operator — is untouched, as before.
     *
     * <p>A failed lookup does NOT re-authenticate: for a D5 consumer that would loop (log in again →
     * the same consumer session → the same failure). It falls back to the pre-BE-610 behaviour.
     */
    private boolean holdsConsoleCredential(Authentication principal, String sessionTenant, String clientId) {
        String email = sessionEmail(principal);
        if (email == null) {
            return false;
        }
        boolean holds;
        try {
            holds = credentialRepository
                    .findByTenantIdAndEmail(TenantContext.CONSOLE_TENANT_ID, email)
                    .isPresent();
        } catch (RuntimeException e) {
            log.warn("authorize: console-credential lookup failed — session reused as before "
                    + "(TASK-BE-610)", e);
            return false;
        }
        if (holds) {
            log.info("authorize: session tenant {} on console client {} and the session's email holds "
                    + "a console credential — re-authentication required (TASK-BE-610)",
                    sessionTenant, clientId);
        }
        return holds;
    }

    /**
     * TASK-BE-615 (multi-tenancy.md § 소비자 계정 풀 § 4) — a consumer-pool principal is one account on
     * every consumer site, so on a consumer-site client its session is reused WITHOUT re-
     * authentication: the token is that site's (tenant = the client's site, roles = that site's only
     * — {@code TenantClaimTokenCustomizer}).
     *
     * <ul>
     *   <li><b>Member of the site</b> (ACTIVE) → pass → code → site token. AC-1.</li>
     *   <li><b>Consumer site, no membership at all</b> → TASK-BE-616: the first-visit consent screen
     *       ({@link Decision#CONSENT}). «Accept» writes the membership and resumes this authorize, which
     *       then passes as a member; «decline» answers the client {@code access_denied}. Re-
     *       authenticating instead would loop: the client's login form picks the pool credential first,
     *       the session is the same pool principal, and this gate would send it back to the form
     *       forever.</li>
     *   <li><b>Consumer site, LEFT by the person themself</b> ({@code leftBy = SELF}) → TASK-BE-619 (owner
     *       decision 2026-10-03 «다시 동의하면 복귀»): the consent screen again, exactly like a first visit —
     *       «accept» reopens the membership (account-service), «decline» answers {@code access_denied}.</li>
     *   <li><b>Consumer site, LEFT because the site's operator removed the account</b> ({@code leftBy =
     *       OPERATOR}, or no recorded actor) → pass, and the token endpoint refuses ({@code invalid_grant} —
     *       no token without an ACTIVE membership; TASK-BE-615 behaviour). No consent screen: consent cannot
     *       undo a removal.</li>
     *   <li><b>Not a consumer site</b> (a B2B client — wms, erp …) → re-authentication, like any
     *       session of another tenant: that client's form does not look the pool up (pool-first is a
     *       consumer-site rule), so the resumed authorize carries the client's own tenant.</li>
     *   <li><b>Lookup failure</b> → re-authentication (the conservative side). No loop: the form login
     *       needs the same answer before it would pick the pool credential, and fails closed without it.</li>
     * </ul>
     * A per-site (non-pool) principal never reaches this method — its rule is byte-unchanged (AC-2).
     * The console never reaches it either ({@link AuthorizationSessionTenant#mapsPoolPrincipalTo}
     * excludes {@code iam}): no consent screen there, and no operator account is created (D1).
     */
    private Decision consumerSiteDecision(Authentication principal, String clientTenant, String clientId) {
        String accountId = principal.getDetails() instanceof Map<?, ?> details
                && details.get(PrincipalDetailKeys.ACCOUNT_ID) instanceof String s && !s.isBlank() ? s : null;
        if (accountId == null) {
            return Decision.REAUTHENTICATE;
        }
        try {
            ConsumerSiteMembershipLookupResult answer =
                    accountServicePort.getConsumerSiteMembership(clientTenant, accountId);
            if (answer == null || !answer.consumerSite()) {
                log.info("authorize: pool session on client {} of non-consumer tenant {} — re-authentication "
                        + "required (TASK-BE-615)", clientId, clientTenant);
                return Decision.REAUTHENTICATE;
            }
            if (answer.membershipStatus() == null) {
                log.info("authorize: pool session on consumer site {} (client {}) without a membership — "
                        + "first-visit consent (TASK-BE-616)", clientTenant, clientId);
                return Decision.CONSENT;
            }
            if (answer.isReopenableByConsent()) {
                log.info("authorize: pool session on consumer site {} (client {}) that the account left "
                        + "itself — consent again to come back (TASK-BE-619)", clientTenant, clientId);
                return Decision.CONSENT;
            }
            return Decision.PASS;
        } catch (RuntimeException e) {
            log.warn("authorize: consumer-site lookup failed for a pool session on client {} — "
                    + "re-authentication (TASK-BE-615)", clientId, e);
            return Decision.REAUTHENTICATE;
        }
    }

    /** The email the login published in the principal details, else the principal name (both paths set both). */
    private static String sessionEmail(Authentication principal) {
        if (principal.getDetails() instanceof Map<?, ?> details
                && details.get(PrincipalDetailKeys.EMAIL) instanceof String s && !s.isBlank()) {
            return s;
        }
        String name = principal.getName();
        return name != null && !name.isBlank() ? name : null;
    }

    /** The client's {@code custom.tenant_id}, trimmed — the same source {@code SavedRequestTenantResolver} reads. */
    private static String clientTenant(RegisteredClient client) {
        Object raw = client.getClientSettings().getSetting(OAuthClientMapper.SETTING_TENANT_ID);
        return raw instanceof String s && !s.isBlank() ? s.trim() : null;
    }
}
