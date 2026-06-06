package com.example.admin.presentation.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exempts a mutation controller method from the deny-by-default RBAC guardrail
 * in {@link RequiresPermissionAspect}. Use only for self-service operations
 * where a valid operator JWT is sufficient (no additional permission required).
 *
 * <p>Example: {@code PATCH /api/admin/operators/me/password} — any authenticated
 * operator may change their own password without a specific permission grant.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SelfServiceEndpoint {
}
