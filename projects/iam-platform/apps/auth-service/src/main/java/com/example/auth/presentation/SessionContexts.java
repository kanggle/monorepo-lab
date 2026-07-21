package com.example.auth.presentation;

import com.example.auth.domain.session.SessionContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Builds a {@link SessionContext} from the standard request headers.
 *
 * <p>The four auth entry-point controllers ({@code LoginController},
 * {@code RefreshController}, {@code OAuthController},
 * {@code SocialLoginBrowserController}) all derived the session context from the
 * same four inputs — remote address, {@code User-Agent},
 * {@code X-Device-Fingerprint}, and {@code X-Geo-Country} (defaulting to
 * {@code "XX"}). This factory is the single source for that mapping.
 */
public final class SessionContexts {

    private static final String DEFAULT_GEO_COUNTRY = "XX";

    private SessionContexts() {
    }

    /**
     * Extracts the session context from the request. A missing {@code X-Geo-Country}
     * header defaults to {@code "XX"}.
     */
    public static SessionContext fromRequest(HttpServletRequest request) {
        String geoCountry = request.getHeader("X-Geo-Country");
        return new SessionContext(
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("X-Device-Fingerprint"),
                geoCountry != null ? geoCountry : DEFAULT_GEO_COUNTRY);
    }
}
