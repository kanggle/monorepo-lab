package com.example.auth.infrastructure.security;

import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * TASK-BE-616 (multi-tenancy.md § 소비자 계정 풀 § 4) — the {@code /oauth2/authorize} request that is
 * waiting on a consumer site's first-visit consent.
 *
 * <p>The authorize gate parks the request here and sends the browser to {@code /consent}; the consent
 * page reads it back to know which site (and which brand) it is asking about, and, on «accept»,
 * resumes it. It lives in the HTTP session under its <b>own</b> attribute — not the form login's
 * {@code SPRING_SECURITY_SAVED_REQUEST} — so a consent can never be mistaken for a login continuation,
 * and a login can never consume it.
 *
 * <p><b>Back-button safety (task Edge Case 1).</b> Reading does not remove it: reloading the consent
 * page, or going back to it, finds the same request. It is removed only when the person answers
 * (accept or decline), or replaced by the next parked authorize.
 *
 * <p><b>Trust.</b> Same rule as {@link SavedRequestTenantResolver}: the client comes only from a parked
 * {@code /oauth2/authorize} request that the gate itself saved, never from the current request — a
 * link cannot make the consent page speak for another site.
 */
@Component
public class PendingSiteConsentStore {

    /** Session attribute of the parked authorize request. */
    public static final String SESSION_ATTRIBUTE = "IAM_PENDING_SITE_CONSENT";

    private static final String AUTHORIZE_PATH = "/oauth2/authorize";

    private final RegisteredClientRepository registeredClientRepository;
    private final HttpSessionRequestCache cache = new HttpSessionRequestCache();

    public PendingSiteConsentStore(RegisteredClientRepository registeredClientRepository) {
        this.registeredClientRepository = Objects.requireNonNull(
                registeredClientRepository, "registeredClientRepository");
        cache.setSessionAttrName(SESSION_ATTRIBUTE);
        // The resumed URL is the authorize request exactly as it came — no "continue" marker.
        cache.setMatchingRequestParameterName(null);
    }

    /** Parks the current {@code /oauth2/authorize} request (replacing any earlier one). */
    public void save(HttpServletRequest request, HttpServletResponse response) {
        cache.saveRequest(request, response);
    }

    /**
     * The parked request, when there is one and it is a {@code /oauth2/authorize} of a registered client
     * with a tenant. Empty otherwise (expired session, nothing parked, a client since removed).
     */
    public Optional<PendingSiteConsent> find(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = cache.getRequest(request, response);
        if (saved == null) {
            return Optional.empty();
        }
        String url = saved.getRedirectUrl();
        if (url == null || !url.contains(AUTHORIZE_PATH)) {
            return Optional.empty();
        }
        String clientId = first(saved, "client_id");
        if (clientId == null) {
            return Optional.empty();
        }
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client == null) {
            return Optional.empty();
        }
        Object tenant = client.getClientSettings().getSetting(OAuthClientMapper.SETTING_TENANT_ID);
        if (!(tenant instanceof String site) || site.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new PendingSiteConsent(client, site.trim(), url,
                first(saved, "redirect_uri"), first(saved, "state")));
    }

    /** Forgets the parked request — the person answered. */
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        cache.removeRequest(request, response);
    }

    private static String first(SavedRequest saved, String name) {
        String[] values = saved.getParameterValues(name);
        return values != null && values.length > 0 && values[0] != null && !values[0].isBlank() ? values[0] : null;
    }

    /**
     * Builds the OAuth error response URI for {@code client} — {@code error} (+ description, + the
     * client's {@code state}) on the redirect URI the request named — or empty when that redirect URI
     * cannot be trusted.
     *
     * <p>🔴 The redirect URI must be one of the client's <b>registered</b> URIs, compared exactly; with
     * none in the request, the client must have exactly one. Anything else answers empty and the
     * caller shows the error on the IAM page instead — an error response is a redirect like any other,
     * and an unchecked one is an open redirector (RFC 6749 § 4.1.2.1).
     */
    public static Optional<String> errorRedirect(RegisteredClient client, String requestedRedirectUri,
                                                 String state, String error, String description) {
        Set<String> registered = client.getRedirectUris();
        String target;
        if (requestedRedirectUri != null) {
            if (!registered.contains(requestedRedirectUri)) {
                return Optional.empty();
            }
            target = requestedRedirectUri;
        } else if (registered.size() == 1) {
            target = registered.iterator().next();
        } else {
            return Optional.empty();
        }
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(target)
                .queryParam("error", encode(error));
        if (description != null) {
            uri.queryParam("error_description", encode(description));
        }
        if (state != null) {
            uri.queryParam("state", encode(state));
        }
        return Optional.of(uri.build(true).toUriString());
    }

    private static String encode(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    /**
     * A parked authorize request awaiting consent.
     *
     * @param client       the registered client that started it
     * @param siteTenantId the client's tenant — the site the consent is about
     * @param authorizeUrl the full authorize URL to resume after «accept»
     * @param redirectUri  the request's {@code redirect_uri}, or {@code null}
     * @param state        the request's {@code state}, or {@code null}
     */
    public record PendingSiteConsent(RegisteredClient client, String siteTenantId, String authorizeUrl,
                                     String redirectUri, String state) {

        /** {@link #errorRedirect} for this request. */
        public Optional<String> errorRedirect(String error, String description) {
            return PendingSiteConsentStore.errorRedirect(client, redirectUri, state, error, description);
        }
    }
}
