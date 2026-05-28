/**
 * OpenTelemetry Node SDK bootstrap (ADR-MONO-007a D3). Imported dynamically
 * by `instrumentation.ts` only in the nodejs runtime.
 *
 * - Trace origination: starts the root SSR span for inbound operator
 *   requests; the undici (native `fetch`) auto-instrumentation injects W3C
 *   `traceparent` into the outbound console-bff call so the BFF adopts it as
 *   parent instead of starting a new root.
 * - Exporter: OTLP/HTTP to the Vector OTLP source (ADR-007a D2), URL from
 *   `OTEL_EXPORTER_OTLP_ENDPOINT`. When unset, NO SDK is started (no-op,
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
