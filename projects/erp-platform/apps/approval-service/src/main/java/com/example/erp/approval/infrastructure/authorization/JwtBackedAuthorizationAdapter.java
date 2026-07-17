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
 *
 * <p><b>Data-scope (subject owning-department subtree) is a v2 concern</b> (TASK-ERP-BE-030):
 * v1 enforces role/scope only. Confining a write to the subject's owning-department subtree is
 * deferred to the v2 {@code permission-service} — it requires an owning-department resolution the
 * JWT claims do not carry. The {@code targetDepartmentId} parameter is reserved for that v2 client.
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
        // v1 (TASK-ERP-BE-030): the JWT-backed adapter enforces role/scope ONLY. Subject
        // data-scope confinement (denying a caller whose scope does not contain the subject's
        // owning-department subtree) is NOT enforced in v1 and is deferred to the v2
        // permission-service — this port is that swap point, and {@code targetDepartmentId} is
        // reserved for the v2 client. The earlier in-adapter attempt was removed here because it
        // was BOTH structurally unreachable (every call site passes {@code targetDepartmentId=null};
        // the subject's owning department is never resolved) AND fail-OPEN on an absent data scope
        // (it fell through to allow when {@code dataScopeDepartmentIds()} was null) — a control that
        // could never fire and, if reached, would fail open. Separation-of-Duties (approver
        // eligibility) remains enforced as a domain guard on the aggregate, independent of this port.
        return AuthorizationDecision.allow();
    }
}
