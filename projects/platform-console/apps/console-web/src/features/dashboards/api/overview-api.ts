import { searchAccounts } from '@/features/accounts/api/accounts-api';
import { queryAudit } from '@/features/audit/api/audit-api';
import { listOperators } from '@/features/operators/api/operators-api';
import {
  ApiError,
  AccountsUnavailableError,
  AuditUnavailableError,
  OperatorsUnavailableError,
} from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';
import {
  type OperatorOverview,
  type AccountsSummary,
  type AuditActivitySummary,
  type OperatorsSummary,
  type CardStatus,
} from './types';

/**
 * Server-side GAP composed operator overview (TASK-PC-FE-005 — ADR-MONO-013
 * Phase 2 slice 4 / ADR-MONO-015 D1-B).
 *
 * Server-only by construction (same posture as `accounts-api.ts` /
 * `audit-api.ts` / `operators-api.ts`): imported exclusively from server
 * components and the `runtime = 'nodejs'` proxy route. The operator token +
 * source PII never reach client JS — client components call the same-origin
 * `/api/dashboards` proxy, which composes server-side.
 *
 * NO NEW PRODUCER (ADR-MONO-015 D1): this is a **bounded fan-out** over the
 * EXISTING FE-002/003/004 server clients (`searchAccounts` / `queryAudit` /
 * `listOperators`). It does NOT duplicate or invent a GAP client — every
 * hardened call site (operator-token bearer via `getOperatorToken()` —
 * NEVER `getAccessToken()`, `X-Tenant-Id` from `getActiveTenant()`,
 * AbortController hard timeout, structured no-PII logging) is inherited
 * unchanged from those clients. This file is ONLY the composition +
 * per-source isolation + the overview view-model.
 *
 * READ-ONLY (§ 2.4.4 — mirrors the FE-003 read discipline): every leg is a
 * GET; there is NO `X-Operator-Reason`, NO `Idempotency-Key`, NO confirm
 * scaffolding anywhere (the reused clients send none on these read paths —
 * `searchAccounts`/`queryAudit`/`listOperators` are all read GETs; carrying
 * over FE-002/004 mutation patterns here would be a defect).
 *
 * PER-SOURCE ISOLATION (the key design point — § 2.4.4 / ADR-015 D3):
 *   - a leg's `*UnavailableError` (503 / CIRCUIT_OPEN / timeout / network)
 *     → that card only is `degraded` (the overview + shell stay intact);
 *   - a leg's `ApiError` with status `403` → that card only is `forbidden`
 *     ("not available to your role" — operators: non-`operator.manage`;
 *     audit: the § 2.4.2 intersection-permission for the security subset);
 *   - **a leg's `ApiError` with status `401` → it is RE-THROWN as a
 *     whole-overview auth failure** (the operator token is shared across
 *     all legs, so a 401 on one is a 401 for all). 401 is NEVER a
 *     per-card degrade — there is no partial authed state (§ 2.4.4 / task
 *     Failure Scenario "A 401 silently degraded as a per-card error").
 *
 * BOUNDED + PRODUCER-META-AUDIT-RESPECTING (integration-heavy I1 /
 * audit-heavy A5): each leg has the reused client's explicit timeout (no
 * unbounded default). The audit leg is meta-audited producer-side, so ONE
 * overview load issues exactly ONE bounded set of calls — no aggressive
 * polling / auto-refetch / N+1 (the hook enforces no refetch loop).
 *
 * Logging: structured, server-side only; tokens + source PII (account ids,
 * masked IPs, operator emails) are NEVER logged — only per-card statuses
 * and the request id (§ 2.6 logging invariant, inherited).
 */

/** A small bounded slice — the overview shows counts + a tiny recent
 *  snapshot, never a full list. Keeping these small bounds the fan-out and
 *  respects the producer meta-audit (one small call per leg per load). */
const OVERVIEW_PAGE = 0;
const OVERVIEW_SAMPLE_SIZE = 20;

/**
 * Wraps one fan-out leg with per-source isolation.
 *
 * - resolves to `{ ok }` on success;
 * - resolves to `{ degraded }` on the reused client's `*UnavailableError`;
 * - resolves to `{ forbidden }` on a `403` `ApiError`;
 * - **RE-THROWS** a `401` `ApiError` (whole-overview auth failure — never
 *   a per-card degrade) and re-throws an unexpected non-degrade error as
 *   a degrade (a degraded card never crashes the overview).
 *
 * The `unavailable` type guard list is the union of the three reused
 * clients' degrade signals — extending the fan-out with another reused
 * client only needs its degrade error added here.
 */
