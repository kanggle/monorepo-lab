package com.example.auth.presentation;

import com.example.auth.application.OAuthLoginUseCase;
import com.example.auth.application.command.OAuthCallbackCommand;
import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.auth.application.exception.InvalidOAuthRedirectUriException;
import com.example.auth.application.exception.InvalidOAuthStateException;
import com.example.auth.application.exception.OAuthEmailRequiredException;
import com.example.auth.application.exception.OAuthProviderException;
import com.example.auth.application.exception.UnsupportedProviderException;
import com.example.auth.application.result.BrowserLoginResolution;
import com.example.auth.application.result.OAuthAuthorizeResult;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.infrastructure.security.SavedRequestTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TASK-BE-396 (ADR-006 option B) — the SAS browser-session social-login bridge.
 *
 * <p>Replaces the legacy custom-JWT JSON flow ({@code /api/auth/oauth/**}) for the
 * browser case. The social authentication terminates in a SAS-consumed
 * authenticated HTTP session (JSESSIONID {@code SecurityContext}) — NOT a custom
 * JWT — so the subsequent saved {@code /oauth2/authorize} resumes and SAS issues
 * standard tokens.
 *
 * <ul>
 *   <li>{@code GET /login/oauth/{provider}} — starts the upstream authorization
 *       (reuses {@link OAuthLoginUseCase#authorize}) with a browser callback URI
 *       built from the configured issuer base URL, then redirects to the provider.</li>
 *   <li>{@code GET /login/oauth/{provider}/callback} — resolves the account
 *       (reuses {@link OAuthLoginUseCase#resolveBrowserLogin}), establishes the
 *       SAS session, and resumes the saved {@code /oauth2/authorize}.</li>
 * </ul>
 */
@Slf4j
@Controller
public class SocialLoginBrowserController {

    private final OAuthLoginUseCase oAuthLoginUseCase;
    private final SavedRequestTenantResolver savedRequestTenantResolver;

    /**
     * The public base URL the browser uses for this auth-service (the OIDC issuer).
     * The social callback URI is built from this — NOT from the request {@code Host}
     * header — so it is deterministic, registered in each provider's
     * allowed-redirect-uris, and not subject to Host-header injection.
     */
    private final String browserCallbackBaseUrl;

    /**
     * The SecurityContextRepository the SAS chain reads via the shared JSESSIONID.
     * MUST be {@link HttpSessionSecurityContextRepository} so the SAS
     * {@code /oauth2/authorize} filter (a separate filter chain) sees the same
     * authenticated context this controller persists.
     */
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    /**
     * Prefix for every failure redirect back to the login page. Both entry points
     * ({@code startSocialLogin} and {@code socialLoginCallback}) surface errors by
     * redirecting to {@code /login?error=<code>}; {@link #loginError(String)} is the
     * single source for that target.
     */
    private static final String LOGIN_ERROR_REDIRECT_PREFIX = "redirect:/login?error=";

    public SocialLoginBrowserController(
            OAuthLoginUseCase oAuthLoginUseCase,
            SavedRequestTenantResolver savedRequestTenantResolver,
            @Value("${oidc.issuer-url:http://localhost:8081}") String issuerUrl) {
        this.oAuthLoginUseCase = oAuthLoginUseCase;
        this.savedRequestTenantResolver = savedRequestTenantResolver;
        this.browserCallbackBaseUrl = issuerUrl.endsWith("/")
                ? issuerUrl.substring(0, issuerUrl.length() - 1)
                : issuerUrl;
    }

    @GetMapping("/login/oauth/{provider}")
    public String startSocialLogin(@PathVariable String provider) {
        String callbackUri = browserCallbackUri(provider);
        try {
            OAuthAuthorizeResult result = oAuthLoginUseCase.authorize(provider, callbackUri);
            return "redirect:" + result.authorizationUrl();
        } catch (UnsupportedProviderException e) {
            log.warn("social login start rejected — unsupported provider '{}'", provider);
            return loginError("unsupported_provider");
        } catch (InvalidOAuthRedirectUriException e) {
            // The issuer-derived callback URI is not in the provider's
            // allowed-redirect-uris — a server misconfiguration, not user error.
            log.error("social login start failed — browser callback URI '{}' not in '{}' "
                    + "allowed-redirect-uris (check oidc.issuer-url + OAUTH_<P>_ALLOWED_REDIRECT_URIS)",
                    callbackUri, provider);
            return loginError("provider_error");
        }
    }

    @GetMapping("/login/oauth/{provider}/callback")
    public String socialLoginCallback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response) {

        String callbackUri = browserCallbackUri(provider);

        SessionContext sessionContext = SessionContexts.fromRequest(request);

        OAuthCallbackCommand command =
                new OAuthCallbackCommand(provider, code, state, callbackUri, sessionContext);

        // Resolve the tenant from the initiating OIDC client (saved /oauth2/authorize
        // request) BEFORE consuming the saved request for the redirect URL.
        SavedRequestTenantResolver.Resolution resolution =
                savedRequestTenantResolver.resolve(request, response);

        BrowserLoginResolution login;
        try {
            login = oAuthLoginUseCase.resolveBrowserLogin(command, resolution.tenantId());
        } catch (OAuthEmailRequiredException e) {
            return loginError("email_required");
        } catch (AccountLockedException | AccountStatusException e) {
            return loginError("account_unavailable");
        } catch (InvalidOAuthStateException e) {
            return loginError("invalid_state");
        } catch (InvalidOAuthRedirectUriException | OAuthProviderException e) {
            return loginError("provider_error");
        } catch (UnsupportedProviderException e) {
            return loginError("unsupported_provider");
        }

        establishSession(login, resolution, request, response);

        // Resume the saved /oauth2/authorize request → SAS issues standard tokens.
        String redirectUrl = resolution.redirectUrl();
        return "redirect:" + (redirectUrl != null ? redirectUrl : "/");
    }

    /**
     * Builds the SAS-session principal and persists it to the HTTP session so the
     * SAS chain consumes it via the shared JSESSIONID.
     *
     * <p>Mirrors {@code CredentialAuthenticationProvider}'s principal template:
     * {@code UsernamePasswordAuthenticationToken(email, null, [ROLE_USER])} with a
     * {@code details} map of {@code tenant_id} / {@code tenant_type} /
     * {@code account_id}. The details map MUST be a {@link HashMap} (never
     * {@code Map.of}) — SAS's {@code JdbcOAuth2AuthorizationService} serializes the
     * Authentication via the strict {@code SecurityJackson2Modules} allowlist, and
     * an immutable map breaks the {@code /oauth2/token} round-trip.
     */
    private void establishSession(BrowserLoginResolution login,
                                  SavedRequestTenantResolver.Resolution resolution,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, resolution.tenantId());
        details.put(PrincipalDetailKeys.TENANT_TYPE, resolution.tenantType());
        details.put(PrincipalDetailKeys.ACCOUNT_ID, login.accountId());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        login.email(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(details);

        // TASK-BE-521 (item A) — session-fixation defense. This path establishes the
        // authenticated SAS session MANUALLY (no AuthenticationFilter), so it bypasses
        // the SessionAuthenticationStrategy (SS6 default changeSessionId) that the
        // sibling password form-login path gets automatically. Rotate the session ID
        // here, before persisting the authenticated context, so a pre-fixated
        // JSESSIONID cannot become an authenticated session. changeSessionId() keeps
        // all session attributes, so the saved /oauth2/authorize request
        // (HttpSessionRequestCache) survives and the resume still works (AC-2).
        // Guarded on getSession(false): with no pre-existing session there is nothing
        // to fixate, and saveContext below creates a fresh one.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        log.debug("social login session established for account_id={} tenant_id={} (newAccount={})",
                login.accountId(), resolution.tenantId(), login.isNewAccount());
    }

    /**
     * Builds the browser callback URI from the configured issuer base URL:
     * {@code <issuer>/login/oauth/{provider}/callback}. This MUST be in the
     * provider's allowed-redirect-uris (validated by {@code authorize()}); the
     * application.yml allowlist registers the {@code iam.local} (prod) +
     * {@code localhost:8081} (dev) variants matching {@code oidc.issuer-url}.
     */
    private String browserCallbackUri(String provider) {
        return browserCallbackBaseUrl + "/login/oauth/" + provider + "/callback";
    }

    /** Builds the redirect back to the login page carrying an {@code error} code. */
    private static String loginError(String code) {
        return LOGIN_ERROR_REDIRECT_PREFIX + code;
    }
}
