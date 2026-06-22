import type { NextRequest } from 'next/server';

/**
 * Core Web Vitals beacon sink.
 *
 * Receives the JSON payload from the client reporter (`web-vitals.tsx`) and
 * emits one structured server log line per metric. This is the collection
 * endpoint of the performance baseline — the log shape (`type: 'web-vitals'`)
 * is intentionally flat so it can be scraped into Vector / VictoriaMetrics
 * without further parsing. Always answers 204 (fire-and-forget beacon); a
 * malformed body is swallowed rather than surfaced to the client.
 */
export async function POST(request: NextRequest): Promise<Response> {
  try {
    const text = await request.text();
    if (text) {
      const metric = JSON.parse(text) as Record<string, unknown>;
      // eslint-disable-next-line no-console
      console.log(JSON.stringify({ type: 'web-vitals', ...metric }));
    }
  } catch {
    // Ignore malformed beacons — never fail an analytics write.
  }
  return new Response(null, { status: 204 });
}
