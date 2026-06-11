package com.example.admin.presentation.aspect;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.exception.AccessConditionUnmetException;
import com.example.admin.application.exception.PermissionDeniedException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.security.access.SourceIpCondition;
import com.example.security.access.TimeWindowCondition;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
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
import java.time.Clock;
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
    /**
     * ADR-MONO-026 / ADR-MONO-028 4th gate — the access conditions (optional/opt-in),
     * composed AND-only. When a provider has no bean (slice tests, or the service
     * started before the config is wired) that condition is net-zero. Each bean
     * defaults to empty config ({@code isConfigured()} false), also net-zero.
     */
    private final ObjectProvider<SourceIpCondition> sourceIpConditionProvider;
    /** ADR-MONO-028 — the TIME_WINDOW condition, composed AND-only with SOURCE_IP. */
    private final ObjectProvider<TimeWindowCondition> timeWindowConditionProvider;
    /**
     * The request clock for TIME_WINDOW. Resolves a uniquely-defined {@code Clock}
     * bean if present (slice tests inject a fixed one), else {@code Clock.systemUTC()}.
     */
    private final ObjectProvider<Clock> clockProvider;

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

        // ADR-MONO-026 / ADR-MONO-028 (axis ② 2단계) — the 4th authorization gate:
        // the access conditions (SOURCE_IP + TIME_WINDOW), composed AND-only.
        // Restriction-only (runs only AFTER RBAC granted) + fail-safe (unresolvable
        // input denies) + net-zero (each condition is skipped when unconfigured).
        // Mutation-only: GET reads are never gated.
        Method joined = ((MethodSignature) pjp.getSignature()).getMethod();
        if (isMutation(joined)) {
            HttpServletRequest request = currentRequest();
            if (anyConditionUnmet(request)) {
                String endpoint = request != null ? request.getRequestURI() : null;
                String method = request != null ? request.getMethod() : null;
                ActionCode actionCode = actionCodeForMethod(pjp);
                auditor.recordDenied(actionCode, joinKeys(required), endpoint, method, null);
                throw new AccessConditionUnmetException(
                        "Request does not satisfy a configured access condition");
            }
        }

        return pjp.proceed();
    }

    /**
     * AND-only composition of the configured access conditions (ADR-026 SOURCE_IP +
     * ADR-028 TIME_WINDOW). Returns {@code true} iff ANY <i>configured</i> condition
     * is unsatisfied for this request — an unconfigured condition is skipped
     * (net-zero), so the gate degrades cleanly to whichever conditions are
     * configured. Fail-safe: an unresolvable input denies (the evaluators return
     * {@code false} on bad input).
     */
    private boolean anyConditionUnmet(HttpServletRequest request) {
        SourceIpCondition sourceIp = sourceIpConditionProvider.getIfAvailable();
        if (sourceIp != null && sourceIp.isConfigured()
                && !sourceIp.isSatisfiedBy(resolveSourceIp(request))) {
            return true;
        }
        TimeWindowCondition timeWindow = timeWindowConditionProvider.getIfAvailable();
        if (timeWindow != null && timeWindow.isConfigured()
                && !timeWindow.isSatisfiedBy(currentClock().instant())) {
            return true;
        }
        return false;
    }

    /**
     * The request clock for the TIME_WINDOW condition — a uniquely-defined
     * {@code Clock} bean if present (slice tests inject a fixed one), else the
     * system UTC clock. {@code getIfUnique} avoids any ambiguity if the context
     * happens to hold more than one {@code Clock}.
     */
    private Clock currentClock() {
        return clockProvider.getIfUnique(Clock::systemUTC);
    }

    /**
     * Resolve the request's source IP for the SOURCE_IP condition: the first hop
     * of {@code X-Forwarded-For} (the real client, since admin-service sits behind
     * the shared gateway), falling back to the transport remote address. Returns
     * {@code null} when no request is bound — the condition treats that as a
     * fail-safe deny.
     */
    private static String resolveSourceIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
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
        if ("TenantAdminController".equals(simple)) {
            if ("createTenant".equals(name)) return ActionCode.TENANT_CREATE;
            if ("updateTenant".equals(name)) return ActionCode.TENANT_UPDATE;
        }
        // TASK-BE-339 — operator org_scope management controller.
        // TASK-BE-347 (ADR-MONO-024 D3-i) — assign/unassign on the same controller.
        if ("OperatorOrgScopeController".equals(simple)) {
            if ("setOrgScope".equals(name)) return ActionCode.OPERATOR_ORG_SCOPE_UPDATE;
            if ("assignOperator".equals(name)) return ActionCode.OPERATOR_ASSIGNMENT_CREATE;
            if ("unassignOperator".equals(name)) return ActionCode.OPERATOR_ASSIGNMENT_DELETE;
            // listAssignments is a read; fall through to null
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
