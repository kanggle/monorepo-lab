package com.example.auth.presentation;

import com.example.auth.application.OAuthLoginUseCase;
import com.example.auth.application.command.OAuthCallbackCommand;
import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.auth.application.exception.InvalidOAuthStateException;
import com.example.auth.application.exception.OAuthEmailRequiredException;
import com.example.auth.application.exception.OAuthProviderException;
import com.example.auth.application.exception.UnsupportedProviderException;
import com.example.auth.application.result.BrowserLoginResolution;
import com.example.auth.application.result.OAuthAuthorizeResult;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.infrastructure.security.SavedRequestTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 *       computed from the request base, then redirects to the provider.</li>
 *   <li>{@code GET /login/oauth/{provider}/callback} — resolves the account
 *       (reuses {@link OAuthLoginUseCase#resolveBrowserLogin}), establishes the
 *       SAS session, and resumes the saved {@code /oauth2/authorize}.</li>
 * </ul>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SocialLoginBrowserController {

    private final OAuthLoginUseCase oAuthLoginUseCase;
    private final SavedRequestTenantResolver savedRequestTenantResolver;

    /**
     * The SecurityContextRepository the SAS chain reads via the shared JSESSIONID.
     * MUST be {@link HttpSessionSecurityContextRepository} so the SAS
     * {@code /oauth2/authorize} filter (a separate filter chain) sees the same
     * authenticated context this controller persists.
     */
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    @GetMapping("/login/oauth/{provider}")
    public String startSocialLogin(@PathVariable String provider, HttpServletRequest request) {
        String callbackUri = browserCallbackUri(request, provider);
        try {
            OAuthAuthorizeResult result = oAuthLoginUseCase.authorize(provider, callbackUri);
            return "redirect:" + result.authorizationUrl();
        } catch (UnsupportedProviderException e) {
            log.warn("social login start rejected — unsupported provider '{}'", provider);
            return "redirect:/login?error=unsupported_provider";
        }
    }

    @GetMapping("/login/oauth/{provider}/callback")
    public String socialLoginCallback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response) {

        String callbackUri = browserCallbackUri(request, provider);

        SessionContext sessionContext = new SessionContext(
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("X-Device-Fingerprint"),
                request.getHeader("X-Geo-Country") != null
                        ? request.getHeader("X-Geo-Country") : "XX");

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
            return "redirect:/login?error=email_required";
        } catch (AccountLockedException | AccountStatusException e) {
            return "redirect:/login?error=account_unavailable";
        } catch (InvalidOAuthStateException e) {
            return "redirect:/login?error=invalid_state";
        } catch (OAuthProviderException e) {
            return "redirect:/login?error=provider_error";
        } catch (UnsupportedProviderException e) {
            return "redirect:/login?error=unsupported_provider";
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
        details.put("tenant_id", resolution.tenantId());
        details.put("tenant_type", resolution.tenantType());
        details.put("account_id", login.accountId());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        login.email(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(details);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        log.debug("social login session established for account_id={} tenant_id={} (newAccount={})",
                login.accountId(), resolution.tenantId(), login.isNewAccount());
    }

    /**
     * Computes the browser callback URI from the request base:
     * {@code scheme://host[:port]/login/oauth/{provider}/callback}. This MUST be in
     * the provider's allowed-redirect-uris (validated by {@code authorize()}); the
     * application.yml allowlist adds the {@code iam.local} + {@code localhost:8081}
     * variants.
     */
    private String browserCallbackUri(HttpServletRequest request, String provider) {
        StringBuilder base = new StringBuilder();
        base.append(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        if (!defaultPort && port > 0) {
            base.append(':').append(port);
        }
        base.append("/login/oauth/").append(provider).append("/callback");
        return base.toString();
    }
}
