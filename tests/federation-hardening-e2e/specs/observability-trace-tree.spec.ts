import { test, expect, type APIRequestContext } from '@playwright/test';

/**
 * TASK-MONO-144 — Federation distributed-trace propagation spec.
 * ADR-MONO-018 D4 cross-product verification, built on the MONO-143 trace
 * foundation (VictoriaTraces backend + OTLP direct export — ADR-MONO-007a
 * D1/D2).
 *
 * Drives one Operator Overview fan-out (console-web SSR → console-bff
 * aggregation → per-domain producers), then polls VictoriaTraces' Jaeger-compat
 * query API and asserts the federation PROPAGATION INVARIANT:
 *
 *     one trace_id spanning >= 2 distinct services (console-bff + >= 1 other).
 *
 * Honest-scope gate (tasks/.../TASK-MONO-144 § Honest-scope note, MONO-140
 * precedent): the literal 7-span tree (console-web + console-bff + 5 producers)
 * is the *observed ceiling*, not the gate, because —
 *   (1) console-web's @opentelemetry/exporter-trace-otlp-http emits OTLP/JSON
 *       and whether VictoriaTraces ingests OTLP/JSON is unconfirmed; AC-6
 *       forbids swapping the console-web exporter package. The console-web ->
 *       console-bff W3C traceparent is injected by the undici instrumentation
 *       regardless of console-web's own export success, so the trace_id still
 *       originates at console-web even if its root span is dropped at ingest.
 *   (2) the producer span count rides the MONO-140-deferred BFF fan-out.
 * The spec therefore REPORTS how close the live trace gets (service list,
 * per-service span counts, console-web root presence, producer count) and
 * gates only on the protobuf-reliable propagation invariant.
 */

const VT_BASE = (
  process.env.E2E_VICTORIATRACES_URL ?? 'http://localhost:10428'
).replace(/\/$/, '');

/** console-bff `spring.application.name` (OTLP resource service.name). */
const BFF_SERVICE = 'platform-console-console-bff';
/** console-web otel-node `OTEL_SERVICE_NAME` (default 'console-web'). */
const WEB_SERVICE = 'console-web';
const OVERVIEW_API = '/api/console/dashboards/operator-overview';

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
    `&start=${startUs}&end=${endUs}&limit=50&lookback=1h`;
  const res = await request.get(url);
  if (!res.ok()) return [];
  const body = await res.json().catch(() => ({}));
  return Array.isArray(body?.data) ? (body.data as JaegerTrace[]) : [];
}

