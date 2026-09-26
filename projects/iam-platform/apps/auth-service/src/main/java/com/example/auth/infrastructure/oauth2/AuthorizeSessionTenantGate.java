package com.example.auth.infrastructure.oauth2;

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
 *   <li>the client's tenant is {@link TenantContext#CONSOLE_TENANT_ID} → untouched. ADR-MONO-044
 *       D5 operators reach the console only with a consumer-tenant session;</li>
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
    private final SecurityContextHolderStrategy securityContextHolderStrategy;

    AuthorizeSessionTenantGate(String authorizationEndpointUri,
                               RegisteredClientRepository registeredClientRepository) {
        this(authorizationEndpointUri, registeredClientRepository,
                SecurityContextHolder.getContextHolderStrategy());
    }

    AuthorizeSessionTenantGate(String authorizationEndpointUri,
                               RegisteredClientRepository registeredClientRepository,
                               SecurityContextHolderStrategy securityContextHolderStrategy) {
        this.authorizationEndpoint = new AntPathRequestMatcher(
                Objects.requireNonNull(authorizationEndpointUri, "authorizationEndpointUri"));
        this.registeredClientRepository = Objects.requireNonNull(
                registeredClientRepository, "registeredClientRepository");
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
        if (clientTenant == null || TenantContext.CONSOLE_TENANT_ID.equals(clientTenant)) {
            return false;
        }
        String sessionTenant = AuthorizationSessionTenant.of(principal, clientTenant);
        if (clientTenant.equals(sessionTenant)) {
            return false;
        }
        log.info("authorize: session tenant {} does not match client {} tenant {} — "
                + "re-authentication required (TASK-BE-605)", sessionTenant, clientId, clientTenant);
        return true;
    }

    /** The client's {@code custom.tenant_id}, trimmed — the same source {@code SavedRequestTenantResolver} reads. */
    private static String clientTenant(RegisteredClient client) {
        Object raw = client.getClientSettings().getSetting(OAuthClientMapper.SETTING_TENANT_ID);
        return raw instanceof String s && !s.isBlank() ? s.trim() : null;
    }
}
