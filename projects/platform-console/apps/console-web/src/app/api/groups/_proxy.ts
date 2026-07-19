import { NextResponse } from 'next/server';
import { z } from 'zod';
import { ApiError, GroupsUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping + body schemas for the operator-group proxy
 * routes (TASK-PC-FE-250 / ADR-MONO-046 / admin-api.md § Operator Group
 * Management / § 2.5). Mirrors the org-nodes / tenants `_proxy` mapping:
 *   - 401 → forced re-login;
 *   - 400 NO_ACTIVE_TENANT → tenant gate;
 *   - 403/404/409/422/400 producer errors → inline actionable passthrough (the
 *     differentiated name-conflict / member-tenant-mismatch / no-escalation /
 *     role-forbidden codes are never swallowed into a generic 500 —
 *     `[[env_bff_proxy_null_body_status_500]]`-adjacent caution);
 *   - 503/timeout → 503 degrade (운영자 그룹 surface only).
 *
 * The audit reason (+ create/add idempotency key) ride in the request BODY,
 * never the query string — the reason is operator-authored free text that a
 * `?reason=` would leak to access logs, browser history and `Referer` headers
 * (the org-nodes DELETE precedent). The 204 RESPONSE stays body-free.
 */

/** create: tenantId (`!= '*'`) + name (1~120) + description? + reason +
 *  idempotency key (producer-required on create). */
export const CreateGroupBodySchema = z
  .object({
    tenantId: z.string().min(1).refine((t) => t !== '*', {
      message: 'a platform-global group is not allowed',
    }),
    name: z.string().min(1).max(120),
    description: z.string().max(255).optional(),
    reason: z.string(),
    idempotencyKey: z.string().min(1),
  })
  .strict();
export type CreateGroupBody = z.infer<typeof CreateGroupBodySchema>;

/** update: name and/or description (at least one) + reason. NO idempotency key
 *  (partial PATCH is naturally idempotent). */
export const UpdateGroupBodySchema = z
  .object({
    name: z.string().min(1).max(120).optional(),
    description: z.string().max(255).optional(),
    reason: z.string(),
  })
  .strict()
  .refine((b) => b.name !== undefined || b.description !== undefined, {
    message: 'at least one of name/description is required',
  });
export type UpdateGroupBody = z.infer<typeof UpdateGroupBodySchema>;

/** add-member: operatorId + reason + idempotency key (producer-required). */
export const AddMemberBodySchema = z
  .object({
    operatorId: z.string().min(1),
    reason: z.string(),
    idempotencyKey: z.string().min(1),
  })
  .strict();
export type AddMemberBody = z.infer<typeof AddMemberBodySchema>;

/** add-grants: roles and/or tenantAssignments (at least one non-empty) +
 *  reason + idempotency key (producer-required). */
export const AddGrantsBodySchema = z
  .object({
    roles: z.array(z.string().min(1)).optional(),
    tenantAssignments: z
      .array(z.object({ tenantId: z.string().min(1) }).strict())
      .optional(),
    reason: z.string(),
    idempotencyKey: z.string().min(1),
  })
  .strict()
  .refine(
    (b) => (b.roles?.length ?? 0) > 0 || (b.tenantAssignments?.length ?? 0) > 0,
    { message: 'at least one of roles/tenantAssignments is required' },
  );
export type AddGrantsBody = z.infer<typeof AddGrantsBodySchema>;

/**
 * reason-only body for the three DELETE surfaces (group delete, member remove,
 * grant revoke). The reason travels in the request BODY, never the query string
 * (see the file header). The 204 RESPONSE remains body-free.
 */
export const GroupReasonBodySchema = z.object({ reason: z.string() }).strict();
export type GroupReasonBody = z.infer<typeof GroupReasonBodySchema>;

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
    // 403 PERMISSION_DENIED / TENANT_SCOPE_DENIED / ROLE_GRANT_FORBIDDEN /
    // 404 GROUP_NOT_FOUND / GROUP_MEMBER_NOT_FOUND / OPERATOR_NOT_FOUND /
    // 409 GROUP_NAME_CONFLICT / GROUP_MEMBER_ALREADY_EXISTS /
    // GROUP_GRANT_ALREADY_EXISTS / 422 GROUP_MEMBER_TENANT_MISMATCH /
    // GROUP_GRANT_NO_ESCALATION / 400 VALIDATION_ERROR / ROLE_NOT_FOUND →
    // inline actionable passthrough (verbatim code + message).
    return NextResponse.json(
      { code: err.code, message: err.message },
      { status: err.status },
    );
  }
  if (err instanceof GroupsUnavailableError) {
    logger.warn('groups_proxy_degraded', { requestId, reason: err.reason });
    return NextResponse.json(
      { code: err.code, message: 'operator-group service unavailable' },
      { status: 503 },
    );
  }
  logger.error('groups_proxy_error', { requestId });
  return NextResponse.json(
    { code: 'DOWNSTREAM_ERROR', message: 'operator-group service unavailable' },
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
