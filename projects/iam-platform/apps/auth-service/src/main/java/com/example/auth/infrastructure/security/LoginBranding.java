package com.example.auth.infrastructure.security;

import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * TASK-BE-613 (ADR-007) — what the shared {@code /login} and {@code /signup} pages show for the
 * OIDC client that started the flow. The form itself is identical for every client; only these
 * values differ.
 *
 * <p>Every field is already validated and never blank except {@code description} and
 * {@code logo}, which are {@code null} when absent. The templates can therefore print them
 * without re-checking, and must print them only through escaping attributes
 * ({@code th:text}, {@code th:attr}) — the values come from the database, not from code.
 *
 * <p>Fallback is <b>per key</b>: a client that sets only a service name still gets a title
 * derived from it, and a malformed colour on an otherwise complete client falls back alone.
 *
 * @param serviceName  the service the visitor is signing in to (e.g. {@code GAP})
 * @param title        the {@code <title>}/{@code <h1>} of the login page
 * @param description  the subtitle, or {@code null}
 * @param logo         an allowlisted logo name (see {@link #ALLOWED_LOGOS}), or {@code null}
 * @param primaryColor a {@code #RRGGBB} colour
 */
public record LoginBranding(String serviceName, String title, String description,
                            String logo, String primaryColor) {

    /**
     * ADR-007 D3 as accepted: a flow with no identifiable client — a direct visit, a client
     * with no branding — is shown as IAM. (The proposal said «Global Account»; the owner's
     * rider on the ACCEPT replaced the wording.)
     */
    public static final String DEFAULT_SERVICE_NAME = "IAM";

    /** The colour the pages used before branding existed. */
    public static final String DEFAULT_PRIMARY_COLOR = "#2563eb";

    /**
     * Logo names a client may reference. Each one is an inline SVG fragment in
     * {@code templates/fragments/auth-page.html} — there is no URL to point anywhere else, and
     * a name outside this set renders no logo. Adding a logo = one name here + one
     * {@code th:case} there; {@code LoginBrandingPageSliceTest} fails if the two disagree.
     */
    public static final Set<String> ALLOWED_LOGOS = Set.of("console");

    public static final LoginBranding DEFAULT = of(null, null, null, null, null);

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    /** Reads the branding keys of a registered client's settings. */
    public static LoginBranding from(ClientSettings settings) {
        return of(
                settings.getSetting(OAuthClientMapper.SETTING_BRANDING_SERVICE_NAME),
                settings.getSetting(OAuthClientMapper.SETTING_BRANDING_TITLE),
                settings.getSetting(OAuthClientMapper.SETTING_BRANDING_DESCRIPTION),
                settings.getSetting(OAuthClientMapper.SETTING_BRANDING_LOGO),
                settings.getSetting(OAuthClientMapper.SETTING_BRANDING_PRIMARY_COLOR));
    }

    static LoginBranding of(Object serviceName, Object title, Object description,
                            Object logo, Object primaryColor) {
        String service = text(serviceName);
        if (service == null) {
            service = DEFAULT_SERVICE_NAME;
        }
        String heading = text(title);
        if (heading == null) {
            heading = service + " 로그인";
        }
        String logoName = text(logo);
        if (logoName != null && !ALLOWED_LOGOS.contains(logoName)) {
            logoName = null;
        }
        String color = text(primaryColor);
        if (color == null || !HEX_COLOR.matcher(color).matches()) {
            color = DEFAULT_PRIMARY_COLOR;
        }
        return new LoginBranding(service, heading, text(description), logoName, color);
    }

    /** The heading of the signup page — the same service, a different act. */
    public String signupTitle() {
        return serviceName + " 회원가입";
    }

    /** A non-blank trimmed string, or {@code null} for anything else (absent, blank, non-string). */
    private static String text(Object raw) {
        if (raw instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return null;
    }
}
