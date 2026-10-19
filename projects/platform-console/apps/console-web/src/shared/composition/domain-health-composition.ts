import { getAccessToken, getActiveTenant } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { sampleGate } from '@/shared/api/sample-gate';
import { resolveBackendUrl } from '@/shared/config/demo-backend';
import { compose, domainHealthLegs } from './console-composition';
import type { CompositionAnswer } from './composition-answer';

/**
 * Domain Health Overview composition — `console-integration-contract.md`
 * § 2.4.9.2. SERVER-ONLY (reads the session through `next/headers`).
 *
 * TASK-PC-FE-350 — the ONE function both callers run:
 *   - `app/api/console/dashboards/domain-health/route.ts` (the browser's
 *     `<RetryButton>` / React Query re-query);
 *   - `features/domain-health/api/domain-health-state.ts` (the SSR pages),
 *     in-process — no HTTP hop to this app's own origin.
 *
 * Six public `/actuator/health` legs, one per domain edge, with NO credential
 * and NO tenant header (§ 2.4.9.2 «D4 scope clarification»). Any leg failure,
 * including a 401/403 from a misconfigured producer, degrades only that card.
 * A 503 carrying a real health document (Spring's DOWN / OUT_OF_SERVICE) is an
 * `ok` card with that status — the producer reporting itself down is not the
 * same as the console failing to reach it.
 *
 * Inbound checks, both before any leg: no active tenant → 400, no session →
 * 401. A sample visitor gets the sample envelope (or 502 if the sample surface
 * did not answer 200). READ-ONLY: GET only.
 */
export async function composeDomainHealth(): Promise<CompositionAnswer> {
  const requestId = newRequestId();

  // ADR-MONO-074 A2 — a sample visitor gets the sample envelope; no leg runs.
  // Sample core `console-composition` (renamed by TASK-MONO-757).
  // 🔴 TASK-PC-FE-350 — first line for the SSR pages too (sample-fetch-allowlist pins it).
  const sample = await sampleGate({
    core: 'console-composition',
    surface: 'domain-health',
    method: 'GET',
    path: '/api/console/dashboards/domain-health',
  });
  if (sample) return sampleAnswer(sample, requestId);

  // The health legs carry no credential, but the composition keeps its inbound
  // checks (§ 2.4.9.2 error envelope): a tenant for traceability, a session.
  const tenant = await getActiveTenant();
  if (!tenant) {
    return { status: 400, body: { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' } };
  }
  const accessToken = await getAccessToken();
  if (!accessToken) {
    return { status: 401, body: { code: 'TOKEN_INVALID', message: 'session not authenticated' } };
  }

  const result = await compose(await domainHealthLegs(requestId), {
    route: 'domain-health',
    requestId,
    // The demo host is resolved here, at the call site (check-fetch-resolution).
    fetchLeg: async (url, init) => fetch(await resolveBackendUrl(url), init),
  });
  // Health legs are `kind: 'health'` — a 401 from one degrades that card and
  // never reaches here as `unauthorized` (§ 2.4.9.2 «no cross-leg collapse»).
  if (result.unauthorized) {
    return { status: 401, body: { code: 'TOKEN_INVALID', message: 'session expired' } };
  }
  return { status: 200, body: result.envelope };
}

async function sampleAnswer(res: Response, requestId: string): Promise<CompositionAnswer> {
  if (res.status === 200) {
    try {
      return { status: 200, body: await res.json() };
    } catch {
      /* fall through */
    }
  }
  logger.warn('domain_health_sample_unexpected', { requestId, status: res.status });
  return { status: 502, body: { code: 'BAD_GATEWAY', message: 'sample health unavailable' } };
}
