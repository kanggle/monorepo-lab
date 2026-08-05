package com.example.user.presentation.filter;

import com.example.user.application.service.UserProfileProvisioner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Establishes "a profile exists for the caller" as an invariant of the self-service
 * surface, instead of a precondition each endpoint has to remember (TASK-BE-575).
 *
 * <h2>Why a filter and not four call sites</h2>
 *
 * <p>Four endpoints presume the row today ({@code GET|PATCH /api/users/me},
 * {@code POST /api/wishlists}, and the address writes via their FK). Spreading the
 * provisioning call across them makes the property something a fifth endpoint can be
 * added without — which is precisely the drift this repository keeps paying for. Put
 * at the boundary, the property holds for every self-service request that has ever
 * been written and every one that will be.
 *
 * <h2>What is deliberately excluded</h2>
 *
 * <p>{@code /api/admin/**} — the operator plane. An operator's {@code X-User-Id} is an
 * IAM subject like any other, so provisioning here would mint a <em>consumer</em>
 * profile for every operator who touched the admin API and inflate the very user list
 * they are looking at. The operator plane reads profiles; it is not one.
 *
 * <p>Requests without {@code X-User-Id} are also skipped: no verified subject, nothing
 * to project. (user-service runs no Spring Security chain — it trusts the
 * gateway-injected identity headers, which {@code IdentityHeaderStripFilter} guarantees
 * are not client-supplied.)
 *
 * <h2>Ordering</h2>
 *
 * <p>Runs immediately after {@link TenantContextFilter}, because the row's
 * {@code tenant_id} comes from {@code TenantContext} (via {@code UserProfileJpaMapper})
 * and the tenant-scoped read that decides whether to create one does too. Provisioning
 * before the tenant is bound would file the profile under the default tenant.
 *
 * <p>It is registered by {@code UserProfileProvisioningFilterConfig} rather than annotated
 * {@code @Component}, and that is not a style choice. {@code @WebMvcTest} pulls every
 * {@code Filter} bean into its slice; a scanned filter would drag
 * {@link UserProfileProvisioner} and the JPA repository behind it into four controller
 * slices that have no persistence layer, and every one of them fails to load its context.
 * (Measured: 58 failures across the slice and contract tests.) A {@code FilterRegistrationBean}
 * in a {@code @Configuration} is invisible to the slice and explicit about its order.
 *
 * <h2>Failure is not the request's failure</h2>
 *
 * <p>A provisioning error is logged and swallowed: the endpoint then behaves exactly as
 * it did before this filter existed (404 {@code USER_PROFILE_NOT_FOUND}), which is a
 * worse outcome for the user but an honest one, and strictly better than turning a
 * readable 404 into a 500 from a filter they never asked to run.
 */
@Slf4j
@RequiredArgsConstructor
public class UserProfileProvisioningFilter extends OncePerRequestFilter {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String OPERATOR_PATH_PREFIX = "/api/admin/";

    private final UserProfileProvisioner provisioner;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        UUID userId = parseUserId(request.getHeader(USER_ID_HEADER));
        if (userId != null) {
            try {
                provisioner.ensureProvisioned(userId, request.getHeader(USER_EMAIL_HEADER));
            } catch (RuntimeException e) {
                log.warn("Provisioning failed for userId={} on {} — continuing; the endpoint will "
                        + "answer as it did before provisioning existed", userId, request.getRequestURI(), e);
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * The operator plane is not a self-service surface — see the class javadoc. Also
     * skips actuator and anything else outside {@code /api/**}: no profile is implied.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/") || path.startsWith(OPERATOR_PATH_PREFIX);
    }

    /**
     * A malformed {@code X-User-Id} is not this filter's error to raise — the controller's
     * {@code @RequestHeader UUID} binding will reject it with the response it always has.
     */
    private static UUID parseUserId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
