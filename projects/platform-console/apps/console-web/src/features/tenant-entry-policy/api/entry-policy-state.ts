import { redirect } from 'next/navigation';
import { ApiError, TenantsUnavailableError } from '@/shared/api/errors';
import { getAccessToken } from '@/shared/lib/session';
import { decodeJwtPayload } from '@/shared/lib/jwt';
import { getEntryPolicy } from './entry-policy-api';
import type { EntryPolicy } from './types';

/**
 * Server-side SSR state for the entry-policy control (TASK-MONO-771 S5 —
 * console-integration-contract § 2.4.3.3), rendered in two places: the tenant
 * detail page and the «보안 설정» page.
 *
 * Resilience (§ 2.5 / § 2.4.3.3):
 *   - 401 → `redirect('/login')` (no partial authed state).
 *   - `NO_ACTIVE_TENANT` → `noTenant` (the page shows its tenant gate).
 *   - 403 `PERMISSION_DENIED` / `TENANT_SCOPE_DENIED` → `permissionError` —
 *     the control renders an inline «권한 없음», never a crash.
 *   - 503 / timeout → `degraded` — ONLY this control degrades.
 */
export interface EntryPolicyState {
  policy: EntryPolicy | null;
  noTenant: boolean;
  permissionError: { code: string; message: string } | null;
  degraded: boolean;
  /**
   * § 2.4.3.3 «Self-effect» — does the CURRENT session's IAM token already carry
   * `mfa` in `amr`? `false` → the «켜기» confirm warns that the operator turning
   * it on will be asked for a second factor at the next exchange/assume too.
   * `null` = cannot tell (no token / unreadable) → the warning is shown.
   * A display hint only — never an authorization decision.
   */
  selfHasSecondFactor: boolean | null;
}

export async function readSelfHasSecondFactor(): Promise<boolean | null> {
  const token = await getAccessToken();
  const amr = decodeJwtPayload(token)?.amr;
  if (!Array.isArray(amr)) return null;
  return amr.includes('mfa');
}

export async function getEntryPolicyState(tenantId: string): Promise<EntryPolicyState> {
  const selfHasSecondFactor = await readSelfHasSecondFactor();
  const base = { selfHasSecondFactor };
  try {
    const policy = await getEntryPolicy(tenantId);
    return { ...base, policy, noTenant: false, permissionError: null, degraded: false };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login?error=session_expired');
    }
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return { ...base, policy: null, noTenant: true, permissionError: null, degraded: false };
    }
    if (err instanceof ApiError && err.status === 403) {
      return {
        ...base,
        policy: null,
        noTenant: false,
        permissionError: { code: err.code, message: err.message },
        degraded: false,
      };
    }
    if (err instanceof TenantsUnavailableError) {
      return { ...base, policy: null, noTenant: false, permissionError: null, degraded: true };
    }
    return { ...base, policy: null, noTenant: false, permissionError: null, degraded: true };
  }
}
