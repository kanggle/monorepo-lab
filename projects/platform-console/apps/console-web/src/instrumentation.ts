/**
 * Next.js 15 instrumentation hook (ADR-MONO-007a D3 — console-web trace
 * origination).
 *
 * Registers the OpenTelemetry Node SDK so the SSR route handlers that
 * proxy console-bff (operator-overview / domain-health) start the root
 * span of the cross-product trace tree and auto-inject W3C `traceparent`
 * into the outbound `fetch` to console-bff. console-bff + the 5 per-domain
 * producers already adopt the incoming `traceparent` via their Spring Boot
 * OTel auto-instrumentation, so the tree assembles end-to-end
 * (console-web SSR → console-bff aggregation → 5 producer spans = 7 spans)
 * and exports directly to VictoriaTraces (ADR-007a D1). NOTE: D2 decided the
 * OTLP leg would route through the Vector source, but the shipped topology
 * bypasses Vector — Vector 0.45 has no `opentelemetry` sink (see ADR-MONO-007a
 * D2's as-built deviation note; logs + metrics still flow through Vector).
 *
 * No-op when `OTEL_EXPORTER_OTLP_ENDPOINT` is unset — production / `next dev`
 * without the observability stack is unaffected (ADR-007a D4). The heavy SDK
 * import is dynamic + nodejs-runtime-gated so the edge runtime never bundles it.
 */
export async function register(): Promise<void> {
  if (process.env.NEXT_RUNTIME === 'nodejs') {
    await import('./otel-node');
  }
}
