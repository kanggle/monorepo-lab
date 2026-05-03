package com.example.fanplatform.community.infrastructure.membership;

import com.example.fanplatform.community.domain.membership.MembershipChecker;
import lombok.extern.slf4j.Slf4j;

/**
 * v1 default {@link MembershipChecker}: always allow + WARN.
 *
 * <p>Wired via {@link MembershipCheckerAutoConfig} with
 * {@code @ConditionalOnMissingBean} so a future membership-service consumer
 * replaces this stub transparently (TASK-FAN-BE-002 § Out of Scope —
 * membership-service v2). Logs a warning every call so the bypass is visible
 * in logs/observability.
 */
@Slf4j
public class AlwaysAllowMembershipChecker implements MembershipChecker {

    @Override
    public boolean hasAccess(String accountId, String tier, String tenantId) {
        // TODO(TASK-FAN-BE-MEMBERSHIP): replace with real membership-service call.
        log.warn("Membership gate bypassed (v1 stub): account={} tier={} tenant={}",
                accountId, tier, tenantId);
        return true;
    }
}
