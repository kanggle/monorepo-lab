package com.example.finance.account.infrastructure.security;

import com.example.finance.account.presentation.security.PublicPaths;
import com.example.security.servlet.FirewallRejections;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.RequestRejectedHandler;

/**
 * account-service Spring Security configuration.
 *
 * <ul>
 *   <li>{@code /actuator/{health,info,prometheus}} — public</li>
 *   <li>{@code /api/finance/**} writes (POST/PUT/PATCH/DELETE) — require the {@code FINANCE_ACCOUNT_WRITE} role or
 *       the workload scope {@code finance.write}</li>
 *   <li>{@code /api/finance/**} reads (GET/HEAD) — require {@code FINANCE_ACCOUNT_READ} or
 *       {@code FINANCE_ACCOUNT_WRITE}, a finance scope, or the platform wildcard READ role</li>
 *   <li>everything else — denied</li>
 * </ul>
 *
 * <p><strong>TASK-MONO-794 (ADR-MONO-083 P7 · owner decisions Q2–Q3, 2026-10-16 UTC) — business roles.</strong> The
 * canonical table is finance {@code iam-integration.md} § 업무 역할. Two former admissions are GONE:
 * <ul>
 *   <li>the operator roles {@code OPERATOR/ADMIN/SUPER_ADMIN/FINANCE_OPERATOR} — the three generic strings are issued
 *       by nobody (auth-service keeps them only on its workload deny-list), so leaving them here would let any token
 *       that got the string some other way open every account route (Failure 1); {@code FINANCE_OPERATOR} is now the
 *       entry role auth-service DERIVES from any Finance business role, ledger-only operators included;</li>
 *   <li>the entitlement-trust {@code ROLE_FINANCE_VIEWER} (TASK-FIN-BE-048) — with it a subscription alone opened every
 *       read and a read-only business set could never be told apart from «no set» (the 793 Q1 analogue).</li>
 * </ul>
 * KYC upgrade and top-up additionally require the account write ROLE at the application layer
 * ({@code AccountApplicationService}) — a scope-only workload token passes this chain and is refused there.
 *
 * <p>Scope is enforced by value here (TASK-FIN-BE-046): {@code iam-integration.md § Token 검증 규칙 #5} — the
 * {@code SCOPE_*} authorities come from {@link ActorContextJwtAuthenticationConverter}. Insufficient authority for an
 * authenticated caller → 403 {@code PERMISSION_DENIED} via {@link SecurityErrorHandler#onAccessDenied}; no token → 401.
 * The scope leg is what the workload client {@code finance-platform-internal-services-client} rides (unchanged).
 *
 * <p><strong>Platform super-admin wildcard READ authority (TASK-FIN-BE-049, ADR-MONO-019 § D5) — unchanged.</strong>
 * A platform super-admin's base OIDC domain-facing token carries {@code tenant_id="*"} but no finance scope and no
 * domain role; the converter grants {@link ActorContextJwtAuthenticationConverter#SUPERADMIN_READ_ROLE} keyed strictly
 * on {@code tenant_id="*"}, so its READS pass. It is in {@code readAuthorities} only — a wildcard token still 403s on
 * every write.
 *
 * <p>No public webhook surface in v1 (finance has no external caller).
 * Error handling (401/403 responses) is delegated to {@link SecurityErrorHandler}.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String SCOPE_READ = "SCOPE_finance.read";
    private static final String SCOPE_WRITE = "SCOPE_finance.write";

    /** TASK-MONO-794 — the account business roles (finance {@code iam-integration.md} § 업무 역할). */
    static final String ROLE_ACCOUNT_READ = "ROLE_FINANCE_ACCOUNT_READ";
    static final String ROLE_ACCOUNT_WRITE = "ROLE_FINANCE_ACCOUNT_WRITE";

    private final SecurityErrorHandler securityErrorHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String[] exact = PublicPaths.EXACT.toArray(new String[0]);
        String[] prefixed = PublicPaths.PREFIXES.stream()
                .map(p -> p + "**")
                .toArray(String[]::new);
        // Writes: the account write role, or the workload scope finance.write. The platform-wildcard
        // ROLE_FINANCE_SUPERADMIN_READ is deliberately absent (TASK-FIN-BE-049 invariant: READ only).
        String[] writeAuthorities = {ROLE_ACCOUNT_WRITE, SCOPE_WRITE};
        // Reads: the account read or write role, a finance scope, or the platform super-admin wildcard READ role.
        String[] readAuthorities = {ROLE_ACCOUNT_READ, ROLE_ACCOUNT_WRITE, SCOPE_READ, SCOPE_WRITE,
                ActorContextJwtAuthenticationConverter.SUPERADMIN_READ_ROLE};
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

    // TASK-MONO-801: a path the firewall rejects up front → 400 BAD_REQUEST here, not denyAll's 401 on /error.
    @Bean
    public RequestRejectedHandler requestRejectedHandler() {
        return FirewallRejections.handler();
    }
}
