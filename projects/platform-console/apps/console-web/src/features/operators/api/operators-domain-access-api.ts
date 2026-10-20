import {
  DomainAccessResponseSchema,
  type DomainAccessResponse,
  GrantDomainSetResultSchema,
  type GrantDomainSetResult,
} from './domain-access-types';
import { callGapOperators, OPERATORS_PREFIX } from './operators-client';

/**
 * operators api — server-side ADR-MONO-083 「업무 권한」 surface (TASK-PC-FE-338
 * slice b; admin-api.md § 도메인 업무 권한 세트 rows 6-8). Consumed ONLY by the
 * same-origin BFF routes under `app/api/operators/[operatorId]/domain-access`
 * + `.../domain-permission-sets[/[setId]]` — mirrors `operators-assignments-api.ts`.
 *
 * PER-ENDPOINT HEADER MATRIX (contract § 2.4.3.4 rows 6-8 — NOT uniform):
 *   - `GET  .../domain-access`                    → no mutation headers (read);
 *   - `POST .../domain-permission-sets` (grant)    → `X-Operator-Reason` ONLY
 *     (NO `Idempotency-Key` — a repeat grant is `409
 *     DOMAIN_PERMISSION_SET_ALREADY_GRANTED`);
 *   - `DELETE .../domain-permission-sets/{setId}`  → `X-Operator-Reason` ONLY.
 *
 * `X-Tenant-Id` (the active tenant) is attached by `callGapOperators` per the
 * omission rule the producer documents for `domain_access.manage` — except a
 * grant/revoke called with `{ allTenants: true }` (TASK-PC-FE-354), which
 * sends `*`. 401 →
 * `ApiError` (whole-session re-login); 403/404/409/422 → `ApiError` (inline
 * actionable — `404 OPERATOR_NOT_FOUND` / `404 DOMAIN_PERMISSION_SET_NOT_FOUND`
 * / `409 DOMAIN_PERMISSION_SET_ALREADY_GRANTED` / `422 DOMAIN_NOT_SUBSCRIBED`
 * surfaced verbatim); 503/timeout → `OperatorsUnavailableError` (this section
 * degrades only — the operators list + every other IAM surface stay intact).
 */

export async function getOperatorDomainAccess(
  operatorId: string,
): Promise<DomainAccessResponse> {
  return callGapOperators(
    {
      method: 'GET',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/domain-access`,
    },
    (json) => DomainAccessResponseSchema.parse(json),
  );
}

/**
 * TASK-PC-FE-354 — `allTenants: true` ⇒ this write addresses the target's
 * «모든 테넌트» (`tenantId='*'`) row: `X-Tenant-Id: *` for this call only
 * (contract § 2.4.3.4 rows 7–8, platform-target bullet). The UI sets it only
 * for a `'*'` target opened by a `'*'` caller; the producer stays the judge
 * (`400` on a company target, `403` from a non-`'*'` actor). Absent / `false`
 * ⇒ the active tenant, byte-identical to before.
 */
export interface DomainSetWriteOptions {
  allTenants?: boolean;
}

export async function grantOperatorDomainSet(
  operatorId: string,
  setId: string,
  reason: string,
  options: DomainSetWriteOptions = {},
): Promise<GrantDomainSetResult> {
  return callGapOperators(
    {
      method: 'POST',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/domain-permission-sets`,
      reason,
      // NO idempotencyKey — per the producer header matrix (row 7: a repeat
      // is 409 DOMAIN_PERMISSION_SET_ALREADY_GRANTED, not a retry).
      body: { setId },
      ...(options.allTenants ? { tenantOverride: '*' as const } : {}),
    },
    (json) => GrantDomainSetResultSchema.parse(json),
  );
}

export async function revokeOperatorDomainSet(
  operatorId: string,
  setId: string,
  reason: string,
  options: DomainSetWriteOptions = {},
): Promise<void> {
  await callGapOperators(
    {
      method: 'DELETE',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/domain-permission-sets/${encodeURIComponent(setId)}`,
      reason,
      expectNoContent: true,
      ...(options.allTenants ? { tenantOverride: '*' as const } : {}),
    },
    () => undefined,
  );
}
