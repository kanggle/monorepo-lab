import { test, expect, type APIRequestContext } from '@playwright/test';

/**
 * TASK-MONO-145 — Federation distributed-trace propagation spec
 * (producer-join regression gate). ADR-MONO-018 D4 follow-up, built on the
 * MONO-143 trace foundation (VictoriaTraces + OTLP direct export — ADR-007a
 * D1/D2) and the MONO-144 trace-tree assertion.
 *
 * Drives one Operator Overview fan-out (console-web SSR → console-bff
 * aggregation → per-domain producers), then polls VictoriaTraces' Jaeger-compat
 * query API across ALL console-bff-bearing traces and gates TWO proven,
 * deterministic propagation invariants:
 *
 *   (floor)         one trace_id with the console-web SSR root + the console-bff
 *                   aggregation span (console-web → console-bff propagation —
 *                   the MONO-144 gate).
 *   (producer-join) one trace_id co-assembling a console-bff span + >= 1
 *                   producer server span (console-bff → producer W3C
 *                   `traceparent` propagation — the MONO-144 "observed ceiling"
 *                   lifted into a gate).
 *
 * WHY a SEPARATE producer-join search (MONO-144 → MONO-145).  MONO-144 only
 * inspected the single *richest* trace and reported `producerServiceCount=0`,
 * concluding the producers do not join.  But the console-bff → producer hop IS
 * wired in src: `RestClientConfig` injects the `ObservationRegistry` into every
 * per-domain `RestClient.Builder`, so micrometer-tracing-bridge-otel injects a
 * W3C `traceparent` on every outbound producer call.  The fan-out
 * (`CompositionEngine.fanOut`) runs each leg on its own Java 21 virtual thread
 * (`Executors.newVirtualThreadPerTaskExecutor()` + `CompletableFuture`); the
 * inbound OTel context is a ThreadLocal NOT propagated to those worker threads,
 * so each leg's outbound client observation roots a FRESH trace_id carrying the
 * console-bff client span + the producer server span.  Those per-leg traces are
 * the deterministic proof of the console-bff → producer hop — MONO-144 never
 * searched for them.  This spec searches ALL console-bff traces and gates on
 * that join WITHOUT any console-bff src change (AC-3).
 *
 * Honest-scope (MONO-140/144 precedent).  Unifying the producer spans under
 * console-web's SINGLE trace_id (the literal ~7-span tree) would require
 * virtual-thread OTel context propagation in `CompositionEngine` (src) — out of
 * scope here.  The spec REPORTS whether the unified tree was reached (producers
 * present in the console-web → console-bff trace); if only per-leg join is
 * observed, that residual is the documented ceiling, not a failure.
 */

const VT_BASE = (
  process.env.E2E_VICTORIATRACES_URL ?? 'http://localhost:10428'
).replace(/\/$/, '');

/** console-bff `spring.application.name` (OTLP resource service.name). */
const BFF_SERVICE = 'platform-console-console-bff';
/** console-web otel-node `OTEL_SERVICE_NAME` (default 'console-web'). */
const WEB_SERVICE = 'console-web';
const OVERVIEW_API = '/api/console/dashboards/operator-overview';

/**
 * Known OTLP-exporting producer service.names reachable from the
 * Operator Overview fan-out (diagnostic only — the gate uses the general
 * "any service that is not console-web / console-bff" rule so a renamed or
 * additional producer still counts). GAP/admin-service has no OTLP exporter
 * (no span); the finance leg short-circuits without an account-id header.
 */
const KNOWN_PRODUCERS = [
  'master-service', // wms
  'scm-platform-procurement-service', // scm
  'finance-platform-account-service', // finance
  'erp-platform-masterdata-service', // erp
];

