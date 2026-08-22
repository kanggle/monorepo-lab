package com.example.auth.infrastructure.tenant;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.TenantSignupEligibilityPort;
import com.example.auth.domain.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TASK-BE-581 — cache-first implementation of {@link TenantSignupEligibilityPort}.
 *
 * <p>Mirrors the {@link TenantTypeResolver} arrangement deliberately (same package, same
 * cache-positives-only policy, same {@link AccountServicePort} dependency) so the two
 * readers of {@code GET /internal/tenants/{tenantId}} stay recognisably one pattern.
 *
 * <h2>Caching</h2>
 * Only <b>eligible</b> answers are cached. An ineligible or unknown tenant is re-asked every
 * time, because a tenant may be provisioned or un-suspended later and a cached "no" would
 * outlive the fix — the same rationale {@link TenantTypeResolver} states for not caching
 * its 404s. The cost is one lookup per render of a login page whose client points at an
 * ineligible tenant; the console is an operator surface, not a hot path.
 *
 * <h2>🔴 Outage policy: fail OPEN, and that is not the usual direction</h2>
 * When account-service cannot answer, this resolver reports <b>eligible</b>. That is the
 * opposite of the fail-closed rule the token-issuance paths follow, and the difference is
 * the consequence, not the confidence:
 * <ul>
 *   <li>Failing <i>closed</i> would hide the signup link from <b>every</b> consumer surface
 *       (fan-platform, ecommerce, …) for the duration of an account-service outage. That is
 *       a silent revert of TASK-BE-470 on the general user path — the exact regression
 *       TASK-BE-581's acceptance criteria hold control cells for.</li>
 *   <li>Failing <i>open</i> costs a user who clicks through during an outage one honest
 *       "temporarily unavailable" message, which is what an outage <i>is</i>. No account is
 *       created that {@code ActiveTenantGuard} would have refused — this gate cannot admit
 *       anything, only decline to offer.</li>
 * </ul>
 * Read the safe side as "the surface must not silently lose a working feature", not as
 * "always deny". Nothing security-relevant rides on this answer.
 */
@Slf4j
@Component
public class TenantSignupEligibilityResolver implements TenantSignupEligibilityPort {

    /** account-service {@code tenants.status} value that {@code ActiveTenantGuard} admits. */
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AccountServicePort accountServicePort;

    /** tenantId -> eligible. Positive answers only (see class javadoc). */
    private final Map<String, Boolean> eligibleCache = new ConcurrentHashMap<>();

    public TenantSignupEligibilityResolver(AccountServicePort accountServicePort) {
        this.accountServicePort = accountServicePort;
    }

    @Override
    public boolean isSignupOffered(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            // SavedRequestTenantResolver never yields blank — it falls back to the default
            // tenant. Treat a blank as "no client-derived constraint" and offer signup, which
            // is the pre-BE-581 behaviour for an unresolvable client.
            return true;
        }
        // The ADR-002 platform-scope sentinel is not a tenant and has no tenants row; nobody
        // can be born into it. TenantTypeResolver short-circuits it for the same reason.
        if (TenantContext.PLATFORM_SCOPE_TENANT_ID.equals(tenantId)) {
            return false;
        }
        if (Boolean.TRUE.equals(eligibleCache.get(tenantId))) {
            return true;
        }
        try {
            Optional<AccountServicePort.TenantLookupResult> tenant =
                    accountServicePort.getTenant(tenantId);
            if (tenant.isEmpty()) {
                // 404 — no such tenant. This is the console's `iam` case: structural, permanent.
                log.debug("signup not offered: tenantId={} has no tenants row", tenantId);
                return false;
            }
            String status = tenant.get().status();
            if (!STATUS_ACTIVE.equalsIgnoreCase(status)) {
                // ActiveTenantGuard would throw TenantSuspendedException (403), not a 404.
                log.debug("signup not offered: tenantId={} status={}", tenantId, status);
                return false;
            }
            eligibleCache.put(tenantId, Boolean.TRUE);
            return true;
        } catch (AccountServiceUnavailableException e) {
            // Fail OPEN — see class javadoc. Logged at warn because a persistent outage means
            // this gate is not actually gating anything.
            log.warn("tenant signup-eligibility unknown for tenantId={} (account-service "
                    + "unavailable); offering signup so consumer surfaces do not lose it", tenantId);
            return true;
        }
    }

    /** Test seam — lets a test observe that a positive answer was memoised. */
    Set<String> cachedEligibleTenants() {
        return Set.copyOf(eligibleCache.keySet());
    }
}
