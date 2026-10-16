package com.example.finance.ledger.infrastructure.security;

import com.example.finance.ledger.presentation.security.PublicPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ledger-service Spring Security configuration.
 *
 * <ul>
 *   <li>{@code /actuator/{health,info,prometheus}} — public</li>
 *   <li>the 7 ledger <strong>control</strong> writes — require {@code FINANCE_LEDGER_CONTROL} or the workload scope
 *       {@code finance.write}</li>
 *   <li>every other {@code /api/finance/**} write (POST/PUT/PATCH/DELETE) — require {@code FINANCE_LEDGER_WRITE} or
 *       {@code finance.write}</li>
 *   <li>{@code /api/finance/**} reads (GET/HEAD) — require any ledger role, a finance scope, or the platform
 *       wildcard READ role</li>
 *   <li>everything else — denied</li>
 * </ul>
 *
 * <p><strong>TASK-MONO-794 (ADR-MONO-083 P7 · owner decisions Q1–Q2, 2026-10-16 UTC) — business roles.</strong> The
 * canonical table is finance {@code iam-integration.md} § 업무 역할. Before it, journal posting, FX override, period
 * close and discrepancy resolve were ONE write authority ({@code finance.write ∨ OPERATOR/ADMIN/SUPER_ADMIN/
 * FINANCE_OPERATOR}). Now:
 * <ul>
 *   <li><b>control writes (7)</b> — period open / close, FX rate override, reconciliation FX tolerance, the cost-flow
 *       default and per-account config (PUT · DELETE): {@code FINANCE_LEDGER_CONTROL} (Q1 — these change the outcome
 *       of every other write, so they are separable from day-to-day posting);</li>
 *   <li><b>business writes</b> — manual journal, settlement, revaluation, statement ingest, discrepancy resolve, FX
 *       refresh, and any write added later: {@code FINANCE_LEDGER_WRITE}. CONTROL does not open these and WRITE does
 *       not open the control writes (independent — Q2);</li>
 *   <li>the generic {@code OPERATOR/ADMIN/SUPER_ADMIN} strings (issued by nobody — Failure 1), {@code FINANCE_OPERATOR}
 *       (now the auth-derived entry role) and the entitlement-trust {@code ROLE_FINANCE_VIEWER} admit NOTHING.</li>
 * </ul>
 * ledger has no application-layer gate, so this chain is the sole authorization surface for its mutations. The control
 * matchers are declared first so a more specific path is decided before the generic write matcher.
 *
 * <p>Scope is enforced by value here (TASK-FIN-BE-047). The {@code SCOPE_*} authorities come from
 * {@link ActorContextJwtAuthenticationConverter}; the scope leg is what the workload client
 * {@code finance-platform-internal-services-client} rides (unchanged — {@code finance.write} opens every write).
 * Insufficient authority → 403 {@code PERMISSION_DENIED} via {@link SecurityErrorHandler#onAccessDenied}; no token →
 * 401.
 *
 * <p><strong>Platform super-admin wildcard READ authority (TASK-FIN-BE-049) — unchanged.</strong> The converter grants
 * {@link ActorContextJwtAuthenticationConverter#SUPERADMIN_READ_ROLE} keyed strictly on {@code tenant_id="*"}; it is in
 * {@code readAuthorities} only, so a wildcard token still 403s on every ledger mutation.
 *
 * Error handling (401/403 responses) is delegated to {@link SecurityErrorHandler}.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String SCOPE_READ = "SCOPE_finance.read";
    private static final String SCOPE_WRITE = "SCOPE_finance.write";

    /** TASK-MONO-794 — the ledger business roles (finance {@code iam-integration.md} § 업무 역할). */
    static final String ROLE_LEDGER_READ = "ROLE_FINANCE_LEDGER_READ";
    static final String ROLE_LEDGER_WRITE = "ROLE_FINANCE_LEDGER_WRITE";
    static final String ROLE_LEDGER_CONTROL = "ROLE_FINANCE_LEDGER_CONTROL";

    private static final String LEDGER = "/api/finance/ledger";

    private final SecurityErrorHandler securityErrorHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String[] exact = PublicPaths.EXACT.toArray(new String[0]);
        String[] prefixed = PublicPaths.PREFIXES.stream()
                .map(p -> p + "**")
                .toArray(String[]::new);
        // Control writes (owner decision Q1): the control role, or the workload scope.
        String[] controlAuthorities = {ROLE_LEDGER_CONTROL, SCOPE_WRITE};
        // Every other write: the ledger write role, or the workload scope. The platform-wildcard
        // ROLE_FINANCE_SUPERADMIN_READ is deliberately absent (TASK-FIN-BE-049 invariant: READ only).
        String[] writeAuthorities = {ROLE_LEDGER_WRITE, SCOPE_WRITE};
        // Reads: any ledger role (a control holder reads the settings and periods it changes — Q2), a finance scope,
        // or the platform super-admin wildcard READ role.
        String[] readAuthorities = {ROLE_LEDGER_READ, ROLE_LEDGER_WRITE, ROLE_LEDGER_CONTROL, SCOPE_READ, SCOPE_WRITE,
                ActorContextJwtAuthenticationConverter.SUPERADMIN_READ_ROLE};
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(exact).permitAll()
                        .requestMatchers(prefixed).permitAll()
                        // ── the 7 control writes (Q1) — before the generic write matchers ──
                        .requestMatchers(HttpMethod.POST, LEDGER + "/periods").hasAnyAuthority(controlAuthorities)
                        .requestMatchers(HttpMethod.POST, LEDGER + "/periods/*/close").hasAnyAuthority(controlAuthorities)
                        .requestMatchers(HttpMethod.PUT, LEDGER + "/settlements/fx-rate-override/*")
                                .hasAnyAuthority(controlAuthorities)
                        .requestMatchers(HttpMethod.PUT, LEDGER + "/reconciliation/fx-tolerance")
                                .hasAnyAuthority(controlAuthorities)
                        .requestMatchers(HttpMethod.PUT, LEDGER + "/settlements/cost-flow-config")
                                .hasAnyAuthority(controlAuthorities)
                        .requestMatchers(HttpMethod.PUT, LEDGER + "/settlements/cost-flow-config/accounts/*")
                                .hasAnyAuthority(controlAuthorities)
                        .requestMatchers(HttpMethod.DELETE, LEDGER + "/settlements/cost-flow-config/accounts/*")
                                .hasAnyAuthority(controlAuthorities)
                        // ── every other write ──
                        .requestMatchers(HttpMethod.POST, "/api/finance/**").hasAnyAuthority(writeAuthorities)
                        .requestMatchers(HttpMethod.PUT, "/api/finance/**").hasAnyAuthority(writeAuthorities)
                        .requestMatchers(HttpMethod.PATCH, "/api/finance/**").hasAnyAuthority(writeAuthorities)
                        .requestMatchers(HttpMethod.DELETE, "/api/finance/**").hasAnyAuthority(writeAuthorities)
                        .requestMatchers("/api/finance/**").hasAnyAuthority(readAuthorities)
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new ActorContextJwtAuthenticationConverter()))
                        .authenticationEntryPoint(securityErrorHandler::onAuthenticationFailure)
                        .accessDeniedHandler(securityErrorHandler::onAccessDenied)
                );
        return http.build();
    }
}
