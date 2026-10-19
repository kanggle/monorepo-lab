import { NextResponse } from 'next/server';
import { composeDomainHealth } from '@/shared/composition/domain-health-composition';

export const runtime = 'nodejs';

/**
 * Domain Health Overview — `console-integration-contract.md` § 2.4.9.2.
 *
 * Produced in the console-web server since TASK-PC-FE-302 (ADR-MONO-081, A;
 * contract § 2.4.9) instead of proxying to the former BFF (retired —
 * ADR-MONO-081), which the Vercel console could not reach. The wire envelope
 * is unchanged.
 *
 * TASK-PC-FE-350 — the composition (sample gate, inbound checks, the six public
 * health legs) lives in `composeDomainHealth()`, which the SSR pages also call
 * in-process. This route is the BROWSER's entry (`<RetryButton>` / React Query)
 * and only puts that answer on the wire — same status codes, same bodies.
 *
 * READ-ONLY: GET only.
 */
export async function GET() {
  const answer = await composeDomainHealth();
  return NextResponse.json(answer.body, { status: answer.status });
}
