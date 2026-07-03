package com.example.auth.presentation;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * TASK-BE-470 — renders the browser self-service signup page.
 *
 * <p>Sibling of {@link LoginPageController}: the Thymeleaf {@code signup} view
 * carries an email/password/confirm/displayName form whose client-side script
 * submits directly to the account-service {@code POST /api/accounts/signup}
 * endpoint (same gateway origin, so a relative-path {@code fetch} needs no CORS;
 * that endpoint is {@code permitAll} + CSRF-disabled + STATELESS, so no CSRF
 * token is required). On {@code 201} the page redirects to {@code /login?registered}.
 *
 * <p>The design keeps account-service the sole owner of signup — auth-service does
 * NOT proxy the API server-side, so no new {@code auth → account} HTTP coupling is
 * introduced (the existing dependency is one-directional {@code account → auth} for
 * credential creation). The trade-off is no post-signup auto-login: the user logs in
 * once on {@code /login} after registering.
 *
 * <p>{@code GET /signup} is added to {@code WebLoginSecurityConfig}'s {@code @Order(0)}
 * form-login chain {@code securityMatcher} (which is {@code anyRequest().permitAll()}),
 * so the page is public. This controller only renders HTML.
 */
@Controller
public class SignupPageController {

    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }
}
