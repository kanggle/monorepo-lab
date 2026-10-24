import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import {
  noTenantNoticeKind,
  type NoTenantNoticeKind,
} from '@/shared/lib/active-tenant-default';
import { ApiError } from '@/shared/api/errors';
import { listOperators } from '@/shared/api/iam-operators-read';
import { searchAccounts } from '@/shared/api/iam-accounts-read';
import { queryAudit } from '@/shared/api/iam-audit-read';
import type { MetricsPeriod } from '@/shared/lib/kst-period';
import {
  performanceMetricsCell,
  type PerformanceCell,
} from '@/shared/api/performance-metrics';
import { getIamMetrics } from './iam-metrics-api';

/**
 * Server-side IAM **operator overview snapshot** fan-out for the `/iam` landing
 * (TASK-PC-FE-180). Splits the former static `/iam` guide (relocated to
 * `/iam/guide`) from a live domain overview — the IAM sibling of the ecommerce
 * `/ecommerce` snapshot (TASK-PC-FE-156 / console-integration-contract §2.4.10.6).
 *
 * ── ARCHITECTURE (console-web direct fan-out; NO new producer) ──
 * Per ADR-MONO-017 D3.B counts are derived from the EXISTING IAM admin-service
 * list endpoints' `totalElements` with `page=0&size=1` — NO `/summary`
 * aggregation endpoint, NO cross-domain composition leg (contrast: the console-wide
 * §2.4.4 / §2.4.9.1 operator overview is a cross-domain composition fan-out — this one is
 * domain-internal, reusing the three EXISTING server reads):
 *
 * TASK-PC-FE-259 — those reads live in
 * `shared/api/iam-{accounts,audit,operators}-read.ts`. Earlier revisions
 * imported them out of `@/features/{operators,accounts,audit}/api/*`, which
 * `architecture.md` § Forbidden Dependencies bars — "같은 계층
 * `features/A → features/B` 상호 참조 금지, 공유 가치는 `shared/` 로 승격".
 * Each feature still re-exports its own read on its § 3.1-pinned public path;
 * the promotion changed WHERE the read is defined, not WHICH read runs.
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
 *
 * ── «실적» strip (ADR-MONO-084 P6, TASK-MONO-811) ──
 * One more cell, `performance` (`GET /api/admin/metrics?period=&tenantId=`):
 * 로그인 성공 · 로그인 실패 · 계정 잠금 · 신규 계정. This is the ADR-MONO-084
 * exception to the «no aggregation endpoint» rule above — the producer counts
 * (D4), the console never sums list pages for it — and it does not replace any
 * card's `totalElements`. Same rule as every leg: a `403` makes it `forbidden`,
 * anything but a `401` `degraded`, and neither touches the cards below it. It
 * needs the active tenant too (sent as the `tenantId` query param), so with no
 * tenant it is not called.
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

/** Accounts card — active-tenant-scoped total + LOCKED count (TASK-PC-FE-181,
 *  via the TASK-BE-475 `status` filter). `status` is the total leg's outcome (the
 *  card gate); `locked` is a null-tolerant sub-leg (its own degrade/forbidden). */
export interface AccountsSummary {
  total: number | null;
  locked: number | null;
  status: CellStatus;
}

/**
 * One recent audit row **as this overview consumes it** (TASK-PC-FE-259).
 *
 * The mini-list never narrows the producer's discriminated `AuditRow` union —
 * `IamOverviewAuditCard.auditRowView()` deliberately reads fields off one
 * permissive record view so a producer evolution (a new `source`, a dropped
 * optional field) can never crash the card. Declaring THAT shape here, as the
 * overview's own view-model, is both the honest description of what this
 * feature needs and what keeps it off `features/audit`'s internals
 * (`architecture.md` § Forbidden Dependencies — a producer wire type is not
 * "shared value" just because an aggregator borrowed it). Structurally
 * assignable from every `AuditRow` variant, so the runtime rows and the
 * rendered output are unchanged.
 */
export interface IamOverviewAuditRow {
  source: string;
  auditId?: string;
  eventId?: string;
  actionCode?: string;
  outcome?: string | null;
  occurredAt?: string;
  [key: string]: unknown;
}

