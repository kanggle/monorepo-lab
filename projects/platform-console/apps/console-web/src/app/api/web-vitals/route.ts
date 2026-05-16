import { NextResponse } from 'next/server';
import { logger } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * web-vitals sink (frontend-app.md § Observability — LCP/INP/CLS/TTFB).
 * Beacon target for `useWebVitals()`. Emits a structured log line the
 * observability backend can scrape; no PII, fire-and-forget.
 */
export async function POST(req: Request) {
  try {
    const metric = (await req.json()) as Record<string, unknown>;
    logger.info('web_vital', {
      name: metric.name,
      value: metric.value,
      rating: metric.rating,
      id: metric.id,
    });
  } catch {
    /* ignore malformed beacon */
  }
  return new NextResponse(null, { status: 204 });
}
