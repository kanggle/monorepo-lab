package com.example.fanplatform.community.infrastructure.membership;

import com.example.fanplatform.community.domain.membership.MembershipChecker;
import lombok.extern.slf4j.Slf4j;

/**
 * Inert structural fallback {@link MembershipChecker}.
 *
 * <p>As of TASK-FAN-BE-010 the production checker is {@link HttpMembershipChecker}
 * (membership-service is live). This stub is retained ONLY as the
 * {@code @ConditionalOnMissingBean} fallback in {@link MembershipCheckerAutoConfig}
 * (declared after the real bean, so production NEVER selects it — see that class's
 * ordering note). It exists purely as an escape hatch (e.g. a profile that
 * excludes the HTTP bean). It logs a WARN every call so that, if it were ever
 * selected, the fail-open bypass is loud in logs/observability.
 */
@Slf4j
public class AlwaysAllowMembershipChecker implements MembershipChecker {

    @Override
    public boolean hasAccess(String accountId, String tier, String tenantId) {
        log.warn("Membership gate bypassed (inert fallback stub selected — "
                + "HttpMembershipChecker absent): account={} tier={} tenant={}",
                accountId, tier, tenantId);
        return true;
    }
}
