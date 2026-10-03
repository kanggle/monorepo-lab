import { test, expect, type APIRequestContext } from '@playwright/test';

/**
 * Federation distributed-trace propagation spec (ADR-MONO-018 D4 — MONO-143
 * trace foundation: VictoriaTraces + OTLP direct export, ADR-007a D1/D2).
 *
 * 🔵 TASK-PC-FE-302 (ADR-MONO-081, A) moved the Operator Overview composition
 * into the console-web server. The middle hop this spec used to gate
 * (console-web → the former BFF → producers) no longer exists, so
 * the gates are re-stated on the path that does: console-web → producers.
 * The INVARIANTS are the same ones MONO-144/145/146/147 put in place; only the
 * middle hop is gone:
 *
 *   (attribution)  the console-web trace of an overview request carries one
 *                  span per fan-out leg, tagged `composition.domain` +
 *                  `composition.route` (was the former BFF's `bff.domain` /
 *                  `bff.route` — MONO-147; contract § 2.4.9 Observability).
 *   (unified tree) that SAME trace_id also carries >= 1 producer server span —
 *                  console-web's auto-instrumented `fetch` injects a W3C
 *                  `traceparent` the producer honours (was MONO-145's
 *                  producer-join + MONO-146's unified-tree gates; with no
 *                  virtual-thread hop in between there is only one tree).
 *
 * The report (logged + attached) is written BEFORE the asserts so a failure
 * carries the evidence of what did and did not propagate.
 */

const VT_BASE = (
  process.env.E2E_VICTORIATRACES_URL ?? 'http://localhost:10428'
).replace(/\/$/, '');

/** console-web otel-node `OTEL_SERVICE_NAME` (default 'console-web'). */
const WEB_SERVICE = 'console-web';
const OVERVIEW_API = '/api/console/dashboards/operator-overview';
/** `shared/composition/console-composition.ts` — the per-leg span. */
const LEG_SPAN = 'console.composition.leg';
const DOMAIN_TAG = 'composition.domain';
const ROUTE_TAG = 'composition.route';

/**
 * OTLP-exporting producers reachable from the overview fan-out — diagnostic
 * only. The gate counts any service that is not console-web, so a renamed or
 * additional producer still counts.
 */
const KNOWN_PRODUCERS = [
  'scm-platform-gateway-service', // scm (console-web reaches scm through it)
  'scm-platform-inventory-visibility-service',
  'finance-platform-account-service', // finance
  'erp-platform-masterdata-service', // erp
];

interface JaegerTag {
  key: string;
  value: unknown;
}
interface JaegerSpan {
  traceID: string;
  spanID: string;
  operationName: string;
  processID: string;
  startTime: number;
  tags?: JaegerTag[];
}
interface JaegerTrace {
  traceID: string;
  spans: JaegerSpan[];
  processes: Record<string, { serviceName: string }>;
}

/** `composition.domain` of every overview leg span in one trace. */
function legDomains(trace: JaegerTrace): string[] {
  const domains = new Set<string>();
  for (const span of trace.spans ?? []) {
    if (span.operationName !== LEG_SPAN) continue;
    const tags = span.tags ?? [];
    const domain = tags.find((t) => t.key === DOMAIN_TAG);
    const route = tags.find((t) => t.key === ROUTE_TAG);
    if (domain && route && String(route.value) === 'operator-overview') {
      domains.add(String(domain.value));
    }
  }
  return [...domains];
}

/** serviceName -> span count for one trace, via the processID -> process map. */
function serviceSpanCounts(trace: JaegerTrace): Map<string, number> {
  const counts = new Map<string, number>();
  for (const span of trace.spans ?? []) {
    const svc = trace.processes?.[span.processID]?.serviceName ?? '(unknown)';
    counts.set(svc, (counts.get(svc) ?? 0) + 1);
  }
  return counts;
}

/** Producer services in one trace = any service that is not console-web / unknown. */
function producersIn(services: Map<string, number>): string[] {
  return [...services.keys()].filter((s) => s !== WEB_SERVICE && s !== '(unknown)');
}

/** Jaeger-compat search for traces containing a console-web span. */
async function searchWebTraces(request: APIRequestContext): Promise<JaegerTrace[]> {
  const nowMs = Date.now();
  const startUs = (nowMs - 3_600_000) * 1000; // last 1h
  const endUs = (nowMs + 60_000) * 1000; // +1m clock skew slack
  const url =
    `${VT_BASE}/select/jaeger/api/traces` +
    `?service=${encodeURIComponent(WEB_SERVICE)}` +
    `&start=${startUs}&end=${endUs}&limit=100&lookback=1h`;
  const res = await request.get(url);
  if (!res.ok()) return [];
  const body = await res.json().catch(() => ({}));
  return Array.isArray(body?.data) ? (body.data as JaegerTrace[]) : [];
}

