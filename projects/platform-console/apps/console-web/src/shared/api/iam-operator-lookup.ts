import { getActiveTenant } from '@/shared/lib/session';
import { callGapOperators, OPERATORS_PREFIX } from './iam-operators-read';
import {
  OperatorLookupResponseSchema,
  type OperatorLookupResponse,
} from './iam-operator-lookup-types';

/**
 * Server-side IAM operator e-mail lookup — `GET /api/admin/operators/lookup`
 * (iam `admin-api.md` § same name · console contract § 2.4.8 «Account
 * selection», TASK-MONO-777).
 *
 * Rides the operators profile (`callGapOperators`): exchanged operator token +
 * active tenant, `forbiddenMode: 'generic'` — a `403` is an inline `ApiError(403)`,
 * never folded into the `401` re-login path. The producer needs NO permission
 * key and answers «not found» and «out of scope» with the same empty list.
 *
 * Lives in `shared/` because the consumer is the erp-ops feature while the
 * surface is IAM's — a feature → feature import is barred (architecture.md
 * § Forbidden Dependencies). SERVER-ONLY (reaches `next/headers`); the client
 * parses with `iam-operator-lookup-types.ts`.
 */
export async function lookupOperatorsByEmail(email: string): Promise<OperatorLookupResponse> {
  const qs = new URLSearchParams();
  qs.set('email', email.trim());
  // Same scoping as every IAM read: the active tenant as `tenantId`. Absent ⇒
  // the gateway core blocks with 400 NO_ACTIVE_TENANT before any fetch.
  const tenant = await getActiveTenant();
  if (tenant) qs.set('tenantId', tenant);
  return callGapOperators(
    { method: 'GET', path: `${OPERATORS_PREFIX}/lookup?${qs.toString()}` },
    (json) => OperatorLookupResponseSchema.parse(json),
  );
}
