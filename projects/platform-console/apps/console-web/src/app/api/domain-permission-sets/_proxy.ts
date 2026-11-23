import { z } from 'zod';
import { DomainPermissionSetsUnavailableError } from '@/shared/api/errors';
import { newRequestId } from '@/shared/lib/logger';
import { makeIamProxyErrorMapper } from '@/shared/api/proxy-factory';

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

export const mapError = makeIamProxyErrorMapper({
  logPrefix: 'domain_permission_sets',
  UnavailableErrorClass: DomainPermissionSetsUnavailableError,
  unavailableMessage: '업무 권한 세트 service unavailable',
});

export { badRequest } from '@/shared/api/proxy-factory';

export { newRequestId };