interface JaegerSpan {
  traceID: string;
  spanID: string;
  operationName: string;
  processID: string;
  startTime: number;
}
interface JaegerTrace {
  traceID: string;
  spans: JaegerSpan[];
  processes: Record<string, { serviceName: string }>;
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

/** Producer services in one trace = any service that is not web / bff / unknown. */
function producersIn(services: Map<string, number>): string[] {
  return [...services.keys()].filter(
    (s) => s !== WEB_SERVICE && s !== BFF_SERVICE && s !== '(unknown)',
  );
}

/** Jaeger-compat search for traces containing a console-bff span. */
async function searchBffTraces(
  request: APIRequestContext,
): Promise<JaegerTrace[]> {
  const nowMs = Date.now();
  const startUs = (nowMs - 3_600_000) * 1000; // last 1h
  const endUs = (nowMs + 60_000) * 1000; // +1m clock skew slack
  const url =
    `${VT_BASE}/select/jaeger/api/traces` +
    `?service=${encodeURIComponent(BFF_SERVICE)}` +
    `&start=${startUs}&end=${endUs}&limit=100&lookback=1h`;
  const res = await request.get(url);
  if (!res.ok()) return [];
  const body = await res.json().catch(() => ({}));
  return Array.isArray(body?.data) ? (body.data as JaegerTrace[]) : [];
}

interface TraceView {
  trace: JaegerTrace;
  services: Map<string, number>;
}

test.describe('Federation distributed-trace propagation (ADR-018 D4)', () => {
  test('one trace_id carries the console-web SSR + console-bff propagation AND a console-bff -> producer join', async ({
    page,
    request,
  }, testInfo) => {
    // The producer-join poll needs OTLP batch flush (~5s) + ingest + index for
    // BOTH the console-web -> console-bff unified trace AND >= 1 per-leg
    // console-bff -> producer trace. Deadline (110s) plus the navigation/discovery
    // prelude must fit inside one attempt (MONO-144 cycle 2: deadline > test
    // timeout -> killed mid-poll -> flaky pass-on-retry). 180s leaves headroom.
    test.setTimeout(180_000);

    // 1. Drive the Operator Overview fan-out. Navigate (realistic operator
    //    path) + an explicit same-context API request (deterministic
    //    SSR -> BFF -> producer trigger). The storage-state SUPER_ADMIN session
    //    (tenant_id='*', accepted by all producers) makes console-bff resolve
    //    credentials and fan out to the live producers; each producer forms a
    //    receiving server span regardless of the leg's HTTP outcome.
    await page.goto('/dashboards/overview');
    await page.waitForLoadState('networkidle');
    const apiRes = await page.request.get(OVERVIEW_API);
    console.log(`[MONO-145] operator-overview proxy status=${apiRes.status()}`);

    // 2. Discover ingested services (diagnostic — surfaces which producers
    //    exported at all this run).
    const servicesRes = await request.get(
      `${VT_BASE}/select/jaeger/api/services`,
    );
    const servicesBody = servicesRes.ok()
      ? await servicesRes.json().catch(() => ({}))
      : {};
    const ingestedServices: string[] = Array.isArray(servicesBody?.data)
      ? servicesBody.data
      : [];
    console.log(
      `[MONO-145] VictoriaTraces ingested services: ${JSON.stringify(ingestedServices)}`,
    );

    // 3. Full-window poll across ALL console-bff traces. Track, separately:
    //    - bestUnified:      richest trace carrying BOTH console-web + console-bff
    //                        (the console-web -> console-bff floor).
    //    - bestProducerJoin: trace carrying console-bff + the MOST distinct
    //                        producers (the console-bff -> producer gate).
    //    - producerUnion:    every producer service seen sharing a trace_id with
    //                        console-bff across ALL traces (per-leg fan-out forks
    //                        each leg into its own trace_id, so the union is the
    //                        full set that propagated).
    //    Unlike MONO-144 this does NOT break at the 2-service floor — it waits
    //    for a producer to join a console-bff trace too.
    let bestUnified: TraceView | null = null;
    let bestProducerJoin: TraceView | null = null;
    const producerUnion = new Set<string>();
    const deadline = Date.now() + 110_000;
    while (Date.now() < deadline) {
      for (const trace of await searchBffTraces(request)) {
        const services = serviceSpanCounts(trace);
        if (!services.has(BFF_SERVICE)) continue;

        if (services.has(WEB_SERVICE)) {
          const richer =
            !bestUnified ||
            (trace.spans?.length ?? 0) > (bestUnified.trace.spans?.length ?? 0);
          if (richer) bestUnified = { trace, services };
        }

        const producers = producersIn(services);
        producers.forEach((p) => producerUnion.add(p));
        if (producers.length > 0) {
          const moreProducers =
            !bestProducerJoin ||
            producers.length > producersIn(bestProducerJoin.services).length ||
            (producers.length ===
              producersIn(bestProducerJoin.services).length &&
              (trace.spans?.length ?? 0) >
                (bestProducerJoin.trace.spans?.length ?? 0));
          if (moreProducers) bestProducerJoin = { trace, services };
        }
      }
      // Both invariants satisfied → stop early.
      if (bestUnified && bestProducerJoin) break;
      await page.waitForTimeout(3_000);
    }

    // 4. Report (artifact + log) BEFORE asserting, so a failure carries the full
    //    evidence of what did / did not propagate.
    const unifiedTreeProducerCount = bestUnified
      ? producersIn(bestUnified.services).length
      : 0;
    const report = {
      victoriaTracesBase: VT_BASE,
      ingestedServices,
      unified: bestUnified
        ? {
            traceId: bestUnified.trace.traceID,
            totalSpans: bestUnified.trace.spans?.length ?? 0,
            serviceSpanCounts: Object.fromEntries(bestUnified.services),
            consoleWebRootPresent: bestUnified.services.has(WEB_SERVICE),
          }
        : null,
      producerJoin: bestProducerJoin
        ? {
            traceId: bestProducerJoin.trace.traceID,
            totalSpans: bestProducerJoin.trace.spans?.length ?? 0,
            serviceSpanCounts: Object.fromEntries(bestProducerJoin.services),
            producers: producersIn(bestProducerJoin.services),
            rootIsConsoleBff: !bestProducerJoin.services.has(WEB_SERVICE),
          }
        : null,
      producerUnion: [...producerUnion],
      knownProducersExpected: KNOWN_PRODUCERS,
      // The residual ceiling: did the producers join the UNIFIED console-web
      // trace (full ~7-span tree), or only per-leg console-bff-rooted traces?
      unifiedTreeReached: unifiedTreeProducerCount > 0,
      ceilingNote:
        unifiedTreeProducerCount > 0
          ? 'Producers joined the unified console-web trace_id (full tree reached).'
          : 'Producers join per-leg console-bff-rooted trace_ids; unifying them under console-web\'s single trace_id needs virtual-thread OTel context propagation in CompositionEngine (src, out of scope) — documented ceiling.',
    };
    console.log(
      `[MONO-145] propagation report:\n${JSON.stringify(report, null, 2)}`,
    );
    await testInfo.attach('mono-145-trace-propagation-report.json', {
      body: JSON.stringify(report, null, 2),
      contentType: 'application/json',
    });

    // 5a. Floor gate — console-web SSR -> console-bff propagation (MONO-144).
    expect(
      bestUnified,
      'no console-web + console-bff trace found within the flush window — ' +
        `ingested services were ${JSON.stringify(ingestedServices)}`,
    ).not.toBeNull();
    const unified = bestUnified!.services;
    expect(
      unified.has(BFF_SERVICE) && unified.has(WEB_SERVICE),
      `unified trace ${bestUnified!.trace.traceID} must carry BOTH console-web (SSR root) and console-bff — proves console-web -> console-bff W3C traceparent propagation + cross-format ingest; got: ${[...unified.keys()].join(', ')}`,
    ).toBe(true);

    // 5b. Producer-join gate — console-bff -> producer propagation (the MONO-144
    //     "observed ceiling" lifted into a regression gate). One trace_id
    //     co-assembling a console-bff span + >= 1 producer server span proves the
    //     RestClientConfig ObservationRegistry wiring injects a traceparent the
    //     producer honors. (Per-leg or unified — searched across ALL bff traces.)
    expect(
      bestProducerJoin,
      'no trace_id co-assembling a console-bff span + >= 1 producer span found ' +
        'within the flush window — console-bff -> producer traceparent propagation ' +
        `did not land. producerUnion=${JSON.stringify([...producerUnion])}, ` +
        `ingestedServices=${JSON.stringify(ingestedServices)}. If this persists ` +
        'under a longer poll it is a real propagation gap (file a console-bff src ' +
        'diagnosis task — AC-5), but RestClientConfig makes per-leg join expected.',
    ).not.toBeNull();
    const joinProducers = producersIn(bestProducerJoin!.services);
    expect(
      bestProducerJoin!.services.has(BFF_SERVICE) && joinProducers.length >= 1,
      `producer-join trace ${bestProducerJoin!.trace.traceID} must carry console-bff + >= 1 producer; got services: ${[...bestProducerJoin!.services.keys()].join(', ')}`,
    ).toBe(true);
  });
});
