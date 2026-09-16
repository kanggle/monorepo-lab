import { SAMPLE_AS_OF, SAMPLE_NOT_READY, SAMPLE_READ_ONLY } from './codes';
import { findSurfaceCoverage, type SampleCore } from './coverage';
import { SAMPLE_FIXTURES } from './fixtures';

/**
 * The sample router (ADR-MONO-074 A2 / A3).
 *
 * Given the request a gateway core was ABOUT to send, it builds the `Response`
 * that core then feeds into its EXISTING response-handling code — parsers,
 * status mapping and error envelopes run unchanged, which is what makes the
 * sample screens "the real page".
 *
 * 🔴🔴 A3 — this module (and everything under `shared/sample/**`) has no path
 *    to a backend: it does not import `@/shared/config/env`, does not import
 *    `@/shared/lib/session`, and does not call `fetch`. It only CONSTRUCTS a
 *    `Response`. `tests/unit/sample-router-isolation.test.ts` enforces that.
 *
 * Answers:
 *   - non-GET → `403 SAMPLE_READ_ONLY` (R1ⓐ). The body message is plumbing
 *     only; the visitor-facing copy comes from the code (`messageForCode`).
 *   - GET on a `ready` surface with a fixture for the path → `200` fixture,
 *     or `404` (a domain fixture reporting a real "no such id" — TASK-PC-FE-283
 *     AC-3) via {@link fixtureNotFound}.
 *   - any other GET → `503 SAMPLE_NOT_READY` (A9 — section degrade).
 *
 * Error bodies use the envelope the core's parser reads: wms is NESTED
 * (`{ error: { code } }` — a flat body would lose the code), every other core
 * is FLAT (`{ code, message, timestamp }`).
 */

export type { SampleCore } from './coverage';

export interface SampleRequest {
  core: SampleCore;
  /** The gateway profile `logPrefix`, or the proxy route's surface name. */
  surface: string;
  method: string;
  /** Path the core would have called (relative to its base; query allowed). */
  path: string;
}

/**
 * The origin-less path a core was about to call: `http://wms.local/api/v1/admin`
 * + `/dashboard/inventory` → `/api/v1/admin/dashboard/inventory`. Lets fixtures
 * match on a path that does not depend on the deployment's hostnames.
 */
export function samplePath(base: string, path: string): string {
  const basePath = base.replace(/^[a-z][a-z0-9+.-]*:\/\/[^/]*/i, '').replace(/\/+$/, '');
  return `${basePath}${path}`;
}

/** Marks every sample response (diagnostics only — nothing branches on it). */
export const SAMPLE_RESPONSE_HEADER = 'X-Console-Sample';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
      [SAMPLE_RESPONSE_HEADER]: '1',
    },
  });
}

function errorBody(core: SampleCore, code: string, message: string): unknown {
  return core === 'wms'
    ? { error: { code, message, timestamp: SAMPLE_AS_OF } }
    : { code, message, timestamp: SAMPLE_AS_OF };
}

/**
 * A domain fixture's "no such id" answer (TASK-PC-FE-283 AC-3 — a detail
 * lookup on an id absent from the fixture rows must produce the SAME 404
 * shape the real backend produces, not a generic `SAMPLE_NOT_READY`). Built
 * with {@link fixtureNotFound}; the router renders it through the SAME
 * `errorBody` envelope (flat vs wms-nested) every other error uses.
 */
export interface FixtureNotFound {
  readonly notFound: true;
  readonly code: string;
  readonly message: string;
}

export function fixtureNotFound(code: string, message: string): FixtureNotFound {
  return { notFound: true, code, message };
}

function isFixtureNotFound(value: unknown): value is FixtureNotFound {
  return (
    typeof value === 'object' &&
    value !== null &&
    (value as { notFound?: unknown }).notFound === true
  );
}

export function sampleResponse(req: SampleRequest): Response {
  if (req.method.toUpperCase() !== 'GET') {
    return jsonResponse(
      403,
      errorBody(req.core, SAMPLE_READ_ONLY, 'sample visitors cannot write'),
    );
  }

  const coverage = findSurfaceCoverage(req.core, req.surface);
  const fixture = SAMPLE_FIXTURES[`${req.core}:${req.surface}`];
  if (coverage?.status === 'ready' && fixture) {
    // TASK-PC-FE-283 — the FULL path (query string included) is handed to the
    // fixture: a domain fixture applies the screen's own filter/search/page
    // query params over its rows (AC-4). The 4 dashboard/registry fixtures
    // from TASK-PC-FE-282 ignore the argument entirely, so this is
    // backward-compatible with them.
    const result = fixture(req.path);
    if (result !== undefined) {
      if (isFixtureNotFound(result)) {
        return jsonResponse(404, errorBody(req.core, result.code, result.message));
      }
      return jsonResponse(200, result);
    }
  }

  return jsonResponse(
    503,
    errorBody(req.core, SAMPLE_NOT_READY, 'sample data not ready'),
  );
}
