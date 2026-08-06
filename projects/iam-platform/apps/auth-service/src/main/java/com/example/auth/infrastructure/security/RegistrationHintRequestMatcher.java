package com.example.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * TASK-BE-578 — recognises a <b>registration hint</b> on an
 * {@code /oauth2/authorize} request: the caller is asking us to start the user
 * on the signup form rather than the login form.
 *
 * <p>The hint is the OIDC standard {@code prompt=create} (<i>Initiating User
 * Registration via OpenID Connect 1.0</i>). Measured against a live SAS before
 * choosing it: SAS <b>ignores prompt values it does not implement</b> — with
 * {@code prompt=create}, {@code prompt=bogusvalue}, or no {@code prompt} at all,
 * an unauthenticated authorize request reaches the entry point and an
 * authenticated one completes with a {@code code}, identically. So carrying the
 * standard value costs nothing and needs no custom parameter. ({@code prompt=none}
 * is the one value SAS <i>does</i> implement — see below.)
 *
 * <p><b>The hint decides the login-vs-signup landing page and nothing else.</b>
 * It must never influence which tenant an account is born into — that stays
 * derived from the saved authorize request's {@code client_id} via
 * {@link SavedRequestTenantResolver}. Promoting a caller-supplied hint to a
 * tenant source would re-open exactly the hole TASK-FE-097 closed: picking a
 * tenant by sending an arbitrary value.
 *
 * <h2>Parsing</h2>
 *
 * <p>{@code prompt} is a space-delimited list of values (OIDC Core 3.1.2.1), so
 * this splits on whitespace and compares whole tokens. A substring test would
 * match {@code created} or a {@code state} that happens to contain the word.
 *
 * <p>{@code none} wins over {@code create}. {@code prompt=none} means "do not
 * display any authentication or consent UI"; combining it with a request to
 * display the signup form is self-contradictory, and of the two readings the
 * safe one is to show <em>less</em> UI, not more.
 *
 * <p><b>That rule is defence in depth, not the live mechanism.</b> Measured:
 * SAS implements {@code prompt=none} itself and short-circuits an
 * unauthenticated authorize request with
 * {@code redirect_uri?error=login_required} from its own authorization endpoint
 * filter — before {@code ExceptionTranslationFilter} consults any entry point.
 * So today this branch is not reached, and the honest description of who
 * enforces the conflict is "SAS does". It stays because the cost is one
 * comparison and the failure it prevents is showing a signup form to a caller
 * who explicitly asked for no UI, which is what would happen if a future SAS
 * version or configuration stopped handling {@code none} first. The unit test
 * is where this rule is exercised.
 */
public class RegistrationHintRequestMatcher implements RequestMatcher {

    /** OIDC {@code prompt} parameter name. */
    static final String PROMPT_PARAM = "prompt";

    /** The registration hint value (<i>Initiating User Registration via OIDC</i>). */
    static final String PROMPT_CREATE = "create";

    /** {@code prompt=none} — "display no UI at all"; contradicts the hint and wins. */
    static final String PROMPT_NONE = "none";

    @Override
    public boolean matches(HttpServletRequest request) {
        String[] values = request.getParameterValues(PROMPT_PARAM);
        if (values == null) {
            return false;
        }

        boolean create = false;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            // Space-delimited list per OIDC Core 3.1.2.1. A repeated `prompt`
            // query parameter is malformed, but tolerating it here costs nothing
            // and keeps a duplicated `none` authoritative.
            for (String token : value.trim().split("\\s+")) {
                if (PROMPT_NONE.equals(token)) {
                    return false;
                }
                if (PROMPT_CREATE.equals(token)) {
                    create = true;
                }
            }
        }
        return create;
    }
}
