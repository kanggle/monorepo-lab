import { NextResponse } from 'next/server';
import {
  getAccessToken,
  getDomainFacingToken,
  getOperatorToken,
  getActiveTenant,
} from '@/shared/lib/session';
import { getFinanceDefaultAccountId } from '@/shared/lib/finance-default-account-id';
import { logger, newRequestId } from '@/shared/lib/logger';
import { sampleGate } from '@/shared/api/sample-gate';
import { compose, operatorOverviewLegs } from '@/shared/composition/console-composition';
import { resolveBackendUrl } from '@/shared/config/demo-backend';

export const runtime = 'nodejs';

/**
 * Operator Overview — `console-integration-contract.md` § 2.4.9.1.
 *
 * Produced in the console-web server since TASK-PC-FE-302 (ADR-MONO-081, A;
 * contract § 2.4.9). Before that this route proxied to the former BFF
 * (retired — ADR-MONO-081), which had no public hostname, so the Vercel console
 * could not reach it. The wire envelope is unchanged.
 *
 * Six legs, each through the address the domain's own console screen uses
 * (`shared/composition/console-composition.ts`):
 *   - IAM: RFC 8693 operator token + `X-Tenant-Id` (ADR-MONO-017 D4);
 *   - wms / scm / finance / erp / ecommerce: domain-facing IAM OIDC token;
 *   - finance only when the operator has a default account (option (a),
 *     TASK-PC-FE-014); otherwise `forbidden / MISSING_PREREQUISITE`, no call.
 *
 * Outcomes:
 *   - no active tenant → 400 NO_ACTIVE_TENANT, no leg called;
 *   - session tokens missing → 401 TOKEN_INVALID, no leg called;
 *   - any data leg 401 → 401 TOKEN_INVALID (cross-leg rule — an expired
 *     session is not a partial outage);
 *   - otherwise 200 — failed legs are per-card `degraded` / `forbidden`,
 *     never a blank dashboard and never 503.
 *
 * READ-ONLY: GET only. Tokens and account ids are never logged.
 */
export async function GET() {
  const requestId = newRequestId();

  // ADR-MONO-074 A2 — asked BEFORE the tenant and token reads. A sample visitor
  // gets the sample overview; no leg is called. Sample core
  // `console-composition` (renamed from the former BFF's name by TASK-MONO-757).
  const sample = await sampleGate({
    core: 'console-composition',
    surface: 'operator-overview',
    method: 'GET',
    path: '/api/console/dashboards/operator-overview',
  });
  if (sample) return passthroughSample(sample, requestId);

  const tenant = await getActiveTenant();
  if (!tenant) {
    // Before any leg — a test asserts zero calls on this path.
    return NextResponse.json(
      { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' },
      { status: 400 },
    );
  }

  const accessToken = await getAccessToken();
  const operatorToken = await getOperatorToken();
  const domainFacingToken = await getDomainFacingToken();
  if (!accessToken || !operatorToken || !domainFacingToken) {
    // No partial authed state — mirrors `isAuthenticated()`.
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'session not authenticated' },
      { status: 401 },
    );
  }

  const legs = await operatorOverviewLegs({
    tenant,
    operatorToken,
    domainFacingToken,
    financeDefaultAccountId: await getFinanceDefaultAccountId(),
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
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'session expired' },
      { status: 401 },
    );
  }
  return NextResponse.json(result.envelope, { status: 200 });
}

/** The sample surface answers with the same envelope the composition would. */
async function passthroughSample(res: Response, requestId: string): Promise<NextResponse> {
  if (res.status === 200) {
    try {
      return NextResponse.json(await res.json(), { status: 200 });
    } catch {
      /* fall through */
    }
  }
  logger.warn('operator_overview_sample_unexpected', { requestId, status: res.status });
  return NextResponse.json(
    { code: 'BAD_GATEWAY', message: 'sample overview unavailable' },
    { status: 502 },
  );
}
