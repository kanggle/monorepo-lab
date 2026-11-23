import { z } from 'zod';
import { TenantsUnavailableError } from '@/shared/api/errors';
import { newRequestId } from '@/shared/lib/logger';
import { makeIamProxyErrorMapper } from '@/shared/api/proxy-factory';
import {
  TENANT_TYPES,
  TENANT_PATCH_STATUSES,
  FIRST_ADMIN_EMAIL_REGEX,
  FIRST_ADMIN_EMAIL_MAX,
  FIRST_ADMIN_DISPLAY_NAME_MAX,
} from '@/features/tenants';

/**
 * Shared error → HTTP mapping + body schemas for the tenant-management proxy
 * routes (TASK-PC-FE-226 / admin-api.md § "Tenant Lifecycle (TASK-BE-256)" /
 * § 2.5). Mirrors the operators/subscriptions `_proxy` mapping:
 *   - 401 → forced re-login;
 *   - 400 NO_ACTIVE_TENANT → tenant gate;
 *   - 403/404/409/400 producer errors → inline actionable (passthrough — a
 *     `409 TENANT_ALREADY_EXISTS` / `400 TENANT_ID_RESERVED` never gets
 *     swallowed into a generic 500, per
 *     `[[env_bff_proxy_null_body_status_500]]`-adjacent caution);
 *   - 503/timeout → 503 degrade (tenants surface only).
 */

/** create: tenantId + displayName + tenantType + reason (+ optional
 *  idempotencyKey — producer-recommended, not required) (+ optional
 *  `orgNodeId` — TASK-PC-FE-312 / TASK-BE-625: the node to place the new
 *  tenant under; absent or `null` = 무소속, the pre-312 create)
 *  + 🔴 REQUIRED `firstAdmin{email, displayName}` (TASK-MONO-773 S4 — IAM
 *  answers `400` without it; checked here after trim with IAM's own rules so a
 *  bad draft fails fast as `422` and never reaches IAM). */
export const FirstAdminBodySchema = z
  .object({
    email: z
      .string()
      .trim()
      .min(1)
      .max(FIRST_ADMIN_EMAIL_MAX)
      .regex(FIRST_ADMIN_EMAIL_REGEX),
    displayName: z.string().trim().min(1).max(FIRST_ADMIN_DISPLAY_NAME_MAX),
  })
  .strict();

export const CreateTenantBodySchema = z
  .object({
    tenantId: z.string().min(1),
    displayName: z.string().min(1).max(100),
    tenantType: z.enum(TENANT_TYPES),
    firstAdmin: FirstAdminBodySchema,
    reason: z.string(),
    idempotencyKey: z.string().min(1).optional(),
    orgNodeId: z.string().min(1).nullable().optional(),
  })
  .strict();
export type CreateTenantBody = z.infer<typeof CreateTenantBodySchema>;

/** TASK-MONO-773 S6 — re-invite a `PENDING` tenant's first admin at a new address (or when none is left):
 *  `{email, displayName}` with the create's `firstAdmin` rules + reason + REQUIRED idempotencyKey
 *  (admin-api.md § POST /api/admin/tenants/{tenantId}/first-admin-invitation). */
export const FirstAdminInvitationBodySchema = z
  .object({
    email: FirstAdminBodySchema.shape.email,
    displayName: FirstAdminBodySchema.shape.displayName,
    reason: z.string(),
    idempotencyKey: z.string().min(1),
  })
  .strict();

/** update: displayName and/or status (at least one) + reason. NO
 *  idempotencyKey (partial PATCH is naturally idempotent). `status` is the
 *  REQUEST enum — never `PENDING` (TASK-MONO-773: only the create makes it). */
export const UpdateTenantBodySchema = z
  .object({
    displayName: z.string().min(1).max(100).optional(),
    status: z.enum(TENANT_PATCH_STATUSES).optional(),
    reason: z.string(),
  })
  .strict()
  .refine(
    (b) => b.displayName !== undefined || b.status !== undefined,
    { message: 'at least one of displayName/status is required' },
  );
export type UpdateTenantBody = z.infer<typeof UpdateTenantBodySchema>;

export const mapError = makeIamProxyErrorMapper({
  logPrefix: 'tenants',
  UnavailableErrorClass: TenantsUnavailableError,
  unavailableMessage: 'tenant management unavailable',
});

export { badRequest } from '@/shared/api/proxy-factory';

export { newRequestId };