test.describe('Federation distributed-trace propagation (ADR-018 D4)', () => {
  test('one trace_id spans the console-web SSR + console-bff propagation', async ({
    page,
    request,
  }, testInfo) => {
    // The poll deadline (75s) for OTLP flush + ingest exceeds Playwright's
    // default 30s per-test timeout (MONO-144 cycle 2: the loop was killed
    // mid-poll at 30s -> flaky pass-on-retry). Raise to 120s so the cold-stack
    // flush window fits inside one attempt.
    test.setTimeout(120_000);

    // 1. Drive the Operator Overview fan-out. Navigate (realistic operator
    //    path) + an explicit same-context API request (deterministic
    //    SSR -> BFF -> producer trigger). The proxy route runs server-side
    //    (nodejs runtime -> otel-node active) regardless of HTTP outcome; a
    //    degraded fan-out still emits the downstream spans (server spans form
    //    on request receipt at console-bff + each producer).
    await page.goto('/dashboards/overview');
    await page.waitForLoadState('networkidle');
    const apiRes = await page.request.get(OVERVIEW_API);
    console.log(`[MONO-144] operator-overview proxy status=${apiRes.status()}`);

    // 2. Discover ingested services (diagnostic — surfaces whether console-web
    //    OTLP/JSON ingested + which producers exported).
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
      `[MONO-144] VictoriaTraces ingested services: ${JSON.stringify(ingestedServices)}`,
    );

    // 3. Poll for the operator-overview trace and wait for the console-web SSR
    //    root span + console-bff aggregation span to both land under one
    //    trace_id. OTLP batch export (~5s) + ingest + index latency -> poll up
    //    to ~75s, keeping the richest trace seen (most distinct services, then
    //    most spans). MONO-144 cycle 2 evidence: console-web (OTLP/JSON) + the
    //    4 OTLP producers + console-bff all ingest; the producer spans land
    //    under their OWN trace_ids (the console-bff -> producer RestClient does
    //    not propagate the inbound W3C context), so the operator-overview trace
    //    is reliably console-web + console-bff. Producer-join would require
    //    console-bff src wiring, which AC-6 forbids here (reported, not gated).
    let best: { trace: JaegerTrace; services: Map<string, number> } | null =
      null;
    const deadline = Date.now() + 75_000;
    while (Date.now() < deadline) {
      for (const trace of await searchBffTraces(request)) {
        const services = serviceSpanCounts(trace);
        const better =
          !best ||
          services.size > best.services.size ||
          (services.size === best.services.size &&
            (trace.spans?.length ?? 0) > (best.trace.spans?.length ?? 0));
        if (better) best = { trace, services };
      }
      if (
        best &&
        best.services.has(BFF_SERVICE) &&
        best.services.has(WEB_SERVICE)
      ) {
        break;
      }
      await page.waitForTimeout(3_000);
    }

    // 4. Report (artifact + log) BEFORE asserting, so a failure carries the
    //    full evidence of what did / did not propagate.
    const report = {
      victoriaTracesBase: VT_BASE,
      ingestedServices,
      bestTraceId: best?.trace.traceID ?? null,
      totalSpans: best ? best.trace.spans?.length ?? 0 : 0,
      distinctServiceSpanCounts: best ? Object.fromEntries(best.services) : {},
      consoleWebRootPresent: best ? best.services.has(WEB_SERVICE) : false,
      producerServiceCount: best
        ? [...best.services.keys()].filter(
            (s) => s !== WEB_SERVICE && s !== BFF_SERVICE,
          ).length
        : 0,
      sevenSpanCeilingNote:
        'console-web + console-bff + 5 producers = 7 spans is the observed ceiling, not the gate (see TASK-MONO-144 honest-scope note).',
    };
    console.log(
      `[MONO-144] propagation report:\n${JSON.stringify(report, null, 2)}`,
    );
    await testInfo.attach('mono-144-trace-propagation-report.json', {
      body: JSON.stringify(report, null, 2),
      contentType: 'application/json',
    });

    // 5. Gate: the console-web SSR -> console-bff propagation invariant — one
    //    trace_id carrying both the console-web root span and the console-bff
    //    aggregation span (>= 2 distinct services).
    expect(
      best,
      'no console-bff trace found in VictoriaTraces within the flush window — ' +
        `ingested services were ${JSON.stringify(ingestedServices)}`,
    ).not.toBeNull();
    const services = best!.services;
    const serviceList = [...services.keys()].join(', ');
    expect(
      services.has(BFF_SERVICE),
      `trace ${best!.trace.traceID} must include console-bff (${BFF_SERVICE}); got: ${serviceList}`,
    ).toBe(true);
    expect(
      services.has(WEB_SERVICE),
      `trace ${best!.trace.traceID} must include the console-web SSR root (${WEB_SERVICE}) — proves console-web -> console-bff W3C traceparent propagation + OTLP/JSON ingest; got: ${serviceList}`,
    ).toBe(true);
    expect(
      services.size,
      `trace ${best!.trace.traceID} must span >=2 distinct services (propagation invariant); got: ${serviceList}`,
    ).toBeGreaterThanOrEqual(2);
    expect(
      best!.trace.spans?.length ?? 0,
      'trace must contain >=2 spans',
    ).toBeGreaterThanOrEqual(2);
  });
});
