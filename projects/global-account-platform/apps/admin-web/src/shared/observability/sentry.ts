// Sentry placeholder. The admin-web scaffold does not ship a live Sentry client
// yet — this module exists so callers can import a stable entry point and the
// real SDK can be wired in once the DSN + ingest infra are provisioned.
//
// TODO: Wire Sentry when SENTRY_DSN env is provisioned.
// In production, initialize with:
//   Sentry.init({ dsn: process.env.NEXT_PUBLIC_SENTRY_DSN, tracesSampleRate: 0.1 })
// See specs/services/admin-web/observability.md.

export function initSentry(): void {
  // no-op placeholder
}

export function captureException(_err: unknown): void {
  // no-op placeholder
}
