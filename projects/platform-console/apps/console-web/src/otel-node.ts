/**
 * OpenTelemetry Node SDK bootstrap (ADR-MONO-007a D3). Imported dynamically
 * by `instrumentation.ts` only in the nodejs runtime.
 *
 * - Trace origination: starts the root SSR span for inbound operator
 *   requests; the undici (native `fetch`) auto-instrumentation injects W3C
 *   `traceparent` into the outbound console-bff call so the BFF adopts it as
 *   parent instead of starting a new root.
 * - Exporter: OTLP/HTTP directly to VictoriaTraces, URL from
 *   `OTEL_EXPORTER_OTLP_ENDPOINT`. NOTE: ADR-007a D2 decided this leg would
 *   route through the Vector OTLP source, but the shipped topology exports
 *   direct to VictoriaTraces, bypassing Vector — Vector 0.45 has no
 *   `opentelemetry` sink, so traces cannot route through the spine yet (see
 *   ADR-MONO-007a D2's as-built deviation note; logs + metrics still flow
 *   through Vector). When unset, NO SDK is started (no-op,
 *   ADR-007a D4 — production / `next dev` without the stack is unaffected).
 * - `X-Request-Id` (set explicitly in the route handlers) is retained for
 *   log correlation alongside the trace context — they are complementary.
 */
import { NodeSDK } from '@opentelemetry/sdk-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { resourceFromAttributes } from '@opentelemetry/resources';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';

const endpoint = process.env.OTEL_EXPORTER_OTLP_ENDPOINT?.trim();

if (endpoint) {
  const sdk = new NodeSDK({
    resource: resourceFromAttributes({
      [ATTR_SERVICE_NAME]: process.env.OTEL_SERVICE_NAME?.trim() || 'console-web',
    }),
    traceExporter: new OTLPTraceExporter({
      url: `${endpoint.replace(/\/$/, '')}/v1/traces`,
    }),
    instrumentations: [
      getNodeAutoInstrumentations({
        // The console-web concern is the SSR→BFF fetch span only. Disable the
        // filesystem instrumentation (noisy, irrelevant to the trace tree).
        '@opentelemetry/instrumentation-fs': { enabled: false },
      }),
    ],
  });
  sdk.start();

  process.once('SIGTERM', () => {
    void sdk.shutdown().finally(() => process.exit(0));
  });
}
