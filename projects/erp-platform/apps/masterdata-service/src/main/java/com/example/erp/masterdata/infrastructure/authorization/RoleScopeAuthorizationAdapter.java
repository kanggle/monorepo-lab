package com.example.erp.masterdata.infrastructure.authorization;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.port.outbound.AuthorizationPort;
import com.example.erp.masterdata.domain.authorization.AuthorizationDecision;
import com.example.erp.masterdata.domain.authorization.RequiredScope;
import org.springframework.stereotype.Component;

/**
 * v1 authorization adapter — derives role + data-scope from JWT claims
 * (architecture.md § Authorization matrix + Data scope, erp E6).
 *
 * <p><b>Fail-CLOSED defaults</b>:
 * <ul>
 *   <li>missing role-set / no recognised erp scope → {@code DENY_ROLE} →
 *       {@code PERMISSION_DENIED} (403).</li>
 *   <li>target department outside the actor's data-scope subtree →
 *       {@code DENY_SCOPE} → {@code DATA_SCOPE_FORBIDDEN} (403).</li>
 * </ul>
 *
 * <p>v2 will swap this for a {@code permission-service} client behind the
 * same port without touching the application layer.
 */
@Component
public class RoleScopeAuthorizationAdapter implements AuthorizationPort {

    private static final String SCOPE_READ = "erp.read";
    private static final String SCOPE_WRITE = "erp.write";

    @Override
    public AuthorizationDecision evaluate(ActorContext actor, RequiredScope required,
                                          String targetDepartmentId) {
        if (actor == null || actor.roles() == null) {
            return AuthorizationDecision.denyRole("no roles claim");
        }
        // Role check
        boolean roleOk = switch (required) {
            case READ -> actor.hasScope(SCOPE_READ) || actor.hasScope(SCOPE_WRITE)
                    || actor.isOperator();
            case WRITE -> actor.hasScope(SCOPE_WRITE) || actor.isOperator();
        };
        if (!roleOk) {
            return AuthorizationDecision.denyRole(
                    "actor lacks required scope " + required.name().toLowerCase());
        }
        // Data-scope check (only if a target is supplied)
        if (targetDepartmentId == null) {
            return AuthorizationDecision.allow();
        }
        if (actor.isPlatformScope()) {
            return AuthorizationDecision.allow();
        }
        if (actor.dataScopeDepartmentIds() == null
                || actor.dataScopeDepartmentIds().isEmpty()) {
            // No explicit scope → fail-CLOSED for human operators. (Machine
            // tokens land here only if they failed to map to "*" — explicit
            // deny.)
            return AuthorizationDecision.denyScope(
                    "actor has no data-scope and target is non-null");
        }
        if (!actor.dataScopeDepartmentIds().contains(targetDepartmentId)) {
            return AuthorizationDecision.denyScope(
                    "target department " + targetDepartmentId + " outside actor scope");
        }
        return AuthorizationDecision.allow();
    }
}
