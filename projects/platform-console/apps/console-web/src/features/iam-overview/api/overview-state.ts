import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import { ApiError } from '@/shared/api/errors';
import { listOperators } from '@/features/operators/api/operators-api';
import { searchAccounts } from '@/features/accounts/api/accounts-api';
import { queryAudit } from '@/features/audit/api/audit-api';
import type { AuditRow } from '@/features/audit/api/types';

/**
 * Server-side IAM **operator overview snapshot** fan-out for the `/iam` landing
 * (TASK-PC-FE-180). Splits the former static `/iam` guide (relocated to
 * `/iam/guide`) from a live domain overview — the IAM sibling of the ecommerce
 * `/ecommerce` snapshot (TASK-PC-FE-156 / console-integration-contract §2.4.10.6).
 *
 * ── ARCHITECTURE (console-web direct fan-out; NO new producer) ──
 * Per ADR-MONO-017 D3.B counts are derived from the EXISTING IAM admin-service
 * list endpoints' `totalElements` with `page=0&size=1` — NO `/summary`
 * aggregation endpoint, NO console-bff leg (contrast: the console-wide
 * §2.4.4 / §2.4.9.1 operator overview is a console-bff fan-out — this one is
 * domain-internal, reusing the three feature api fns server-side):
 *   - operators — `listOperators` (§2.4.3; `operator.manage`) total + ACTIVE/SUSPENDED split.
 *   - accounts  — `searchAccounts` (§2.4.1; `account.read`) total.
 *   - audit     — `queryAudit` (§2.4.2; `audit.read`) total + recent 5 rows.
 *
 * ── RESILIENCE (§2.5) — the decisive rule (IAM error taxonomy) ──
 * The fan-out is bounded + parallel. Each cell CATCHES its own error into a
 * cell status (ok / forbidden / degraded) EXCEPT `401`, which it re-throws so
 * the top-level catch performs a whole-session `redirect('/login')` (no partial
 * authed state). Unlike the ecommerce snapshot's single `ApiError`, the IAM
 * clients throw a per-surface `*UnavailableError` for 503/timeout (→ degraded)
 * and `ApiError` for 401/403 — so the cell helper keys off BOTH. A `403`
 * (`PERMISSION_DENIED`/`TENANT_SCOPE_DENIED`) is a NORMAL forbidden cell for a
 * narrow role (the rbac.md access matrix), not an outage. No auto-refetch.
 *
 * ── TENANT GATE ──
 * All three legs require an active tenant (the api fns throw
 * `400 NO_ACTIVE_TENANT` before any fetch). Rather than surface three identical
 * per-cell messages, the state short-circuits to `noActiveTenant: true` and the
 * screen renders one page-level tenant gate (mirror of the sibling IAM screens).
 */

export type CellStatus = 'ok' | 'forbidden' | 'degraded';

/** Operators card — total + ACTIVE/SUSPENDED split. `status` is the total leg's
 *  outcome (the card gate); the split counts are null-tolerant sub-legs. */
export interface OperatorsSummary {
  total: number | null;
  active: number | null;
  suspended: number | null;
  status: CellStatus;
}

/** Accounts card — active-tenant-scoped total (no lock/suspended breakdown:
 *  the accounts search endpoint has no status filter). */
export interface AccountsSummary {
  total: number | null;
  status: CellStatus;
}

/** Audit·security card — total events in scope + the recent 5 rows. */
export interface AuditSummary {
  total: number | null;
  recent: AuditRow[] | null;
  status: CellStatus;
}

export interface IamOverviewState {
  /** True when no tenant is selected — page-level gate, no fan-out was run. */
  noActiveTenant: boolean;
  operators: OperatorsSummary;
  accounts: AccountsSummary;
  audit: AuditSummary;
}

const EMPTY: Omit<IamOverviewState, 'noActiveTenant'> = {
  operators: { total: null, active: null, suspended: null, status: 'degraded' },
  accounts: { total: null, status: 'degraded' },
  audit: { total: null, recent: null, status: 'degraded' },
};

/** Recent-activity page size (audit). */
const RECENT_SIZE = 5;

interface Cell<T> {
  value: T | null;
  status: CellStatus;
}

/**
 * Resolve a single fan-out leg into a cell: success → `ok`; `ApiError 403`
 * (`PERMISSION_DENIED`/`TENANT_SCOPE_DENIED`) → `forbidden`; any
 * `*UnavailableError` (503/timeout/network) or other error → `degraded`. An
 * `ApiError 401` is RE-THROWN so the caller performs a whole-session
 * `redirect('/login')` (never a per-cell degrade).
 */
async function cell<T>(p: Promise<T>): Promise<Cell<T>> {
  try {
    return { value: await p, status: 'ok' };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      throw err; // whole-session re-login — propagate, do not degrade.
    }
    if (err instanceof ApiError && err.status === 403) {
      return { value: null, status: 'forbidden' };
    }
    return { value: null, status: 'degraded' };
  }
}

export async function getIamOverviewState(): Promise<IamOverviewState> {
  // All three legs require an active tenant — short-circuit to one page-level
  // gate instead of three identical NO_ACTIVE_TENANT per-cell messages.
  const tenant = await getActiveTenant();
  if (!tenant) {
    return { noActiveTenant: true, ...EMPTY };
  }

  try {
    const [
      operatorsTotalCell,
      operatorsActiveCell,
      operatorsSuspendedCell,
      accountsCell,
      auditCell,
    ] = await Promise.all([
      cell(listOperators({ page: 0, size: 1 })),
      cell(listOperators({ status: 'ACTIVE', page: 0, size: 1 })),
      cell(listOperators({ status: 'SUSPENDED', page: 0, size: 1 })),
      cell(searchAccounts({ page: 0, size: 1 })),
      cell(queryAudit({ page: 0, size: RECENT_SIZE })),
    ]);

    const operators: OperatorsSummary = {
      total: operatorsTotalCell.value?.totalElements ?? null,
      active: operatorsActiveCell.value?.totalElements ?? null,
      suspended: operatorsSuspendedCell.value?.totalElements ?? null,
      status: operatorsTotalCell.status,
    };

    const accounts: AccountsSummary = {
      total: accountsCell.value?.totalElements ?? null,
      status: accountsCell.status,
    };

    const audit: AuditSummary = {
      total: auditCell.value?.totalElements ?? null,
      recent: auditCell.value?.content.slice(0, RECENT_SIZE) ?? null,
      status: auditCell.status,
    };

    return { noActiveTenant: false, operators, accounts, audit };
  } catch (err) {
    // Only a `401` re-thrown by a cell reaches here → whole-session re-login.
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    throw err;
  }
}
