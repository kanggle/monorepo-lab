import {
  getAccessToken,
  getDomainFacingToken,
  getOperatorToken,
  getActiveTenant,
} from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { sampleGate } from '@/shared/api/sample-gate';
import { resolveBackendUrl } from '@/shared/config/demo-backend';
import { compose, operatorOverviewLegs } from './console-composition';
import type { CompositionAnswer } from './composition-answer';

/**
 * Operator Overview composition — `console-integration-contract.md` § 2.4.9.1.
 * SERVER-ONLY (reads the session through `next/headers`).
 *
 * TASK-PC-FE-350 — the ONE function both callers run:
 *   - `app/api/console/dashboards/operator-overview/route.ts` (the browser's
 *     `<RetryButton>` / React Query re-query);
 *   - `features/operator-overview/api/operator-overview-state.ts` (the SSR page),
 *     in-process — no HTTP hop to this app's own origin.
 * Before 350 this body lived in the route and the page fetched the route.
 *
 * Six legs, each through the address the domain's own console screen uses
 * (`console-composition.ts`):
 *   - IAM: RFC 8693 operator token + `X-Tenant-Id` (ADR-MONO-017 D4);
 *   - wms / scm / finance / erp / ecommerce: domain-facing IAM OIDC token.
 *   TASK-PC-FE-364 — every card is one real call; the finance card reads the
 *   reconciliation queue, which needs no default account, so this function no
 *   longer reads `getFinanceDefaultAccountId()` (the `/finance` overview still
 *   does).
 *
 * Answers:
 *   - sample visitor → the sample envelope (200), or 502 BAD_GATEWAY if the
 *     sample surface did not answer 200; no leg called;
 *   - no active tenant → 400 NO_ACTIVE_TENANT, no leg called;
 *   - session tokens missing → 401 TOKEN_INVALID, no leg called;
 *   - any data leg 401 → 401 TOKEN_INVALID (cross-leg rule — an expired
 *     session is not a partial outage);
 *   - otherwise 200 — failed legs are per-card `degraded` / `forbidden`,
 *     never a blank dashboard and never 503.
 *
 * READ-ONLY: GET only. Tokens and account ids are never logged.
 */
export async function composeOperatorOverview(): Promise<CompositionAnswer> {
  const requestId = newRequestId();

  // ADR-MONO-074 A2 — asked BEFORE the tenant and token reads. A sample visitor
  // gets the sample overview; no leg is called. Sample core
  // `console-composition` (renamed from the former BFF's name by TASK-MONO-757).
  // 🔴 TASK-PC-FE-350 — this is now the first line for the SSR page too
  //    (`tests/unit/sample-fetch-allowlist.test.ts` pins the order in this file).
  const sample = await sampleGate({
    core: 'console-composition',
    surface: 'operator-overview',
    method: 'GET',
    path: '/api/console/dashboards/operator-overview',
  });
  if (sample) return sampleAnswer(sample, requestId);

  const tenant = await getActiveTenant();
  if (!tenant) {
    // Before any leg — a test asserts zero calls on this path.
    return { status: 400, body: { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' } };
  }

  const accessToken = await getAccessToken();
  const operatorToken = await getOperatorToken();
  const domainFacingToken = await getDomainFacingToken();
  if (!accessToken || !operatorToken || !domainFacingToken) {
    // No partial authed state — mirrors `isAuthenticated()`.
    return { status: 401, body: { code: 'TOKEN_INVALID', message: 'session not authenticated' } };
  }

  const legs = await operatorOverviewLegs({
    tenant,
    operatorToken,
    domainFacingToken,
    requestId,
  });
  const result = await compose(legs, {
    route: 'operator-overview',
    requestId,
    // The demo host is resolved here, at the call site (check-fetch-resolution).
    fetchLeg: async (url, init) => fetch(await resolveBackendUrl(url), init),
  });

  if (result.unauthorized) {
    // Cross-leg 401 rule (§ 2.4.4 D3): one data leg's 401 is the session's.
    return { status: 401, body: { code: 'TOKEN_INVALID', message: 'session expired' } };
  }
  return { status: 200, body: result.envelope };
}

/** The sample surface answers with the same envelope the composition would. */
async function sampleAnswer(res: Response, requestId: string): Promise<CompositionAnswer> {
  if (res.status === 200) {
    try {
      return { status: 200, body: await res.json() };
    } catch {
      /* fall through */
    }
  }
  logger.warn('operator_overview_sample_unexpected', { requestId, status: res.status });
  return { status: 502, body: { code: 'BAD_GATEWAY', message: 'sample overview unavailable' } };
}
