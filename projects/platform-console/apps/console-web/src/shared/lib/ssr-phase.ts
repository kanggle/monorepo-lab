import { logger } from '@/shared/lib/logger';

/**
 * Per-phase SSR timing (TASK-PC-FE-341 — instrumentation only, no behaviour).
 *
 * Why: the nightly console e2e showed `/dashboards/overview` documents taking
 * ~10 s with a 4 s floor from the composition legs; `console_composition_leg`
 * only times the legs, so the remaining ~6 s had no owner. One
 * `console_ssr_phase` line per server-side phase of the render (the shell
 * layout's data fetches, the page's overview/catalog/health fetches, and the
 * layout / page totals) lets a later nightly say WHICH phase held the time.
 *
 * Fields — `route`, `phase`, `status`, `latencyMs`, `startedAt` (ISO, when the
 * phase began; the logger's own `ts` is when it ended). On a throw also
 * `httpStatus` (a numeric `status` on the error, e.g. `ApiError`) and
 * `errorName`. 🔴 R1, same as `console_composition_leg`: NO token, account id,
 * email, tenant id, cookie, URL or body. `route` is a fixed label chosen at the
 * call site, never the request path (paths carry ids).
 *
 * 🔴 There is no request id here: a server layout / page cannot see the one the
 * composition route mints (the page reaches it over an in-process fetch), so
 * correlation to `console_composition_leg` is by `ts` + `latencyMs`.
 *
 * The wrapper calls `fn` synchronously and hands back its outcome unchanged
 * (same value, same rethrown error) — it adds no await in front of `fn`, no
 * timeout and no retry.
 */

export type SsrPhaseStatus = 'ok' | 'error' | 'redirect';

function errorFields(err: unknown): { status: SsrPhaseStatus; extra: Record<string, unknown> } {
  const e = err as { digest?: unknown; status?: unknown; name?: unknown } | null;
  if (e && typeof e.digest === 'string' && e.digest.startsWith('NEXT_REDIRECT')) {
    // `redirect()` is control flow, not a failure.
    return { status: 'redirect', extra: {} };
  }
  const extra: Record<string, unknown> = {};
  if (e && typeof e.status === 'number') extra.httpStatus = e.status;
  if (e && typeof e.name === 'string') extra.errorName = e.name;
  return { status: 'error', extra };
}

export async function timedPhase<T>(
  route: string,
  phase: string,
  fn: () => Promise<T>,
): Promise<T> {
  const startedAtMs = Date.now();
  const emit = (status: SsrPhaseStatus, extra: Record<string, unknown>) => {
    const fields = {
      route,
      phase,
      status,
      latencyMs: Date.now() - startedAtMs,
      startedAt: new Date(startedAtMs).toISOString(),
      ...extra,
    };
    if (status === 'error') logger.warn('console_ssr_phase', fields);
    else logger.info('console_ssr_phase', fields);
  };
  try {
    const value = await fn();
    emit('ok', {});
    return value;
  } catch (err) {
    const { status, extra } = errorFields(err);
    emit(status, extra);
    throw err;
  }
}
