import { NextResponse } from 'next/server';
import { composeOperatorOverview } from '@/shared/composition/operator-overview-composition';

export const runtime = 'nodejs';

/**
 * Operator Overview — `console-integration-contract.md` § 2.4.9.1.
 *
 * Produced in the console-web server since TASK-PC-FE-302 (ADR-MONO-081, A;
 * contract § 2.4.9). Before that this route proxied to the former BFF
 * (retired — ADR-MONO-081), which had no public hostname, so the Vercel console
 * could not reach it. The wire envelope is unchanged.
 *
 * TASK-PC-FE-350 — the composition itself (sample gate, inbound checks, the six
 * legs, the cross-leg 401 rule) lives in `composeOperatorOverview()`, which the
 * SSR page also calls in-process. This route is the BROWSER's entry
 * (`<RetryButton>` / React Query) and only puts that answer on the wire — same
 * status codes, same bodies as before.
 *
 * READ-ONLY: GET only.
 */
export async function GET() {
  const answer = await composeOperatorOverview();
  return NextResponse.json(answer.body, { status: answer.status });
}
