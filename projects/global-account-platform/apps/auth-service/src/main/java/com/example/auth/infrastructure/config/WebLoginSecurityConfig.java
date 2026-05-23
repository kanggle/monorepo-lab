package com.example.auth.infrastructure.config;

import com.example.auth.infrastructure.security.CredentialAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import java.util.List;

/**
 * TASK-BE-309 — HTML form-login surface for Spring Authorization Server's
 * browser-driven OIDC PKCE flow.
 *
 * <p>Before this filter chain, an unauthenticated browser GET on
 * {@code /oauth2/authorize} was redirected by the SAS configurer's
 * {@code LoginUrlAuthenticationEntryPoint} to {@code /api/auth/login} — but
 * {@link com.example.auth.presentation.LoginController} is a JSON-only POST
 * endpoint, so the redirect was a browser dead-end. As of TASK-PC-FE-019's
 * Playwright harness standup this gap was the architectural blocker for true
 * OIDC PKCE end-to-end browser testing (fixture had to fall back to a
 * {@code client_credentials} backdoor).
 *
 * <p>This chain runs at {@code @Order(0)} so it precedes the SAS chain
 * ({@code @Order(1)}) and the legacy stateless API chain ({@code @Order(2)}).
 * It matches only {@code /login} and {@code /logout} — every other path
 * falls through to the lower-precedence chains:
 *
 * <ul>
 *   <li>{@code GET /login} — Spring Security's default
 *       {@code DefaultLoginPageGeneratingFilter} renders a minimal HTML form
 *       with CSRF token. v1 placeholder UI; a custom Thymeleaf template is a
 *       separate future task.</li>
 *   <li>{@code POST /login} — bridged through
 *       {@link CredentialAuthenticationProvider} which verifies credentials
 *       via the existing {@link com.example.security.password.PasswordHasher}
 *       + {@link com.example.auth.domain.repository.CredentialRepository}.
 *       Success creates an HTTP session and redirects to the original URL
 *       held in {@code HttpSessionRequestCache} (typically
 *       {@code /oauth2/authorize}).</li>
 *   <li>{@code POST /logout} — clears the session, redirects to
 *       {@code /login?logout}.</li>
 * </ul>
 *
 * <p>CSRF is enabled (Spring Security default for form-login). The SAS chain
 * already disables CSRF for token / revoke / introspect endpoints; that
 * configuration is unaffected because it lives on a different filter chain
 * with a different {@code securityMatcher}.
 *
 * <p>Session policy is {@code IF_REQUIRED} (default) — sessions are created
 * only when needed and the SAS chain's downstream {@code /oauth2/authorize}
 * filter can read the same {@code SecurityContext} via the shared
 * {@code JSESSIONID} cookie. The legacy {@code STATELESS} policy on
 * {@link SecurityConfig} ({@code @Order(2)}) is untouched and continues to
 * govern {@code /api/auth/**} JSON endpoints.
 */
@Configuration
@EnableWebSecurity
public class WebLoginSecurityConfig {

    /**
     * Builds the form-login filter chain.
     *
     * <p>The {@code securityMatcher(OrRequestMatcher)} restricts this chain
     * to {@code /login} + {@code /logout} only — every other request flows to
     * the SAS chain ({@code @Order(1)}) or the legacy API chain
     * ({@code @Order(2)}). This decoupling is intentional: the SAS chain
     * keeps its CSRF-disabled stance for programmatic token endpoints
     * unchanged, and the legacy {@code STATELESS} chain stays untouched.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain webLoginFilterChain(
            HttpSecurity http,
            CredentialAuthenticationProvider credentialAuthenticationProvider) throws Exception {
        AuthenticationManager authenticationManager =
                new ProviderManager(List.of(credentialAuthenticationProvider));

        http
                .securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher("/login", "GET"),
                        new AntPathRequestMatcher("/login", "POST"),
                        new AntPathRequestMatcher("/logout", "POST")))
                .authenticationManager(authenticationManager)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // Form-login itself + the post-logout landing must be public;
                        // the post-login redirect target (typically /oauth2/authorize)
                        // is governed by the SAS chain on its own filter.
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        // Use Spring Security's default-generated HTML form. Custom
                        // Thymeleaf UI is a separate future task.
                        //
                        // TASK-BE-311 — do NOT call `.loginPage("/login")`. In Spring
                        // Security 6, that setter flips `customLoginPage=true` on the
                        // FormLoginConfigurer, which SUPPRESSES the registration of
                        // `DefaultLoginPageGeneratingFilter` (Spring assumes the caller
                        // provides a controller-mapped /login view). Both the default
                        // form-login URL and the default login URL are `/login`
                        // already, so omitting the setter loses nothing functionally
                        // while keeping the auto-generated form active. The SAS
                        // chain's `LoginUrlAuthenticationEntryPoint("/login")` in
                        // AuthorizationServerConfig is independent of this setter and
                        // continues to drive unauth `/oauth2/authorize` → `/login`.
                        // BE-309's `FormLoginIntegrationTest` happened to pass without
                        // catching this regression because it only exercises POST /login
                        // (handled by UsernamePasswordAuthenticationFilter, present in
                        // both configurations) and the unauth entry-point redirect (a
                        // separate concern); it never asserted that GET /login renders
                        // HTML. PC-FE-028 iter 7 trace evidence + BE-311 iter 1
                        // diagnostic confirmed the missing
                        // DefaultLoginPageGeneratingFilter in chain[0].
                        .permitAll()
                        // No custom successHandler — the default
                        // SavedRequestAwareAuthenticationSuccessHandler reads
                        // HttpSessionRequestCache and redirects back to the original
                        // /oauth2/authorize URL.
                        .failureUrl("/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true));

        // CSRF is enabled by default for this chain. DefaultLoginPageGeneratingFilter
        // injects the CSRF token automatically into the HTML form.

        return http.build();
    }
}
