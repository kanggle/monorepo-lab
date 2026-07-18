package com.example.finance.account.infrastructure.security;

import com.example.finance.account.presentation.security.PublicPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.stream.Stream;

/**
 * account-service Spring Security configuration.
 *
 * <ul>
 *   <li>{@code /actuator/{health,info,prometheus}} — public</li>
 *   <li>{@code /api/finance/**} writes (POST/PUT/PATCH/DELETE) — require {@code finance.write}
 *       scope (or an operator role, so the application-layer operator gate on /kyc/upgrade still
 *       governs rather than being shadowed here)</li>
 *   <li>{@code /api/finance/**} reads (GET/HEAD) — require {@code finance.read} or
 *       {@code finance.write} scope, or an operator role, or the entitlement-trust
 *       {@code ROLE_FINANCE_VIEWER} (so the platform-console operator read consumer keeps working,
 *       ADR-MONO-013, including the entitled-but-scopeless operator)</li>
 *   <li>everything else — denied</li>
 * </ul>
 *
 * <p>Scope is enforced by value here (TASK-FIN-BE-046): {@code iam-integration.md § Token 검증
 * 규칙 #5} declares the downstream service enforces {@code finance.read}/{@code finance.write}, but
 * this chain previously required only {@code .authenticated()} — a {@code finance.read}-only token
 * could perform every write. The {@code SCOPE_*} authorities come from
 * {@link ActorContextJwtAuthenticationConverter}. Insufficient scope for an authenticated caller →
 * 403 {@code PERMISSION_DENIED} via {@link SecurityErrorHandler#onAccessDenied}; no token → 401.
 *
 * <p>Read-OR-scope, not scope-only: a caller admitted by an operator role (no finance scope) is
 * still allowed, mirroring the gateway's {@code roleOrScope} admission and keeping the two layers
 * from disagreeing about validity.
 *
 * <p><strong>Entitlement-trust READ authority (TASK-FIN-BE-048, ADR-MONO-019 § D5 / ADR-MONO-020
 * D4 — the finance analogue of the WMS {@code ROLE_WMS_VIEWER} synthesis, TASK-MONO-162):</strong>
 * a finance-entitled token ({@code entitled_domains ∋ "finance"}) that carries no finance scope and
 * no operator role — the base OIDC domain-facing token the platform console federates for an
 * entitled customer operator — is granted {@link ActorContextJwtAuthenticationConverter#VIEWER_ROLE}
 * by the converter, so its READS pass this gate. This is <em>read-visibility only</em>:
 * {@code VIEWER_ROLE} is in {@code readAuthorities} but NOT {@code writeAuthorities}, so such a token
 * still 403s on every write. Finance had applied entitlement-trust at the tenant layer only
 * (TASK-FIN-BE-006 pilot); when the read-scope hardening landed (FIN-BE-046) the entitled operator
 * became a straggler — 403 at this authorization layer despite passing layer-1. This closes it.
 *
 * <p><strong>Platform super-admin wildcard READ authority (TASK-FIN-BE-049, ADR-MONO-019 § D5 —
 * the authority-layer analogue of the tenant gate's {@code allowSuperAdminWildcard()}):</strong>
 * a platform super-admin's base OIDC domain-facing token carries {@code tenant_id="*"} but no
 * finance scope and no domain role — per ADR-033 S2 / ADR-034 U5 the admin plane's
 * {@code SUPER_ADMIN} is deliberately kept OFF the domain-facing token, so the wildcard cannot be
 * minted into a {@code ROLE_SUPER_ADMIN} at auth-service without breaching that plane disjointness.
 * The converter grants {@link ActorContextJwtAuthenticationConverter#SUPERADMIN_READ_ROLE} keyed
 * strictly on {@code tenant_id="*"}, so its READS pass this gate. This is <em>read-visibility
 * only</em>: {@code SUPERADMIN_READ_ROLE} is in {@code readAuthorities} but NOT
 * {@code writeAuthorities}, so a wildcard token still 403s on every write. The tenant gate opened
 * the wildcard (FIN-BE-006 pilot); when the read-scope hardening landed (FIN-BE-046) the wildcard
 * super-admin became the read straggler one axis over from the entitlement straggler FIN-BE-048
 * closed — runtime-confirmed by the console super-admin persona (nightly-e2e run 29635409302:
 * finance overview card forbidden, reason PERMISSION_DENIED). This closes it.
 *
 * No public webhook surface in v1 (finance has no external caller).
 * Error handling (401/403 responses) is delegated to {@link SecurityErrorHandler}.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String SCOPE_READ = "SCOPE_finance.read";
    private static final String SCOPE_WRITE = "SCOPE_finance.write";

    /**
     * Roles that admit a caller regardless of scope — the operator/admin identities the
     * application layer already recognises ({@link com.example.finance.account.application.ActorContext#isOperator()}).
     * Kept in sync with that method so a role-bearing operator token is never blocked here before
     * the application-layer operator check runs.
     */
    private static final String[] OPERATOR_AUTHORITIES = {
            "ROLE_OPERATOR", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_FINANCE_OPERATOR"
    };

    private static String[] withOperators(String... scopes) {
        return Stream.concat(Stream.of(scopes), Stream.of(OPERATOR_AUTHORITIES))
                .toArray(String[]::new);
    }

    private final SecurityErrorHandler securityErrorHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String[] exact = PublicPaths.EXACT.toArray(new String[0]);
        String[] prefixed = PublicPaths.PREFIXES.stream()
                .map(p -> p + "**")
                .toArray(String[]::new);
        // Writes: scope finance.write or an operator role ONLY — the entitlement-trust
        // ROLE_FINANCE_VIEWER and the platform-wildcard ROLE_FINANCE_SUPERADMIN_READ are deliberately
        // absent, so neither an entitled-but-scopeless token nor a bare super-admin wildcard token can
        // ever mutate (TASK-FIN-BE-048/049 invariant: both widen READ visibility only).
        String[] writeAuthorities = withOperators(SCOPE_WRITE);
        // Reads: scope finance.read|write, an operator role, the entitlement-trust VIEWER role, or the
        // platform super-admin wildcard READ role (TASK-FIN-BE-049).
        String[] readAuthorities = withOperators(
                SCOPE_READ, SCOPE_WRITE,
                ActorContextJwtAuthenticationConverter.VIEWER_ROLE,
                ActorContextJwtAuthenticationConverter.SUPERADMIN_READ_ROLE);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(exact).permitAll()
                        .requestMatchers(prefixed).permitAll()
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
