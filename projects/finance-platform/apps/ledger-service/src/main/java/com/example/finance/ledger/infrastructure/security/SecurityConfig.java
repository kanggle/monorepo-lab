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

import java.util.stream.Stream;

/**
 * ledger-service Spring Security configuration.
 *
 * <ul>
 *   <li>{@code /actuator/{health,info,prometheus}} — public</li>
 *   <li>{@code /api/finance/**} writes (POST/PUT/PATCH/DELETE) — require {@code finance.write}
 *       scope (or an operator role, for gateway parity)</li>
 *   <li>{@code /api/finance/**} reads (GET/HEAD) — require {@code finance.read} or
 *       {@code finance.write} scope (or an operator role, so the platform-console operator read
 *       consumer keeps working, ADR-MONO-013)</li>
 *   <li>everything else — denied</li>
 * </ul>
 *
 * <p>Scope is enforced by value here (TASK-FIN-BE-047): {@code iam-integration.md § Token 검증
 * 규칙 #5} declares every downstream finance service enforces {@code finance.read}/{@code finance.write},
 * but this ledger chain previously required only {@code .authenticated()} — a {@code finance.read}-only
 * token could drive every ledger mutation (post journal entries, override FX rates, close accounting
 * periods, resolve reconciliation discrepancies). This is the ledger sibling of the account-service
 * fix (FIN-BE-046); the fix had not propagated here (the stale "read-only API" javadoc predated the
 * ~12 mutating endpoints this service since grew). The {@code SCOPE_*} authorities come from
 * {@link ActorContextJwtAuthenticationConverter}. Insufficient scope for an authenticated caller →
 * 403 {@code PERMISSION_DENIED} via {@link SecurityErrorHandler#onAccessDenied}; no token → 401.
 *
 * <p>Read-OR-scope, not scope-only: a caller admitted by an operator role (no finance scope) is
 * still allowed, mirroring the gateway's {@code roleOrScope} admission and account-service, so the
 * two layers do not disagree about validity. ledger has no application-layer operator gate, so this
 * chain is the sole authorization surface for the mutating endpoints.
 *
 * Error handling (401/403 responses) is delegated to {@link SecurityErrorHandler}.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String SCOPE_READ = "SCOPE_finance.read";
    private static final String SCOPE_WRITE = "SCOPE_finance.write";

    /**
     * Roles that admit a caller regardless of scope — the operator/admin identities recognised
     * across the finance platform. Kept in sync with account-service's {@code OPERATOR_AUTHORITIES}
     * so a role-bearing operator token admitted at the gateway is never blocked here.
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
        String[] writeAuthorities = withOperators(SCOPE_WRITE);
        String[] readAuthorities = withOperators(SCOPE_READ, SCOPE_WRITE);
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
