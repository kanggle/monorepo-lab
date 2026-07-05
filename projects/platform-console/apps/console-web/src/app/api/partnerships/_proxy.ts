import { NextResponse } from 'next/server';
import { z } from 'zod';
import { ApiError, PartnershipsUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * Shared body/param schemas + error → HTTP mapping for the partnership proxy
 * routes (TASK-PC-FE-187 / admin-api.md § Partnership Management / § 2.5).
 * Mirrors the operators `_proxy` mapping:
 *   - 401 → forced re-login;
 *   - 400 NO_ACTIVE_TENANT → tenant gate;
 *   - 403/404/409/400/422 producer errors → inline actionable (passthrough);
 *   - 503/timeout → 503 degrade (partnership surface only).
 *
 * The tenant id is NEVER accepted from the client — neither in a body nor a
 * path segment (the invite body carries only `partnerTenantId`; the host
 * tenant is the server-side active `X-Tenant-Id`). The `reason` travels in the
 * same-origin JSON body and becomes the `X-Operator-Reason` header only inside
 * `callPartnerships` (the proxy never re-derives the header set).
 */

/** A bounded scope set — `.strict()` mirrors the producer's fail-on-unknown. */
export const ScopeSetBodySchema = z
  .object({
    domains: z.array(z.string()),
    roles: z.array(z.string()),
  })
  .strict();

/** invite: partnerTenantId + delegatedScope + reason (NO tenantId, NO key —
 *  the host tenant is the server active tenant; the key is generated in the
 *  api layer). */
export const InviteBodySchema = z
  .object({
    partnerTenantId: z.string().min(1),
    delegatedScope: ScopeSetBodySchema,
    reason: z.string(),
  })
  .strict();
export type InviteBody = z.infer<typeof InviteBodySchema>;

/** lifecycle transition (accept/suspend/reactivate/terminate) + participant
 *  remove: reason ONLY (the target is fully specified by the path). */
export const ReasonBodySchema = z.object({ reason: z.string() }).strict();
export type ReasonBody = z.infer<typeof ReasonBodySchema>;

/** participant add: optional participantScope + reason. Omitted/null scope ⟺
 *  full delegatedScope (net-zero default). */
export const ParticipantAddBodySchema = z
  .object({
    participantScope: ScopeSetBodySchema.nullable().optional(),
    reason: z.string(),
  })
  .strict();
export type ParticipantAddBody = z.infer<typeof ParticipantAddBodySchema>;

/** Path param — a non-empty id / operatorId. Validated via `.safeParse`. */
export const IdParamSchema = z.string().min(1);

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
    // 403 PERMISSION_DENIED / PARTNERSHIP_SCOPE_DENIED /
    // 400 REASON_REQUIRED|VALIDATION_ERROR /
    // 422 PARTNERSHIP_SCOPE_INVALID|PARTICIPANT_NOT_OWN_OPERATOR|
    //     PARTICIPANT_SCOPE_EXCEEDS_DELEGATION /
    // 404 PARTNERSHIP_NOT_FOUND|OPERATOR_NOT_FOUND|PARTICIPANT_NOT_FOUND /
    // 409 PARTNERSHIP_ALREADY_EXISTS|PARTNERSHIP_TRANSITION_INVALID
    // → inline actionable passthrough.
    return NextResponse.json(
      { code: err.code, message: err.message },
      { status: err.status },
    );
  }
  if (err instanceof PartnershipsUnavailableError) {
    logger.warn('partnerships_proxy_degraded', {
      requestId,
      reason: err.reason,
    });
    return NextResponse.json(
      { code: err.code, message: 'partnerships unavailable' },
      { status: 503 },
    );
  }
  logger.error('partnerships_proxy_error', { requestId });
  return NextResponse.json(
    { code: 'DOWNSTREAM_ERROR', message: 'partnerships unavailable' },
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
