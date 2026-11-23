import { z } from 'zod';
import { OrgNodesUnavailableError } from '@/shared/api/errors';
import { newRequestId } from '@/shared/lib/logger';
import { makeIamProxyErrorMapper } from '@/shared/api/proxy-factory';
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

/**
 * tenant placement (TASK-PC-FE-312 / TASK-BE-625): `orgNodeId` + reason.
 * `orgNodeId` is `nullable()` but NOT `optional()` — the KEY must be present,
 * `null` = detach. A body without the key is a 422 here, before it can reach
 * the producer (which 400s `{}` for the same reason: a typo'd key must never
 * silently detach the tenant and lift its ceiling).
 */
export const PlaceTenantBodySchema = z
  .object({
    orgNodeId: z.string().min(1).nullable(),
    reason: z.string(),
  })
  .strict();
export type PlaceTenantBody = z.infer<typeof PlaceTenantBodySchema>;

export const mapError = makeIamProxyErrorMapper({
  logPrefix: 'org_nodes',
  UnavailableErrorClass: OrgNodesUnavailableError,
  unavailableMessage: 'org-node hierarchy unavailable',
});

export { badRequest } from '@/shared/api/proxy-factory';

export { newRequestId };
