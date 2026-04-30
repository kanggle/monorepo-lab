package com.example.admin.presentation.aspect;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.exception.PermissionDeniedException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralised RBAC enforcement for admin-service controllers.
 *
 * <p><b>Annotation path.</b> Methods bearing {@link RequiresPermission} are
 * gated against {@link PermissionEvaluator}; denial writes a DENIED
 * {@code admin_actions} row and emits the canonical outbox event before
 * throwing {@link PermissionDeniedException}.
 *
 * <p><b>Deny-by-default guardrail.</b> Controller mutation methods
 * ({@code @PostMapping}/{@code @PutMapping}/{@code @PatchMapping}/{@code @DeleteMapping})
 * that are NOT annotated with {@code @RequiresPermission} are rejected at
 * runtime with {@code permission_used="<missing>"} (rbac.md D2). GET
 * endpoints are out of scope for this guardrail.
 *
 * <p><b>Ordering.</b> Spring Security's {@code MethodSecurityInterceptor}
 * runs at order {@code 0} (default). We deliberately run at {@code 200} so
 * any future method-security advisor executes first and our aspect owns the
 * final audit write. Spring Security method-level authorization is not used
 * in this service; see {@code SecurityConfig} — {@code @EnableMethodSecurity}
 * is intentionally absent so there is a single authorization decision site.
 */
@Slf4j
@Aspect
@Component
@Order(200)
@RequiredArgsConstructor
public class RequiresPermissionAspect {

    private final PermissionEvaluator permissionEvaluator;
    private final AdminActionAuditor auditor;

    /** Annotated path: explicit permission requirement. */
    @Around("@annotation(requires)")
    public Object checkAnnotated(ProceedingJoinPoint pjp, RequiresPermission requires) throws Throwable {
        List<String> required = collectRequired(requires);
        OperatorContext op = OperatorContextHolder.require();
        String operatorId = op.operatorId();

        boolean granted = required.size() == 1
                ? permissionEvaluator.hasPermission(operatorId, required.get(0))
                : permissionEvaluator.hasAllPermissions(operatorId, required);

        if (!granted) {
            String permissionUsed = joinKeys(required);
            HttpServletRequest request = currentRequest();
            String endpoint = request != null ? request.getRequestURI() : null;
            String method = request != null ? request.getMethod() : null;
            ActionCode actionCode = actionCodeForMethod(pjp);
            auditor.recordDenied(actionCode, permissionUsed, endpoint, method, null);
            throw new PermissionDeniedException(
                    "Operator lacks required permission: " + permissionUsed);
        }

        return pjp.proceed();
    }

    /**
     * Deny-by-default guardrail: any mutation mapping (POST/PUT/PATCH/DELETE)
     * under an admin controller that lacks {@code @RequiresPermission} is
     * treated as unannotated and rejected. GET endpoints are NOT gated here.
     */
    @Around("within(com.example.admin.presentation..*)"
            // Exclude the unauthenticated sub-tree (admin-api.md Authentication
            // Exceptions): WellKnownController (JWKS) and AdminAuthController
            // (login, 2FA enroll/verify). These paths sit before operator JWT
            // issuance and must not be gated by the RBAC aspect.
            + " && !within(com.example.admin.presentation.WellKnownController)"
            + " && !within(com.example.admin.presentation.AdminAuthController)"
            + " && ("
            + " @annotation(org.springframework.web.bind.annotation.PostMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.PutMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.PatchMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public Object denyUnannotatedMutation(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        if (method.isAnnotationPresent(RequiresPermission.class)
                || method.isAnnotationPresent(SelfServiceEndpoint.class)) {
            // Annotated path handles itself; self-service endpoints are JWT-only.
            return pjp.proceed();
        }
        HttpServletRequest request = currentRequest();
        String endpoint = request != null ? request.getRequestURI() : null;
        String httpMethod = request != null ? request.getMethod() : null;
        ActionCode actionCode = actionCodeForMethod(pjp);
        auditor.recordDenied(actionCode, Permission.MISSING, endpoint, httpMethod, null);
        throw new PermissionDeniedException(
                "Mutation endpoint missing @RequiresPermission declaration");
    }

    private static List<String> collectRequired(RequiresPermission a) {
        List<String> keys = new ArrayList<>();
        if (a.value() != null && !a.value().isBlank()) {
            keys.add(a.value());
        }
        for (String k : a.allOf()) {
            if (k != null && !k.isBlank() && !keys.contains(k)) {
                keys.add(k);
            }
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException(
                    "@RequiresPermission must declare at least one key (value or allOf)");
        }
        return keys;
    }

    private static String joinKeys(List<String> keys) {
        return keys.size() == 1 ? keys.get(0) : String.join("+", keys);
    }

    /**
     * Best-effort actionCode derivation from the joined method — the
     * auditor maps this to the target_type. When unrecognised returns null,
     * which the auditor widens to {@code "UNKNOWN"}.
     */
    private static ActionCode actionCodeForMethod(ProceedingJoinPoint pjp) {
        Method m = ((MethodSignature) pjp.getSignature()).getMethod();
        String simple = m.getDeclaringClass().getSimpleName();
        String name = m.getName();
        if ("AccountAdminController".equals(simple)) {
            if ("lock".equals(name)) return ActionCode.ACCOUNT_LOCK;
            if ("unlock".equals(name)) return ActionCode.ACCOUNT_UNLOCK;
            if ("bulkLock".equals(name)) return ActionCode.ACCOUNT_LOCK;
        }
        if ("SessionAdminController".equals(simple)) return ActionCode.SESSION_REVOKE;
        if ("AuditController".equals(simple)) return ActionCode.AUDIT_QUERY;
        // TASK-BE-083 — operator management controller; each endpoint maps to a
        // dedicated action code so DENIED rows carry the correct target semantics.
        if ("OperatorAdminController".equals(simple)) {
            if ("createOperator".equals(name)) return ActionCode.OPERATOR_CREATE;
            if ("patchRoles".equals(name)) return ActionCode.OPERATOR_ROLE_CHANGE;
            if ("patchStatus".equals(name)) return ActionCode.OPERATOR_STATUS_CHANGE;
            // listOperators / currentOperator are reads; fall through to null
        }
        // Fallback for deny-by-default on unknown mutation endpoints.
        if (isMutation(m)) return null;
        return null;
    }

    private static boolean isMutation(Method m) {
        for (Class<? extends Annotation> a : List.of(
                PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class)) {
            if (m.isAnnotationPresent(a)) return true;
        }
        return false;
    }

    private static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (IllegalStateException ex) {
            return null;
        }
    }
}
