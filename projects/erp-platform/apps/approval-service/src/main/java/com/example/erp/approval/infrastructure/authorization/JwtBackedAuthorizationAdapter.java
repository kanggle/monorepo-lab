package com.example.erp.approval.infrastructure.authorization;

import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.application.port.outbound.AuthorizationPort;
import com.example.erp.approval.domain.authorization.AuthorizationDecision;
import com.example.erp.approval.domain.authorization.RequiredScope;
import org.springframework.stereotype.Component;

/**
 * JWT-claim-backed authorization adapter (E6, fail-CLOSED). Maps the actor's
 * scope/role claims to a READ/WRITE decision. The application service short-
 * circuits the common scope-bearing / operator / entitlement cases before
 * calling this port; this adapter is the authoritative fail-closed evaluator
 * for the remaining cases (and the swap-point for a v2 {@code permission-service}
 * client). The approval-specific approver-eligibility check is a SEPARATE
 * domain guard on the aggregate (Separation of Duties — I4).
 *
 * <p><b>Fail-CLOSED default</b>: an actor with no recognised role/scope is
 * DENY_ROLE. There is no allow-by-default codepath.
 */
@Component
public class JwtBackedAuthorizationAdapter implements AuthorizationPort {

    @Override
    public AuthorizationDecision evaluate(ActorContext actor, RequiredScope required,
                                          String targetDepartmentId) {
        boolean roleOk = switch (required) {
            case READ -> actor.canReadErp();
            case WRITE -> actor.canWriteErp();
        };
        if (!roleOk) {
            return AuthorizationDecision.denyRole(
                    "actor lacks the required erp " + required + " role/scope");
        }
        // Data-scope: platform/operator scope sees everything; otherwise a
        // non-null target outside the actor's department subtree is denied.
        if (targetDepartmentId != null
                && !actor.isPlatformScope()
                && !actor.isOperator()
                && actor.dataScopeDepartmentIds() != null
                && !actor.dataScopeDepartmentIds().contains(targetDepartmentId)) {
            return AuthorizationDecision.denyScope(
                    "target department '" + targetDepartmentId + "' outside actor data scope");
        }
        return AuthorizationDecision.allow();
    }
}