interface TraceView {
  trace: JaegerTrace;
  services: Map<string, number>;
  legs: string[];
}

test.describe('Federation distributed-trace propagation (ADR-018 D4)', () => {
  test('one console-web trace_id carries the per-leg spans AND >= 1 producer server span', async ({
    page,
    request,
  }, testInfo) => {
    // OTLP batch flush (~5 s) + ingest + index; deadline + prelude fit in one
    // attempt (MONO-144 cycle 2: deadline > test timeout → flaky pass-on-retry).
    test.setTimeout(180_000);

    // 1. Drive the overview — navigate (realistic path) + an explicit same-
    //    context request (deterministic composition trigger). The SUPER_ADMIN
    //    storage state (tenant_id='*') is accepted by every producer; each
    //    producer forms a server span regardless of the leg's HTTP outcome.
    await page.goto('/dashboards/overview');
    await page.waitForLoadState('networkidle');
    const apiRes = await page.request.get(OVERVIEW_API);
    console.log(`[trace-tree] operator-overview status=${apiRes.status()}`);

    // 2. Ingested services (diagnostic).
    const servicesRes = await request.get(`${VT_BASE}/select/jaeger/api/services`);
    const servicesBody = servicesRes.ok() ? await servicesRes.json().catch(() => ({})) : {};
    const ingestedServices: string[] = Array.isArray(servicesBody?.data)
      ? servicesBody.data
      : [];
    console.log(`[trace-tree] ingested services: ${JSON.stringify(ingestedServices)}`);

    // 3. Poll console-web traces. Keep the overview trace (has leg spans) with
    //    the most producers; stop once one carries legs AND a producer.
    let best: TraceView | null = null;
    const producerUnion = new Set<string>();
    const deadline = Date.now() + 110_000;
    while (Date.now() < deadline) {
      for (const trace of await searchWebTraces(request)) {
        const legs = legDomains(trace);
        if (legs.length === 0) continue;
        const services = serviceSpanCounts(trace);
        const producers = producersIn(services);
        producers.forEach((p) => producerUnion.add(p));
        const better =
          !best ||
          producers.length > producersIn(best.services).length ||
          (producers.length === producersIn(best.services).length &&
            legs.length > best.legs.length);
        if (better) best = { trace, services, legs };
      }
      if (best && producersIn(best.services).length > 0) break;
      await page.waitForTimeout(3_000);
    }

    // 4. Report before asserting.
    const report = {
      victoriaTracesBase: VT_BASE,
      ingestedServices,
      overviewTrace: best
        ? {
            traceId: best.trace.traceID,
            totalSpans: best.trace.spans?.length ?? 0,
            serviceSpanCounts: Object.fromEntries(best.services),
            legDomains: best.legs,
            producers: producersIn(best.services),
          }
        : null,
      producerUnion: [...producerUnion],
      knownProducersExpected: KNOWN_PRODUCERS,
    };
    console.log(`[trace-tree] propagation report:\n${JSON.stringify(report, null, 2)}`);
    await testInfo.attach('trace-propagation-report.json', {
      body: JSON.stringify(report, null, 2),
      contentType: 'application/json',
    });

    // 5a. Attribution gate — the overview trace carries per-leg spans.
    expect(
      best,
      `no console-web trace with a ${LEG_SPAN} span (tags ${DOMAIN_TAG} + ${ROUTE_TAG}=operator-overview) ` +
        `within the flush window — ingested services were ${JSON.stringify(ingestedServices)}. ` +
        'If console-web is present but no leg span is, either the composition no longer wraps ' +
        'legs in a span or the tags did not survive the OTLP → VictoriaTraces → Jaeger round-trip.',
    ).not.toBeNull();
    expect(best!.legs.length).toBeGreaterThanOrEqual(1);

    // 5b. Unified-tree gate — the SAME trace_id carries >= 1 producer span.
    const producers = producersIn(best!.services);
    expect(
      producers.length >= 1,
      `overview trace ${best!.trace.traceID} must carry >= 1 producer server span ` +
        '(console-web fetch → producer W3C traceparent). ' +
        `got services: ${[...best!.services.keys()].join(', ')}; ` +
        `producerUnion=${JSON.stringify([...producerUnion])}.`,
    ).toBe(true);
  });
});
