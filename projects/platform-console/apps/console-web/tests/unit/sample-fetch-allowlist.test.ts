/**
 * 🔴🔴 Every network call site in `src/` is on this list — and the ones that can
 *      reach a backend sit BEHIND the sample branch (TASK-PC-FE-282 AC-5).
 *
 * ADR-MONO-074 moved the safety of `(console)` from «the route guard keeps
 * anonymous visitors out» to «anonymous visitors come in, but every backend call
 * site answers them from the sample router». Under that design a NEW `fetch(`
 * outside the branch is exactly the hole: an anonymous visitor reaches it with
 * no token, the backend answers 401, and the visitor lands in a re-login loop.
 * (Not a data leak — no token, no data — but the regression this guard exists
 * to stop.)
 *
 * 🔵 This is a different axis from `scripts/check-fetch-resolution.mjs`, which
 *    asks «does this call resolve its backend URL?». This file asks «is this call
 *    behind the sample branch, or provably unreachable for a sample visitor?».
 *    Neither replaces the other.
 *
 * Two assertions:
 *   1. the per-file count of network primitives equals ALLOWED exactly (a new
 *      site → red; a removed site → red, so the list cannot go stale);
 *   2. in each GATED file, `sampleGate(` appears BEFORE the first token read,
 *      tenant read, registry read, token exchange and `fetch(`.
 */
import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';

const SRC = path.resolve(__dirname, '..', '..', 'src');

/**
 * A call, not a mention: `fetch(` not preceded by an identifier char, `.`, `$`,
 * or a quote/backtick (doc comments write `` `fetch()` `` — those are prose).
 */
