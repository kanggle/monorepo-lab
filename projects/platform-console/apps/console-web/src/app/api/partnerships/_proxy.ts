import { z } from 'zod';
import { PartnershipsUnavailableError } from '@/shared/api/errors';
import { newRequestId } from '@/shared/lib/logger';
import { makeIamProxyErrorMapper } from '@/shared/api/proxy-factory';

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

export const mapError = makeIamProxyErrorMapper({
  logPrefix: 'partnerships',
  UnavailableErrorClass: PartnershipsUnavailableError,
  unavailableMessage: 'partnerships unavailable',
});

export { badRequest } from '@/shared/api/proxy-factory';

export { newRequestId };
