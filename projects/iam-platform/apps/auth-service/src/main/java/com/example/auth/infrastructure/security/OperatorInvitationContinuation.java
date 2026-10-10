package com.example.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.savedrequest.SimpleSavedRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * TASK-MONO-772 S3 (auth-api.md § IdP 브라우저 화면 — 운영자 초대 수락) — «after logging in, come back to the
 * acceptance page».
 *
 * <p>The acceptance page is {@code permitAll} (its controller makes the checks), so no entry point ever saves it
 * as the login continuation. When it needs a login — the page's «로그인» link, or right after the site-less signup —
 * it parks {@code /operator-invitations/accept?token=…} where Spring Security's {@code HttpSessionRequestCache}
 * keeps a saved request. The form login's default {@code SavedRequestAwareAuthenticationSuccessHandler} then
 * redirects there, and {@link #isAcceptanceContinuation} is how the form-login provider knows that this login must
 * pick the {@code consumer-pool} credential only (S1-7).
 *
 * <p>🔴 R4: the token lives in the server-side session only (the saved request) and in the redirect the browser
 * already had — it is never logged here.
 */
public final class OperatorInvitationContinuation {

    /** The page path. */
    public static final String ACCEPT_PATH = "/operator-invitations/accept";

    /** {@code HttpSessionRequestCache}'s default session attribute — where a saved request is read from. */
    static final String SAVED_REQUEST_ATTRIBUTE = "SPRING_SECURITY_SAVED_REQUEST";

    private OperatorInvitationContinuation() {
    }

    /** Parks the acceptance page for {@code token} as this session's login continuation (creates the session). */
    public static void park(HttpServletRequest request, String token) {
        String url = UriComponentsBuilder.fromPath(ACCEPT_PATH).queryParam("token", token).encode().build()
                .toUriString();
        request.getSession(true).setAttribute(SAVED_REQUEST_ATTRIBUTE, new SimpleSavedRequest(url));
    }

    /** Is this saved request the acceptance page? (path only — query and host do not matter) */
    public static boolean isAcceptanceContinuation(SavedRequest saved) {
        if (saved == null || saved.getRedirectUrl() == null) {
            return false;
        }
        try {
            String path = URI.create(saved.getRedirectUrl()).getPath();
            return path != null && path.endsWith(ACCEPT_PATH);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
