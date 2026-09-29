package com.example.auth.infrastructure.oauth2;

import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
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
    private final SecurityContextHolderStrategy securityContextHolderStrategy;

    AuthorizeSessionTenantGate(String authorizationEndpointUri,
                               RegisteredClientRepository registeredClientRepository,
                               CredentialRepository credentialRepository) {
        this(authorizationEndpointUri, registeredClientRepository, credentialRepository,
                SecurityContextHolder.getContextHolderStrategy());
    }

    AuthorizeSessionTenantGate(String authorizationEndpointUri,
                               RegisteredClientRepository registeredClientRepository,
                               CredentialRepository credentialRepository,
                               SecurityContextHolderStrategy securityContextHolderStrategy) {
        this.authorizationEndpoint = new AntPathRequestMatcher(
                Objects.requireNonNull(authorizationEndpointUri, "authorizationEndpointUri"));
        this.registeredClientRepository = Objects.requireNonNull(
                registeredClientRepository, "registeredClientRepository");
        this.credentialRepository = Objects.requireNonNull(
                credentialRepository, "credentialRepository");
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
        if (requiresReauthentication(principal, clientId)) {
            // Replace, never mutate: the context in the holder IS the object the HTTP session
            // stores, so setAuthentication(null) on it would log the user out of the client they
            // came from as well (bite-checked, TASK-BE-605).
            securityContextHolderStrategy.setContext(securityContextHolderStrategy.createEmptyContext());
        }
        filterChain.doFilter(request, response);
    }

    /**
     * @return {@code true} when the session must not be reused for this client's authorize
     */
    boolean requiresReauthentication(Authentication principal, String clientId) {
        if (principal == null || principal instanceof AnonymousAuthenticationToken
                || !principal.isAuthenticated()) {
            return false;
        }
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client == null) {
            return false;
        }
        String clientTenant = clientTenant(client);
        if (clientTenant == null) {
            return false;
        }
        String sessionTenant = AuthorizationSessionTenant.of(principal, clientTenant);
        if (clientTenant.equals(sessionTenant)) {
            return false;
        }
        if (TenantContext.CONSOLE_TENANT_ID.equals(clientTenant)) {
            return holdsConsoleCredential(principal, sessionTenant, clientId);
        }
        log.info("authorize: session tenant {} does not match client {} tenant {} — "
                + "re-authentication required (TASK-BE-605)", sessionTenant, clientId, clientTenant);
        return true;
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
