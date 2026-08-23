import { NextResponse } from 'next/server';
import { decideVerdict, type ProbeResult } from './verdict';

/**
 * ⏳ TEMPORARY — TASK-MONO-571 / ADR-MONO-067 AC-0 ②. Delete after measuring (AC-5).
 *
 * Question: can a Vercel serverless function make a PLAINTEXT HTTP upstream call?
 * The ADR picked (B) "browser talks only to Vercel over HTTPS, the Next server proxies to the
 * plaintext-HTTP demo backend" — and that proxy half has never once been exercised. If it turns
 * out to be false, the ADR does not hold and needs a superseding decision.
 *
 * The running launcher is NOT evidence for this: it reads the IP from /status and builds a link,
 * and the browser then performs a top-level navigation, which is outside the mixed-content rule
 * entirely. That proves the discovery half, not the proxy half.
 *
 * Why three cells and not one:
 *
 *   - `plaintextA` / `plaintextB` are the SUBJECT, from two independent hosts. With one host,
 *     "that host is down" and "plaintext is blocked" produce the same output.
 *   - `httpsControl` is the POSITIVE CONTROL. A runtime with no egress at all and a runtime that
 *     blocks only plaintext look identical without it — the measurement would be unjudgeable.
 *
 * Why `redirect: 'manual'`: a server answering `301 -> https://` is NOT "the plaintext upstream
 * worked". Node's fetch follows redirects by default, which would return 200 and read as success.
 * The verdict is true only for a 2xx reached WITHOUT a redirect, so the raw status and `location`
 * are reported rather than a boolean.
 *
 * `runtime = 'nodejs'` is pinned deliberately: edge-runtime fetch has different constraints, and
 * the proxy (B) would actually ship is nodejs. Measure the thing you will use.
 */

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

async function probe(url: string): Promise<ProbeResult> {
  try {
    const res = await fetch(url, {
      redirect: 'manual',
      cache: 'no-store',
      headers: { 'User-Agent': 'monorepo-lab-ac0-probe/1 (TASK-MONO-571)' },
    });
    return {
      url,
      ok: res.status >= 200 && res.status < 300,
      status: res.status,
      location: res.headers.get('location'),
      error: null,
    };
  } catch (e) {
    const err = e as Error & { cause?: { code?: string } };
    return {
      url,
      ok: false,
      status: null,
      location: null,
      error: {
        name: err.name,
        message: err.message,
        code: err.cause?.code ?? null,
      },
    };
  }
}

export async function GET() {
  const [plaintextA, plaintextB, httpsControl] = await Promise.all([
    probe('http://neverssl.com/'),
    probe('http://example.com/'),
    probe('https://example.com/'),
  ]);

  return NextResponse.json({
    task: 'TASK-MONO-571',
    adr: 'ADR-MONO-067 AC-0 (2)',
    verdict: decideVerdict({ plaintextA, plaintextB, httpsControl }),
    // Stated so a passing result is not over-read: this measures Vercel's ability to speak
    // plaintext HTTP, NOT that our EC2 demo is reachable from it.
    notMeasured: ['sslip.io DNS resolution', 'EC2 security-group ingress from Vercel egress', 'non-80 ports'],
    cells: { plaintextA, plaintextB, httpsControl },
  });
}
