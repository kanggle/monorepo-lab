import { NextResponse } from 'next/server';
import { getAccessToken, getActiveTenant } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { sampleGate } from '@/shared/api/sample-gate';
import { compose, domainHealthLegs } from '@/shared/composition/console-composition';
import { resolveBackendUrl } from '@/shared/config/demo-backend';

export const runtime = 'nodejs';

/**
 * Domain Health Overview — `console-integration-contract.md` § 2.4.9.2.
 *
 * Produced in the console-web server since TASK-PC-FE-302 (ADR-MONO-081, A;
 * contract § 2.4.9) instead of proxying to the former BFF (retired —
 * ADR-MONO-081), which the Vercel console could not reach. The wire envelope
 * is unchanged.
 *
 * Six public `/actuator/health` legs, one per domain edge, with NO credential
 * and NO tenant header (§ 2.4.9.2 «D4 scope clarification»). Any leg failure,
 * including a 401/403 from a misconfigured producer, degrades only that card.
 * A 503 carrying a real health document (Spring's DOWN / OUT_OF_SERVICE) is an
 * `ok` card with that status — the producer reporting itself down is not the
 * same as the console failing to reach it.
 *
 * The route keeps its inbound checks: no active tenant → 400, no session →
 * 401, both before any leg. READ-ONLY: GET only.
 */
export async function GET() {
  const requestId = newRequestId();

  // ADR-MONO-074 A2 — a sample visitor gets the sample envelope; no leg runs.
  // Sample core `console-composition` (renamed by TASK-MONO-757).
  const sample = await sampleGate({
    core: 'console-composition',
    surface: 'domain-health',
    method: 'GET',
    path: '/api/console/dashboards/domain-health',
  });
  if (sample) return passthroughSample(sample, requestId);

  // The health legs carry no credential, but the route keeps its inbound
  // checks (§ 2.4.9.2 error envelope): a tenant for traceability, a session.
  const tenant = await getActiveTenant();
  if (!tenant) {
    return NextResponse.json(
      { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' },
      { status: 400 },
    );
  }
  const accessToken = await getAccessToken();
  if (!accessToken) {
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'session not authenticated' },
      { status: 401 },
    );
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
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'session expired' },
      { status: 401 },
    );
  }
  return NextResponse.json(result.envelope, { status: 200 });
}

async function passthroughSample(res: Response, requestId: string): Promise<NextResponse> {
  if (res.status === 200) {
    try {
      return NextResponse.json(await res.json(), { status: 200 });
    } catch {
      /* fall through */
    }
  }
  logger.warn('domain_health_sample_unexpected', { requestId, status: res.status });
  return NextResponse.json(
    { code: 'BAD_GATEWAY', message: 'sample health unavailable' },
    { status: 502 },
  );
}
