import { NextResponse } from 'next/server';
import { z } from 'zod';
import { ApiError, TenantsUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';
import { TENANT_TYPES, TENANT_STATUSES } from '@/features/tenants';

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
 *  idempotencyKey — producer-recommended, not required). */
export const CreateTenantBodySchema = z
  .object({
    tenantId: z.string().min(1),
    displayName: z.string().min(1).max(100),
    tenantType: z.enum(TENANT_TYPES),
    reason: z.string(),
    idempotencyKey: z.string().min(1).optional(),
  })
  .strict();
export type CreateTenantBody = z.infer<typeof CreateTenantBodySchema>;

/** update: displayName and/or status (at least one) + reason. NO
 *  idempotencyKey (partial PATCH is naturally idempotent). */
export const UpdateTenantBodySchema = z
  .object({
    displayName: z.string().min(1).max(100).optional(),
    status: z.enum(TENANT_STATUSES).optional(),
    reason: z.string(),
  })
  .strict()
  .refine(
    (b) => b.displayName !== undefined || b.status !== undefined,
    { message: 'at least one of displayName/status is required' },
  );
export type UpdateTenantBody = z.infer<typeof UpdateTenantBodySchema>;

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
    // 403 PERMISSION_DENIED / TENANT_SCOPE_DENIED (not SUPER_ADMIN) /
    // 400 VALIDATION_ERROR / TENANT_ID_RESERVED /
    // 404 TENANT_NOT_FOUND / 409 TENANT_ALREADY_EXISTS /
    // 503(as ApiError, if any) → inline actionable passthrough.
    return NextResponse.json(
      { code: err.code, message: err.message },
      { status: err.status },
    );
  }
  if (err instanceof TenantsUnavailableError) {
    logger.warn('tenants_proxy_degraded', { requestId, reason: err.reason });
    return NextResponse.json(
      { code: err.code, message: 'tenant management unavailable' },
      { status: 503 },
    );
  }
  logger.error('tenants_proxy_error', { requestId });
  return NextResponse.json(
    { code: 'DOWNSTREAM_ERROR', message: 'tenant management unavailable' },
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
