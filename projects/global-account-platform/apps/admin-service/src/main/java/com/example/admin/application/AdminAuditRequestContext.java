package com.example.admin.application;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Per-request helpers used by the admin audit subsystem to stamp the current
 * HTTP endpoint/method and to read the in-flight {@link OperatorContext} from
 * the security context.
 *
 * <p>Extracted from {@link AdminActionAuditor} (TASK-BE-314). All methods are
 * tolerant of being called outside a request scope (e.g. direct unit tests of
 * {@link AdminActionAuditWriter}) — they return {@code null} rather than
 * throwing, matching the pre-split behavior verbatim.
 */
final class AdminAuditRequestContext {

    private AdminAuditRequestContext() {}

    static String currentEndpoint() {
        HttpServletRequest req = currentRequest();
        return req != null ? req.getRequestURI() : null;
    }

    static String currentMethod() {
        HttpServletRequest req = currentRequest();
        return req != null ? req.getMethod() : null;
    }

    /**
     * Resolves {@link OperatorContext} from the security context if available.
     * Returns {@code null} when called outside a request scope (e.g. tests that
     * exercise recordDenied in isolation).
     */
    static OperatorContext tryReadOperatorContext() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof OperatorContext ctx) {
                return ctx;
            }
        } catch (RuntimeException ignored) {
            // no security context (e.g. direct unit test) — fall through
        }
        return null;
    }

    private static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