const NETWORK_PRIMITIVES: RegExp[] = [
  /(?<![\w.$`'"])fetch\s*\(/g,
  /\bnew\s+XMLHttpRequest\s*\(/g,
  /\bsendBeacon\s*\(/g,
  /\b(?:globalThis|window|self)\.fetch\b/g,
];

type Category =
  /** A gateway core / console-bff proxy: the call is behind `sampleGate(`. */
  | 'gated'
  /** Same-origin call to this app's own route handlers (which are gated). */
  | 'same-origin'
  /** The OIDC login/refresh/logout mechanism — the path OUT of anonymity. */
  | 'auth-flow'
  /** Only reachable from a state a sample visitor cannot be in. */
  | 'unreachable-for-sample';

const ALLOWED: Record<string, { count: number; category: Category; why: string }> = {
  // ── gated (ADR-MONO-074 A2) ────────────────────────────────────────────────
  'shared/api/iam-gateway.ts': { count: 1, category: 'gated', why: 'callAdminGateway' },
  'shared/api/registry-client.ts': { count: 1, category: 'gated', why: 'fetchRegistry' },
  'shared/api/wms-gateway.ts': { count: 1, category: 'gated', why: 'callWmsGateway' },
  'shared/api/ecommerce-gateway.ts': { count: 1, category: 'gated', why: 'callEcommerceGateway' },
  'shared/api/flat-envelope-gateway.ts': {
    count: 1,
    category: 'gated',
    why: 'callFlatEnvelopeGateway (callScmGateway shims onto it)',
  },
  'app/api/console/dashboards/operator-overview/route.ts': {
    count: 1,
    category: 'gated',
    why: 'console-bff operator overview',
  },
  'app/api/console/dashboards/domain-health/route.ts': {
    count: 1,
    category: 'gated',
    why: 'console-bff domain health',
  },
  'app/api/console/notifications/inbox/route.ts': {
    count: 1,
    category: 'gated',
    why: 'console-bff notification inbox',
  },
  'app/api/console/notifications/[sourceDomain]/[id]/read/route.ts': {
    count: 1,
    category: 'gated',
    why: 'console-bff mark-read',
  },

  // ── same-origin (this app's route handlers, which are gated) ───────────────
  'shared/api/client.ts': {
    count: 2,
    category: 'same-origin',
    why: 'apiClient + /api/auth/refresh — the single client entry point',
  },
  'features/operator-overview/api/operator-overview-api.ts': {
    count: 1,
    category: 'same-origin',
    why: '/api/console/dashboards/operator-overview',
  },
  'features/domain-health/api/domain-health-api.ts': {
    count: 1,
    category: 'same-origin',
    why: '/api/console/dashboards/domain-health',
  },
  'features/operators/api/account-existence.ts': {
    count: 1,
    category: 'same-origin',
    why: '/api/accounts (iam core, gated)',
  },
  'shared/lib/logout.ts': { count: 1, category: 'same-origin', why: '/api/auth/logout' },
  'shared/observability/web-vitals.tsx': {
    count: 2,
    category: 'same-origin',
    why: '/api/web-vitals (sendBeacon + fetch fallback) — telemetry, no backend',
  },
  'widgets/demo-heartbeat/DemoHeartbeat.tsx': {
    count: 1,
    category: 'same-origin',
    why: '/api/demo/heartbeat — NOT mounted for a sample visitor (ADR-MONO-071 D8); the route re-checks isAuthenticated',
  },

  // ── auth flow (the way out of anonymity — must NOT be sample-branched) ──────
  'app/api/auth/callback/route.ts': { count: 1, category: 'auth-flow', why: 'OIDC code → token' },
  'shared/lib/session-refresh.ts': {
    count: 1,
    category: 'auth-flow',
    why: 'OIDC refresh grant (TASK-MONO-674 moved it out of app/api/auth/refresh/route.ts, now 0) — reached only when a refresh cookie exists, i.e. never for a sample visitor',
  },
  'app/api/auth/logout/route.ts': { count: 1, category: 'auth-flow', why: 'OIDC revoke' },
  'shared/lib/operator-token-exchange.ts': {
    count: 1,
    category: 'auth-flow',
    why: 'RFC 8693 operator exchange — called from callback/refresh only',
  },
  'shared/lib/assume-tenant-exchange.ts': {
    count: 1,
    category: 'auth-flow',
    why: 'assume-tenant exchange — called from /api/tenant (gated first) and refresh',
  },

  // ── unreachable for a sample visitor ───────────────────────────────────────
  'features/onboarding/api/onboarding-client.ts': {
    count: 1,
    category: 'unreachable-for-sample',
    why: 'pre-operator only: /api/onboarding requires the access cookie a sample visitor lacks',
  },
  'features/onboarding/components/CreateOrganizationForm.tsx': {
    count: 1,
    category: 'unreachable-for-sample',
    why: 'rendered only by the (onboarding) group, which admits pre-operator sessions only',
  },
  'features/ecommerce-ops/hooks/use-ecommerce-images.ts': {
    count: 1,
    category: 'unreachable-for-sample',
    why: 'presigned S3 PUT (XHR) — its only input is the upload URL minted by a WRITE, which a sample visitor is refused',
  },
};

/**
 * Files whose backend reach must be behind the sample branch. `app/api/tenant`
 * has no direct `fetch(` (it reaches the registry and the token exchange
 * through helpers), so it is listed here rather than derived from ALLOWED.
 */
const GATED_FILES = [
  ...Object.entries(ALLOWED)
    .filter(([, v]) => v.category === 'gated')
    .map(([f]) => f),
  'app/api/tenant/route.ts',
];

/**
 * Backend reach that must come AFTER `sampleGate(` in a gated file. A
 * `function name(` declaration is not a call and is excluded.
 */
const REACH = [
  'fetch',
  'getOperatorToken',
  'getDomainFacingToken',
  'getAccessToken',
  'getActiveTenant',
  'fetchRegistry',
  'exchangeForAssumedToken',
].map((name) => new RegExp(`(?<![\\w.$\`'"])(?<!function\\s)${name}\\s*\\(`));

/**
 * Drop comments before matching — prose like "the overview fetch (TASK-…)" is
 * not a call. Line comments are only stripped when `//` starts the line or
 * follows whitespace, so a `'http://…'` string literal survives.
 */
function stripComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/(^|\s)\/\/.*$/gm, '$1');
}

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const full = path.join(dir, name);
    if (statSync(full).isDirectory()) return walk(full);
    return /\.(ts|tsx)$/.test(name) ? [full] : [];
  });
}

function rel(file: string): string {
  return path.relative(SRC, file).split(path.sep).join('/');
}

function countNetworkPrimitives(source: string): number {
  const code = stripComments(source);
  return NETWORK_PRIMITIVES.reduce((n, re) => n + (code.match(re)?.length ?? 0), 0);
}

describe('network call sites are allow-listed (AC-5)', () => {
  const files = walk(SRC);

  it('🔵 non-vacuity — the walk saw the tree', () => {
    expect(files.length).toBeGreaterThan(500);
  });

  it('🔴🔴 the per-file count of network primitives equals the allow-list, both ways', () => {
    const actual: Record<string, number> = {};
    for (const file of files) {
      const n = countNetworkPrimitives(readFileSync(file, 'utf8'));
      if (n > 0) actual[rel(file)] = n;
    }
    const expected = Object.fromEntries(
      Object.entries(ALLOWED).map(([f, v]) => [f, v.count]),
    );
    expect(
      actual,
      [
        'A network call site appeared, moved or disappeared.',
        'If it can reach a backend, it must sit behind `sampleGate(` (ADR-MONO-074 A2)',
        'and be listed as `gated`; otherwise list it with the reason a sample visitor',
        'cannot reach it. Do not widen a count without that reason.',
      ].join('\n'),
    ).toEqual(expected);
  });
});

describe('gated files ask the sample branch first (AC-2 / AC-5)', () => {
  it.each(GATED_FILES)('%s — `sampleGate(` precedes every backend reach', (file) => {
    const source = stripComments(readFileSync(path.join(SRC, file), 'utf8'));
    const gate = source.indexOf('sampleGate(');
    expect(gate, `${file} has no sampleGate( call`).toBeGreaterThan(-1);
    for (const re of REACH) {
      const m = re.exec(source);
      if (m) {
        expect(
          m.index,
          `${file}: ${re} at ${m.index} comes before sampleGate( at ${gate}`,
        ).toBeGreaterThan(gate);
      }
    }
  });
});