async function isolateLeg<T>(
  card: 'accounts' | 'audit' | 'operators',
  requestId: string,
  fetchLeg: () => Promise<T>,
  onOk: (data: T) => void,
  setStatus: (status: CardStatus) => void,
): Promise<void> {
  try {
    const data = await fetchLeg();
    setStatus('ok');
    onOk(data);
  } catch (err) {
    // 401 on ANY leg ⇒ whole-overview re-login (NOT a per-card degrade).
    // The operator token is shared across all legs — a 401 on one is a
    // 401 for all; re-throw so the page/proxy forces a clean re-login.
    if (err instanceof ApiError && err.status === 401) {
      logger.warn('overview_leg_unauthorized', { requestId, card });
      throw err;
    }
    // 403 (PERMISSION_DENIED / TENANT_SCOPE_DENIED) ⇒ this card only is
    // "not available to your role" — not a crash, not a re-login.
    if (err instanceof ApiError && err.status === 403) {
      logger.info('overview_leg_forbidden', {
        requestId,
        card,
        code: err.code,
      });
      setStatus('forbidden');
      return;
    }
    // Any reused-client degrade signal (503 / CIRCUIT_OPEN / timeout /
    // network) ⇒ this card only degrades — the overview + shell intact.
    if (
      err instanceof AccountsUnavailableError ||
      err instanceof AuditUnavailableError ||
      err instanceof OperatorsUnavailableError
    ) {
      logger.warn('overview_leg_degraded', {
        requestId,
        card,
        reason: err.reason,
      });
      setStatus('degraded');
      return;
    }
    // NO_ACTIVE_TENANT is a pre-flight gate handled by the caller before
    // the fan-out; if it somehow reaches here, treat as a degrade (never
    // crash the whole overview because of one leg). Any other unexpected
    // error → degrade this card only.
    logger.warn('overview_leg_unexpected', { requestId, card });
    setStatus('degraded');
  }
}

/**
 * Composes the operator overview by a single bounded fan-out over the
 * existing FE-002/003/004 read clients. One call per load (no auto-refetch
 * — the hook owns that). Per-source isolated; a `401` from any leg
 * propagates as an `ApiError(401)` (whole-overview re-login).
 */
export async function getOperatorOverview(): Promise<OperatorOverview> {
  const requestId = newRequestId();

  // Defaults: every card is present (degraded card = a status, never a
  // missing key / a throw) so the UI renders the full shell even all-down.
  const accounts: AccountsSummary = {
    status: 'degraded',
    totalElements: null,
    sampleCount: null,
  };
  const audit: AuditActivitySummary = {
    status: 'degraded',
    totalElements: null,
    recentCount: null,
    latestOccurredAt: null,
  };
  const operators: OperatorsSummary = {
    status: 'degraded',
    totalElements: null,
    activeCount: null,
    suspendedCount: null,
  };

  // Bounded fan-out: one small call per leg, in parallel, each leg
  // inheriting its reused client's explicit AbortController timeout (no
  // unbounded default). `Promise.all` so it is exactly ONE bounded set per
  // load. A 401 from ANY isolateLeg rejects the whole `Promise.all` (the
  // re-throw) → whole-overview re-login (no partial authed state). Every
  // other leg outcome is captured in-place (per-source isolation).
  await Promise.all([
    isolateLeg(
      'accounts',
      requestId,
      () =>
        searchAccounts({ page: OVERVIEW_PAGE, size: OVERVIEW_SAMPLE_SIZE }),
      (page) => {
        accounts.totalElements = page.totalElements;
        accounts.sampleCount = page.content.length;
      },
      (s) => {
        accounts.status = s;
      },
    ),
    isolateLeg(
      'audit',
      requestId,
      () =>
        queryAudit({ page: OVERVIEW_PAGE, size: OVERVIEW_SAMPLE_SIZE }),
      (page) => {
        audit.totalElements = page.totalElements;
        audit.recentCount = page.content.length;
        // Only a non-PII timestamp is surfaced — never an audit-row's
        // account id / masked IP / geo (§ 2.4.2 / § 2.4.4 PII discipline).
        const first = page.content[0] as
          | { occurredAt?: string }
          | undefined;
        audit.latestOccurredAt =
          typeof first?.occurredAt === 'string' ? first.occurredAt : null;
      },
      (s) => {
        audit.status = s;
      },
    ),
    isolateLeg(
      'operators',
      requestId,
      () =>
        listOperators({ page: OVERVIEW_PAGE, size: OVERVIEW_SAMPLE_SIZE }),
      (page) => {
        operators.totalElements = page.totalElements;
        operators.activeCount = page.content.filter(
          (o) => o.status === 'ACTIVE',
        ).length;
        operators.suspendedCount = page.content.filter(
          (o) => o.status === 'SUSPENDED',
        ).length;
      },
      (s) => {
        operators.status = s;
      },
    ),
  ]);

  logger.info('overview_composed', {
    requestId,
    accounts: accounts.status,
    audit: audit.status,
    operators: operators.status,
  });

  return { accounts, audit, operators };
}
