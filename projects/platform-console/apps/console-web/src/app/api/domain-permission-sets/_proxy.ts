import { NextResponse } from 'next/server';
import { z } from 'zod';
import { ApiError, DomainPermissionSetsUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping + body schemas for the ADR-MONO-083
 * 「업무 권한 세트」 proxy routes (TASK-PC-FE-338 / admin-api.md §
 * 도메인 업무 권한 세트 / § 2.5). Mirrors the operator-group `_proxy` mapping:
 *   - 401 → forced re-login;
 *   - 400 NO_ACTIVE_TENANT → tenant gate;
 *   - 403/404/409/422/400 producer errors → inline actionable passthrough
 *     (the differentiated read-only / in-use / role-not-assignable /
 *     domain-not-subscribed codes are never swallowed into a generic 500);
 *   - 503/timeout → 503 degrade (업무 권한 세트 surface only).
 *
 * The audit reason (+ create idempotency key) ride in the request BODY, never
 * the query string — the reason is operator-authored free text that a
 * `?reason=` would leak to access logs, browser history and `Referer`
 * headers (the org-nodes/groups DELETE precedent). The 204 RESPONSE stays
 * body-free.
 */

const DomainSetRoleRefBodySchema = z
  .object({ domainKey: z.string().min(1), role: z.string().min(1) })
  .strict();

/** create: name (1~120) + description? + roles (>=1) + reason + idempotency
 *  key (producer-required on create). */
export const CreateSetBodySchema = z
  .object({
    name: z.string().min(1).max(120),
    description: z.string().max(255).optional(),
    roles: z.array(DomainSetRoleRefBodySchema).min(1),
    reason: z.string(),
    idempotencyKey: z.string().min(1),
  })
  .strict();
export type CreateSetBody = z.infer<typeof CreateSetBodySchema>;

/** edit: all fields optional (at least one), `roles` (if sent) is a FULL
 *  REPLACE + reason. NO idempotency key (PATCH MUST NOT send one). */
export const UpdateSetBodySchema = z
  .object({
    name: z.string().min(1).max(120).optional(),
    description: z.string().max(255).optional(),
    roles: z.array(DomainSetRoleRefBodySchema).min(1).optional(),
    reason: z.string(),
  })
  .strict()
  .refine(
    (b) => b.name !== undefined || b.description !== undefined || b.roles !== undefined,
    { message: 'at least one of name/description/roles is required' },
  );
export type UpdateSetBody = z.infer<typeof UpdateSetBodySchema>;

/** reason-only body for the DELETE surface. The reason travels in the
 *  request BODY, never the query string (see the file header). The 204
 *  RESPONSE remains body-free. */
export const SetReasonBodySchema = z.object({ reason: z.string() }).strict();
export type SetReasonBody = z.infer<typeof SetReasonBodySchema>;

export function mapError(err: unknown, requestId: string): NextResponse {
  if (err instanceof ApiError && err.status === 401) {
    return NextResponse.json(
      { code: err.code, message: 'session expired' },
      { status: 401 },
    );
  }
  if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
    return NextResponse.json(
      { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' },
      { status: 400 },
    );
  }
  if (err instanceof ApiError) {
    // 403 PERMISSION_DENIED / TENANT_SCOPE_DENIED /
    // 404 DOMAIN_PERMISSION_SET_NOT_FOUND /
    // 409 DOMAIN_PERMISSION_SET_READ_ONLY / DOMAIN_PERMISSION_SET_IN_USE /
    // 422 DOMAIN_ROLE_NOT_ASSIGNABLE / DOMAIN_NOT_SUBSCRIBED /
    // 400 VALIDATION_ERROR / REASON_REQUIRED → inline actionable passthrough
    // (verbatim code + message).
    return NextResponse.json(
      { code: err.code, message: err.message },
      { status: err.status },
    );
  }
  if (err instanceof DomainPermissionSetsUnavailableError) {
    logger.warn('domain_permission_sets_proxy_degraded', {
      requestId,
      reason: err.reason,
    });
    return NextResponse.json(
      { code: err.code, message: '업무 권한 세트 service unavailable' },
      { status: 503 },
    );
  }
  logger.error('domain_permission_sets_proxy_error', { requestId });
  return NextResponse.json(
    { code: 'DOWNSTREAM_ERROR', message: '업무 권한 세트 service unavailable' },
    { status: 503 },
  );
}

export function badRequest(): NextResponse {
  return NextResponse.json(
    { code: 'VALIDATION_ERROR', message: 'invalid request body' },
    { status: 422 },
  );
}

export { newRequestId };
