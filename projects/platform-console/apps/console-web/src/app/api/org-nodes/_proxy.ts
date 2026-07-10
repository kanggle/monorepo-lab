import { NextResponse } from 'next/server';
import { z } from 'zod';
import { ApiError, OrgNodesUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';
import { CeilingSchema } from '@/features/org-hierarchy/api/types';

/**
 * Shared error → HTTP mapping + body schemas for the org-node hierarchy proxy
 * routes (TASK-PC-FE-237 / ADR-047 / admin-api.md § org-node / § 2.5). Mirrors
 * the tenants `_proxy` mapping:
 *   - 401 → forced re-login;
 *   - 400 NO_ACTIVE_TENANT → tenant gate;
 *   - 403/404/422/400 producer errors → inline actionable passthrough (the
 *     differentiated 422 cycle / depth / ceiling-not-subset / not-empty /
 *     grant-out-of-ceiling codes are never swallowed into a generic 500 —
 *     `[[env_bff_proxy_null_body_status_500]]`-adjacent caution);
 *   - 503/timeout → 503 degrade (org-hierarchy surface only).
 */

/** create: name + parentId(null=root) + ceiling + reason. */
export const CreateOrgNodeBodySchema = z
  .object({
    name: z.string().min(1).max(100),
    parentId: z.string().nullable(),
    ceiling: CeilingSchema,
    reason: z.string(),
  })
  .strict();
export type CreateOrgNodeBody = z.infer<typeof CreateOrgNodeBodySchema>;

/** update: name and/or parentId (at least one) + reason. `parentId: null`
 *  re-parents to a root. NO ceiling here (ceiling has its own PUT endpoint). */
export const UpdateOrgNodeBodySchema = z
  .object({
    name: z.string().min(1).max(100).optional(),
    parentId: z.string().nullable().optional(),
    reason: z.string(),
  })
  .strict()
  .refine((b) => b.name !== undefined || b.parentId !== undefined, {
    message: 'at least one of name/parentId is required',
  });
export type UpdateOrgNodeBody = z.infer<typeof UpdateOrgNodeBodySchema>;

/** set-ceiling: the ceiling (the api layer unwraps it — the producer body IS
 *  the ceiling) + reason. */
export const SetCeilingBodySchema = z
  .object({
    ceiling: CeilingSchema,
    reason: z.string(),
  })
  .strict();
export type SetCeilingBody = z.infer<typeof SetCeilingBodySchema>;

/** grant-admin: operatorId + roleName + reason. */
export const GrantOrgAdminBodySchema = z
  .object({
    operatorId: z.string().min(1),
    roleName: z.string().min(1),
    reason: z.string(),
  })
  .strict();
export type GrantOrgAdminBody = z.infer<typeof GrantOrgAdminBodySchema>;

/**
 * reason-only body for the two DELETE surfaces (node delete, admin revoke).
 * The reason travels in the request BODY, never the query string — it is
 * operator-authored free text and a `?reason=` would be captured by access
 * logs, browser history and `Referer` headers. (`apiClient.delete` forwards
 * `opts.body`; the `operators/.../assignments` DELETE is the precedent.)
 * The 204 RESPONSE remains body-free.
 */
export const OrgNodeReasonBodySchema = z
  .object({ reason: z.string() })
  .strict();
export type OrgNodeReasonBody = z.infer<typeof OrgNodeReasonBodySchema>;

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
    // 403 PERMISSION_DENIED / ORG_NODE_SELF_CEILING_DENIED /
    // 404 ORG_NODE_NOT_FOUND / 422 ORG_NODE_* / 400 VALIDATION_ERROR →
    // inline actionable passthrough (verbatim code + message).
    return NextResponse.json(
      { code: err.code, message: err.message },
      { status: err.status },
    );
  }
  if (err instanceof OrgNodesUnavailableError) {
    logger.warn('org_nodes_proxy_degraded', { requestId, reason: err.reason });
    return NextResponse.json(
      { code: err.code, message: 'org-node hierarchy unavailable' },
      { status: 503 },
    );
  }
  logger.error('org_nodes_proxy_error', { requestId });
  return NextResponse.json(
    { code: 'DOWNSTREAM_ERROR', message: 'org-node hierarchy unavailable' },
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