/** Audit·security card — total events in scope + the recent 5 rows. */
export interface AuditSummary {
  total: number | null;
  recent: IamOverviewAuditRow[] | null;
  status: CellStatus;
}

export interface IamOverviewState {
  /** True when no tenant is selected — page-level gate, no fan-out was run. */
  noActiveTenant: boolean;
  /**
   * Only meaningful when {@link noActiveTenant} is true (TASK-PC-FE-301).
   * Resolved server-side (not by the presentational `IamOverviewScreen`,
   * which is sync and unit-tested with the client-side RTL renderer — it
   * cannot itself await {@link noTenantNoticeKind}) and threaded down so the
   * screen renders the SAME `NoTenantNoticeBody` every other gated screen
   * uses, instead of its own copy of the two paragraphs.
   */
  tenantNoticeKind?: NoTenantNoticeKind;
  operators: OperatorsSummary;
  accounts: AccountsSummary;
  audit: AuditSummary;
  /** The period the «실적» strip shows (`?period=`, default `month`). */
  period: MetricsPeriod;
  /** The «실적» strip (TASK-MONO-811) — its own cell. */
  performance: PerformanceCell;
}

/** ADR-MONO-084 OD-4: week / month; the overview opens on the month. */
export const DEFAULT_OVERVIEW_PERIOD: MetricsPeriod = 'month';

const EMPTY: Omit<IamOverviewState, 'noActiveTenant' | 'period'> = {
  operators: { total: null, active: null, suspended: null, status: 'degraded' },
  accounts: { total: null, locked: null, status: 'degraded' },
  audit: { total: null, recent: null, status: 'degraded' },
  performance: { status: 'degraded', value: null },
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

export async function getIamOverviewState(
  period: MetricsPeriod = DEFAULT_OVERVIEW_PERIOD,
): Promise<IamOverviewState> {
  // All three legs require an active tenant — short-circuit to one page-level
  // gate instead of three identical NO_ACTIVE_TENANT per-cell messages.
  const tenant = await getActiveTenant();
  if (!tenant) {
    return {
      noActiveTenant: true,
      tenantNoticeKind: await noTenantNoticeKind(),
      ...EMPTY,
      period,
    };
  }

  try {
    const [
      operatorsTotalCell,
      operatorsActiveCell,
      operatorsSuspendedCell,
      accountsCell,
      accountsLockedCell,
      auditCell,
      performance,
    ] = await Promise.all([
      cell(listOperators({ page: 0, size: 1 })),
      cell(listOperators({ status: 'ACTIVE', page: 0, size: 1 })),
      cell(listOperators({ status: 'SUSPENDED', page: 0, size: 1 })),
      cell(searchAccounts({ page: 0, size: 1 })),
      // TASK-PC-FE-181: LOCKED count via the TASK-BE-475 status filter (own sub-leg
      // so a degrade here never blanks the total).
      cell(searchAccounts({ status: 'LOCKED', page: 0, size: 1 })),
      cell(queryAudit({ page: 0, size: RECENT_SIZE })),
      // TASK-MONO-811 — 401 re-thrown like every leg; anything else degrades
      // the strip only.
      performanceMetricsCell(getIamMetrics(period, tenant)),
    ]);

    const operators: OperatorsSummary = {
      total: operatorsTotalCell.value?.totalElements ?? null,
      active: operatorsActiveCell.value?.totalElements ?? null,
      suspended: operatorsSuspendedCell.value?.totalElements ?? null,
      status: operatorsTotalCell.status,
    };

    const accounts: AccountsSummary = {
      total: accountsCell.value?.totalElements ?? null,
      locked: accountsLockedCell.value?.totalElements ?? null,
      status: accountsCell.status,
    };

    const audit: AuditSummary = {
      total: auditCell.value?.totalElements ?? null,
      recent: auditCell.value?.content.slice(0, RECENT_SIZE) ?? null,
      status: auditCell.status,
    };

    return { noActiveTenant: false, operators, accounts, audit, period, performance };
  } catch (err) {
    // Only a `401` re-thrown by a cell reaches here → whole-session re-login.
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login?error=session_expired');
    }
    throw err;
  }
}
