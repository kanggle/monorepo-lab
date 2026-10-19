import { ApiError } from '@/shared/api/errors';

/**
 * What a dashboard composition answers — TASK-PC-FE-350.
 *
 * The status code and JSON body the route handler puts on the wire, as a plain
 * value. Two callers read it:
 *
 *   - the route handler (`app/api/console/dashboards/<x>/route.ts`) turns it into
 *     `NextResponse.json(body, { status })` for the browser (`<RetryButton>` /
 *     React Query) — byte-for-byte what the route answered before;
 *   - the SSR state function (`features/<x>/api/<x>-state.ts`) reads it
 *     in-process. It used to fetch the route over HTTP at this app's own origin;
 *     that hop added a name lookup and undici's 10 s connect timeout, which
 *     queued behind a saturated libuv threadpool (TASK-PC-FE-350 § AC-0).
 *
 * 🔴 Keeping the answer a value — not a `Response` — is what lets both callers
 *    share ONE function, so the browser path and the SSR path cannot drift.
 */
export interface CompositionAnswer {
  status: number;
  body: unknown;
}

/**
 * The non-200 answer as the `ApiError` the SSR path used to get from the HTTP
 * hop (`readErrorEnvelope` in `features/<x>/api/<x>-api.ts` — same defaults), so
 * the state function's `400 NO_ACTIVE_TENANT` / `401` / other branches keep
 * meaning exactly what they meant.
 */
export function answerError(answer: CompositionAnswer): ApiError {
  let code = `HTTP_${answer.status}`;
  let message = 'composition request failed';
  const body = answer.body as { code?: unknown; message?: unknown } | null;
  if (body && typeof body === 'object') {
    if (typeof body.code === 'string') code = body.code;
    if (typeof body.message === 'string') message = body.message;
  }
  return new ApiError(answer.status, code, message);
}
