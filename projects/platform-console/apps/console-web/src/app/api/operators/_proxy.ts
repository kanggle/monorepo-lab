import { NextResponse } from 'next/server';
import { z } from 'zod';
import { ApiError, OperatorsUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping for the operators proxy routes
 * (console-integration-contract § 2.4.3 / § 2.5). Mirrors the FE-002
 * accounts `_proxy` mapping so every operators op maps identically:
 *   - 401 → forced re-login (client api-client refresh→login)
 *   - 400 NO_ACTIVE_TENANT → tenant gate
 *   - 403/404/409/400 producer errors → inline actionable (passthrough)
 *   - 503/timeout/network → 503 degrade (operators section only)
 *
 * The reason / idempotency-key / password are NEVER fabricated or logged
 * here — the client must supply a non-empty reason for the reason-bearing
 * mutations (the reason-capture gate); the api layer is the fail-safe. The
 * per-endpoint header matrix is applied in `operators-api.ts` (create ⇒
 * reason+key; roles/status ⇒ reason only; password ⇒ self) — the proxy
 * routes call the matching api fn and never re-derive the header set.
 */

/** create: reason + idempotencyKey + the operator draft (password incl.). */
export const CreateBodySchema = z.object({
  email: z.string().min(1),
  displayName: z.string().min(1),
  password: z.string().min(1),
  roles: z.array(z.string()),
  tenantId: z.string().min(1),
  reason: z.string(),
  idempotencyKey: z.string().min(1),
});
export type CreateBody = z.infer<typeof CreateBodySchema>;

/** edit-roles: roles + reason ONLY (NO idempotencyKey — producer matrix). */
export const EditRolesBodySchema = z.object({
  roles: z.array(z.string()),
  reason: z.string(),
});
export type EditRolesBody = z.infer<typeof EditRolesBodySchema>;

/** change-status: status + reason ONLY (NO idempotencyKey). */
export const ChangeStatusBodySchema = z.object({
  status: z.enum(['ACTIVE', 'SUSPENDED']),
  reason: z.string(),
});
export type ChangeStatusBody = z.infer<typeof ChangeStatusBodySchema>;

/** change-password: self — current + new. NO reason, NO key per producer. */
export const ChangePasswordBodySchema = z.object({
  currentPassword: z.string().min(1),
  newPassword: z.string().min(1),
});
export type ChangePasswordBody = z.infer<typeof ChangePasswordBodySchema>;

/**
 * update-profile: self — operator profile carrier (TASK-PC-FE-016).
 * NO reason, NO key per producer (mirrors `me/password` exactly).
 *
 * Body shape mirrors the read shape on the registry verbatim
 * (`operatorContext.defaultAccountId: string | null`). Explicit `null`
 * means "clear the column"; a string must be non-empty after trim,
 * ≤ 36 chars, with no internal whitespace and no control chars / DEL
 * (regex `^[^\s\x00-\x1f\x7f]+$`). The producer is the final authority
 * (the same validation lives on GAP `admin-service`); this zod is the
 * UX fail-fast pre-check at the same-origin proxy boundary.
 *
 * `.strict()` on both the outer + nested objects mirrors the producer's
 * `FAIL_ON_UNKNOWN_PROPERTIES = true` — unknown keys under
 * `operatorContext` (or at the top level) → 422 VALIDATION_ERROR, never
 * forwarded to GAP.
 */
export const UpdateProfileBodySchema = z
  .object({
    operatorContext: z
      .object({
        defaultAccountId: z.union([
          z.null(),
          z
            .string()
            .trim()
            .min(1)
            .max(36)
            .regex(/^[^\s\x00-\x1f\x7f]+$/),
        ]),
      })
      .strict(),
  })
  .strict();
export type UpdateProfileBody = z.infer<typeof UpdateProfileBodySchema>;

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
    // 403 PERMISSION_DENIED / 403 TENANT_SCOPE_DENIED /
    // 409 OPERATOR_EMAIL_CONFLICT / 400 ROLE_NOT_FOUND|VALIDATION_ERROR|
    // STATE_TRANSITION_INVALID|SELF_SUSPEND_FORBIDDEN|
    // CURRENT_PASSWORD_MISMATCH|PASSWORD_POLICY_VIOLATION /
    // 404 OPERATOR_NOT_FOUND → inline actionable (passthrough, no crash).
    return NextResponse.json(
      { code: err.code, message: err.message },
      { status: err.status },
    );
  }
  if (err instanceof OperatorsUnavailableError) {
    logger.warn('operators_proxy_degraded', {
      requestId,
      reason: err.reason,
    });
    return NextResponse.json(
      { code: err.code, message: 'operators unavailable' },
      { status: 503 },
    );
  }
  logger.error('operators_proxy_error', { requestId });
  return NextResponse.json(
    { code: 'DOWNSTREAM_ERROR', message: 'operators unavailable' },
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
