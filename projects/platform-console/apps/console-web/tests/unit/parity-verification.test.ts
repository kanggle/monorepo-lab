import { describe, it, expect, vi, beforeEach } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import {
  PARITY_MATRIX,
  OVERVIEW_FANOUT_LEGS,
  type ParityRow,
} from './parity-matrix';

/**
 * TASK-PC-FE-006 — consolidated parity-verification suite (ADR-MONO-013
 * Phase 2 slice 5 of 5, the capstone).
 *
 * This is an ATTESTATION layer over the EXISTING, unmodified FE-002..005
 * surface — NOT a re-run of every slice's internals. It iterates the
 * SINGLE machine-readable matrix (`parity-matrix.ts`, which IS
 * `console-integration-contract.md` § 3.1 in executable form) and, per
 * row, asserts the parity-relevant invariants:
 *
 *  - the route module file exists / the feature module exports the
 *    capability (capability is present);
 *  - the capability's server client targets the CORRECT GAP producer
 *    path (the admin-api.md § in the matrix) with the OPERATOR token
 *    (`getOperatorToken()`, NEVER the GAP OIDC access token — the #569
 *    boundary) and `X-Tenant-Id`;
 *  - mutation rows carry the contract-correct headers per § 2.4.1/§ 2.4.3
 *    — including the FE-004 per-endpoint Idempotency-Key NON-uniformity
 *    (create has it; roles/status do NOT);
 *  - read rows carry NO mutation artifacts;
 *  - the `dashboards` row maps to the composed overview (§ 2.4.4),
 *    per-source-isolated, NOT Grafana;
 *  - matrix ↔ test cannot drift (one fixture iterated here AND mirrored
 *    by the § 3.1 spec table — cross-checked below).
 *
 * Honesty / no-green-wash (task AC): a `verified:false` row would mean a
 * real parity gap; this suite asserts every row is `true` AND
 * independently re-derives the truth from the actual surface — it does
 * NOT trust the fixture's `verified` flag blindly. No gap was found.
 *
 * `next/headers` cookies() + getServerEnv() mocked (the established
 * FE-001..005 lane — same harness, same operator/GAP cookie fixtures).
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));

const { ENV } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://gap.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://gap.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://gap.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    GAP_ADMIN_API_BASE: 'http://gap.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import * as accountsApi from '@/features/accounts/api/accounts-api';
import * as auditApi from '@/features/audit/api/audit-api';
import * as operatorsApi from '@/features/operators/api/operators-api';
import * as dashboardsApi from '@/features/dashboards/api/overview-api';
import { resolveConsoleRoute } from '@/features/catalog';
import { OVERVIEW_QUICK_LINKS } from '@/features/dashboards/api/types';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { ApiError } from '@/shared/api/errors';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
} from '@/shared/lib/session';

const GAP_TOKEN_SENTINEL = 'GAP-OIDC-ACCESS-must-not-leak';
const OPERATOR_TOKEN = 'OPERATOR-TOKEN-correct';
const APP_ROOT = path.resolve(__dirname, '../..');

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function noContent() {
  return new Response(null, { status: 204 });
}

/** Generic page/result body the parsers accept across all slices. */
const ACCOUNTS_PAGE = {
  content: [
    {
      id: 'acc-1',
      email: 'a@x.io',
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  totalElements: 1,
  page: 0,
  size: 20,
  totalPages: 1,
};
const AUDIT_PAGE = {
  content: [
    {
      source: 'admin',
      auditId: 'aud-1',
      actionCode: 'ACCOUNT_LOCK',
      operatorId: 'op-1',
      targetId: 'acc-1',
      reason: 'fraud',
      outcome: 'SUCCESS',
      occurredAt: '2026-04-12T10:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};
const OPERATORS_PAGE = {
  content: [
    {
      operatorId: 'op-1',
      email: 'op@x.io',
      displayName: 'Op One',
      status: 'ACTIVE',
      roles: ['SUPPORT_LOCK'],
      createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  totalElements: 1,
  page: 0,
  size: 20,
  totalPages: 1,
};

/** Routes the mocked fetch to a parser-valid body by URL. */
function routedFetch() {
  return vi.fn((url: string) => {
    const u = String(url);
    if (u.includes('/api/admin/operators/me/password')) {
      return Promise.resolve(noContent());
    }
    if (u.includes('/api/admin/operators/me/profile')) {
      return Promise.resolve(noContent());
    }
    if (u.includes('/api/admin/operators/') && u.includes('/profile')) {
      // admin-on-behalf-of (row 18) — distinct from the self path above.
      return Promise.resolve(noContent());
    }
    if (u.includes('/api/admin/operators/') && u.includes('/roles')) {
      return Promise.resolve(
        jsonResponse({ operatorId: 'op-1', roles: ['SUPPORT_LOCK'], auditId: 'a' }),
      );
    }
    if (u.includes('/api/admin/operators/') && u.includes('/status')) {
      return Promise.resolve(
        jsonResponse({
          operatorId: 'op-1',
          previousStatus: 'ACTIVE',
          currentStatus: 'SUSPENDED',
          auditId: 'a',
        }),
      );
    }
    if (u.includes('/api/admin/operators')) {
      return Promise.resolve(
        u.includes('?')
          ? jsonResponse(OPERATORS_PAGE)
          : jsonResponse({
              operatorId: 'op-9',
              email: 'new@x.io',
              displayName: 'New',
              status: 'ACTIVE',
              roles: ['SUPPORT_LOCK'],
              createdAt: '2026-04-24T10:00:00Z',
              auditId: 'a',
              tenantId: 'wms',
            }, 201),
      );
    }
    if (u.includes('/api/admin/accounts/') && u.includes('/lock')) {
      return Promise.resolve(
        jsonResponse({
          accountId: 'acc-1',
          previousStatus: 'ACTIVE',
          currentStatus: 'LOCKED',
          operatorId: 'op',
          lockedAt: 'x',
          auditId: 'a',
        }),
      );
    }
    if (u.includes('/api/admin/accounts/') && u.includes('/unlock')) {
      return Promise.resolve(
        jsonResponse({
          accountId: 'acc-1',
          previousStatus: 'LOCKED',
          currentStatus: 'ACTIVE',
          operatorId: 'op',
          unlockedAt: 'x',
          auditId: 'a',
        }),
      );
    }
    if (u.includes('/api/admin/accounts/bulk-lock')) {
      return Promise.resolve(
        jsonResponse({ results: [{ accountId: 'acc-1', outcome: 'LOCKED' }] }),
      );
    }
    if (u.includes('/api/admin/accounts/') && u.includes('/gdpr-delete')) {
      return Promise.resolve(
        jsonResponse({
          accountId: 'acc-1',
          status: 'DELETED',
          maskedAt: 'x',
          auditId: 'a',
        }),
      );
    }
    if (u.includes('/api/admin/accounts/') && u.includes('/export')) {
      return Promise.resolve(
        jsonResponse({
          accountId: 'acc-1',
          email: 'a@x.io',
          status: 'ACTIVE',
          createdAt: '2026-01-01T00:00:00Z',
          exportedAt: '2026-04-18T10:00:00Z',
        }),
      );
    }
    if (u.includes('/api/admin/sessions/')) {
      return Promise.resolve(
        jsonResponse({
          accountId: 'acc-1',
          revokedSessionCount: 1,
          operatorId: 'op',
          revokedAt: 'x',
          auditId: 'a',
        }),
      );
    }
    if (u.includes('/api/admin/accounts')) {
      return Promise.resolve(jsonResponse(ACCOUNTS_PAGE));
    }
    if (u.includes('/api/admin/audit')) {
      return Promise.resolve(jsonResponse(AUDIT_PAGE));
    }
    return Promise.resolve(jsonResponse({}));
  });
}

/**
 * Invokes a row's capability through the EXISTING feature client with
 * arguments valid for that capability — so the assertion is on the real
 * FE-002..005 surface (not a re-test of internals; one representative
 * call to attest the parity invariants).
 */
async function invokeCapability(row: ParityRow): Promise<void> {
  switch (row.clientExport) {
    case 'searchAccounts':
      await accountsApi.searchAccounts({ page: 0, size: 20 });
      return;
    case 'getAccountByEmail':
      await accountsApi.getAccountByEmail('a@x.io');
      return;
    case 'lockAccount':
      await accountsApi.lockAccount('acc-1', { reason: 'r' }, 'k1');
      return;
    case 'unlockAccount':
      await accountsApi.unlockAccount('acc-1', { reason: 'r' }, 'k1');
      return;
    case 'bulkLockAccounts':
      await accountsApi.bulkLockAccounts(['acc-1'], { reason: 'r' }, 'k1');
      return;
    case 'revokeSessions':
      await accountsApi.revokeSessions('acc-1', { reason: 'r' }, 'k1');
      return;
    case 'gdprDeleteAccount':
      await accountsApi.gdprDeleteAccount('acc-1', { reason: 'r' }, 'k1');
      return;
    case 'exportAccount':
      await accountsApi.exportAccount('acc-1', 'gdpr portability');
      return;
    case 'queryAudit': {
      const source =
        row.id === 10
          ? ('login_history' as const)
          : row.id === 11
            ? ('suspicious' as const)
            : undefined;
      await auditApi.queryAudit(source ? { source } : {});
      return;
    }
    case 'createOperator':
      await operatorsApi.createOperator(
        {
          email: 'new@x.io',
          displayName: 'New',
          password: 'Sup3rSecret!pw',
          roles: ['SUPPORT_LOCK'],
          tenantId: 'wms',
        },
        'onboarding',
        'idem-create-1',
      );
      return;
    case 'editOperatorRoles':
      await operatorsApi.editOperatorRoles('op-1', ['SUPPORT_LOCK'], 'r');
      return;
    case 'changeOperatorStatus':
      await operatorsApi.changeOperatorStatus('op-1', 'SUSPENDED', 'r');
      return;
    case 'changeOwnPassword':
      await operatorsApi.changeOwnPassword({
        currentPassword: 'Old1!pass',
        newPassword: 'Sup3rSecret!pw',
      });
      return;
    case 'updateOwnProfile':
      await operatorsApi.updateOwnProfile({
        defaultAccountId: '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
      });
      return;
    case 'setOperatorProfile':
      await operatorsApi.setOperatorProfile(
        'op-1',
        '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
        'admin-set-profile reason',
      );
      return;
    case 'getOperatorOverview':
      await dashboardsApi.getOperatorOverview();
      return;
    default:
      throw new Error(`unmapped clientExport: ${row.clientExport}`);
  }
}

/** The public-API module each capability's export must be reachable
 *  through (Layered-by-Feature: app/ imports the feature, not internals).
 *  The api module is the call surface FE-006 attests. */
const FEATURE_API_MODULE: Record<ParityRow['featureModule'], () => unknown> = {
  'features/accounts': () => accountsApi,
  'features/audit': () => auditApi,
  'features/operators': () => operatorsApi,
  'features/dashboards': () => dashboardsApi,
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

// ===========================================================================
// 0. Matrix ↔ spec § 3.1 no-drift guard (one fixture, mirrored by the spec)
// ===========================================================================

describe('parity matrix — single source, no matrix↔spec drift', () => {
  it('the fixture has exactly the 18 § 3.1 rows with stable ids 1..18', () => {
    expect(PARITY_MATRIX).toHaveLength(18);
    expect(PARITY_MATRIX.map((r) => r.id)).toEqual([
      1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
    ]);
  });

  it('every fixture row is mirrored verbatim in console-integration-contract.md § 3.1 (no drift)', () => {
    const spec = readFileSync(
      path.resolve(
        APP_ROOT,
        '../../specs/contracts/console-integration-contract.md',
      ),
      'utf8',
    );
    // The spec must carry the finalized verified-matrix marker + the
    // ADR-MONO-015 not-Grafana note + the Phase-3-gate-satisfied closing
    // statement (task AC). Then every row's capability + producer section
    // string must literally appear in the spec table — the fixture cannot
    // diverge from the documented matrix without failing here.
    expect(spec).toContain('VERIFIED parity matrix');
    expect(spec).toContain('NOT Grafana');
    expect(spec).toContain('Phase 3');
    expect(spec).toContain('retirement gate');
    // Each row carries a "verified by TASK-PC-FE-<NNN>" marker (task AC) —
    // exactly one per matrix row (the §3.1 table's Verified column). Rows
    // 1..16 are PC-FE-006 (the original capstone); row 17 carries PC-FE-016
    // (change-profile); row 18 carries PC-FE-017 (admin-set-profile) — the
    // count must match.
    const verifiedMarks = (spec.match(/verified by TASK-PC-FE-\d+/g) ?? [])
      .length;
    expect(verifiedMarks).toBe(PARITY_MATRIX.length);
    // The real no-drift binding: every fixture row's capability AND its
    // producer path (the consumer-facing endpoint) appear in the spec §3
    // table. (Composed rows — detail/dashboards — have no dedicated
    // producer path; their composition basis is asserted via the
    // capability + the explicit composed-over reads instead.)
    for (const row of PARITY_MATRIX) {
      // The stable parity-line identity is the capability's leading
      // segment (before any annotation parenthesis); the spec cell may add
      // **markdown** emphasis inside the annotation, so match the stable
      // token, not the decorated label.
      const capKey = row.capability.split(' (')[0];
      expect(spec).toContain(capKey);
      if (row.producerPath !== '') {
        expect(spec).toContain(row.producerPath);
      }
    }
    // The composed rows must be documented as composed (no fabricated
    // endpoint) — the spec records detail's composition + dashboards'
    // fan-out over the existing reads.
    expect(spec).toMatch(/detail[\s\S]*?composed/i);
    expect(spec).toMatch(/no new producer[\s\S]*?fan-out|fan-out[\s\S]*?no new producer/i);
  });

  it('honesty AC — every row is verified:true (no green-wash; the matrix reflects reality)', () => {
    const gaps = PARITY_MATRIX.filter((r) => !r.verified);
    // A non-empty list here means a real parity gap was recorded — which
    // (per the project Review Rules) must be a NEW fix task, never a
    // silent in-place patch. FE-006 found none.
    expect(gaps).toEqual([]);
  });
});

// ===========================================================================
// 1. Capability presence — route module exists + feature exports the export
// ===========================================================================

describe('parity matrix — every capability is present (route + feature export)', () => {
  for (const row of PARITY_MATRIX) {
    it(`#${row.id} ${row.capability} — route module exists & ${row.featureModule} exports ${row.clientExport}`, () => {
      const routeAbs = path.resolve(APP_ROOT, row.routeFile);
      expect(existsSync(routeAbs)).toBe(true);

      const mod = FEATURE_API_MODULE[row.featureModule]() as Record<
        string,
        unknown
      >;
      expect(typeof mod[row.clientExport]).toBe('function');
    });
  }

  it('the four Phase-2 routes resolve to their in-console destinations', () => {
    const gap: RegistryProduct = {
      productKey: 'gap',
      displayName: 'Global Account Platform',
      available: true,
      tenants: ['wms'],
      baseRoute: '/gap',
    };
    // FE-002 contract unchanged: gap catalog tile → /accounts.
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
    // The composed overview quick-links target the EXISTING routes.
    expect(OVERVIEW_QUICK_LINKS.accounts).toBe('/accounts');
    expect(OVERVIEW_QUICK_LINKS.audit).toBe('/audit');
    expect(OVERVIEW_QUICK_LINKS.operators).toBe('/operators');
  });
});

// ===========================================================================
// 2. Per-row: correct producer path + operator-token (NOT GAP) + X-Tenant-Id
// ===========================================================================

describe('parity matrix — operator-token boundary + producer path + tenant scope (per row)', () => {
  for (const row of PARITY_MATRIX) {
    it(`#${row.id} ${row.capability} — targets ${row.producerPath || 'the composed reads'} with the OPERATOR token (not GAP) + X-Tenant-Id`, async () => {
      cookieJar.set(ACCESS_COOKIE, GAP_TOKEN_SENTINEL);
      cookieJar.set(OPERATOR_COOKIE, OPERATOR_TOKEN);
      cookieJar.set(TENANT_COOKIE, 'wms');
      const fetchMock = routedFetch();
      vi.stubGlobal('fetch', fetchMock);

      await invokeCapability(row);

      const calls = fetchMock.mock.calls;
      expect(calls.length).toBeGreaterThan(0);

      if (row.id === 16) {
        // dashboards: bounded fan-out over EXACTLY the 3 existing reads
        // (no new producer — ADR-MONO-015 D1). One bounded set per load.
        expect(calls).toHaveLength(3);
        const urls = calls.map((c) => String(c[0]));
        for (const leg of OVERVIEW_FANOUT_LEGS) {
          expect(urls.some((u) => u.includes(leg))).toBe(true);
        }
      } else {
        const urls = calls.map((c) => String(c[0]));
        expect(
          urls.some((u) => u.includes(row.producerPath)),
        ).toBe(true);
      }

      // EVERY leg/call: operator-token bearer (NEVER the GAP OIDC token —
      // the #569 trust-boundary invariant) + non-empty active tenant.
      for (const [, init] of calls) {
        const h = (init as RequestInit).headers as Record<string, string>;
        expect(h.Authorization).toBe(`Bearer ${OPERATOR_TOKEN}`);
        expect(h.Authorization).not.toContain(GAP_TOKEN_SENTINEL);
        expect(h['X-Tenant-Id']).toBe('wms');
      }
    });

    it(`#${row.id} ${row.capability} — absent operator token ⇒ 401 with NO fetch (no GAP-token fallback)`, async () => {
      // The #569 boundary: a GAP-token-only state must NEVER be used as an
      // /api/admin/** credential. Each reused client throws 401 (no fetch);
      // the composed overview re-throws a 401 as a whole-overview failure.
      cookieJar.set(ACCESS_COOKIE, GAP_TOKEN_SENTINEL); // GAP token present…
      cookieJar.set(TENANT_COOKIE, 'wms');
      // …but NO operator cookie.
      const fetchMock = routedFetch();
      vi.stubGlobal('fetch', fetchMock);

      const err = await invokeCapability(row).catch((e) => e);
      expect(err).toBeInstanceOf(ApiError);
      expect((err as ApiError).status).toBe(401);
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it(`#${row.id} ${row.capability} — no active tenant ⇒ blocked, NO fetch (never an empty X-Tenant-Id)`, async () => {
      cookieJar.set(OPERATOR_COOKIE, OPERATOR_TOKEN);
      // NO tenant cookie.
      const fetchMock = routedFetch();
      vi.stubGlobal('fetch', fetchMock);

      if (row.id === 16) {
        // The overview isolates each leg's NO_ACTIVE_TENANT as a non-ok
        // per-card status — no fetch, no crash (per-source isolation).
        const overview = await dashboardsApi.getOperatorOverview();
        expect(fetchMock).not.toHaveBeenCalled();
        expect(overview.accounts.status).not.toBe('ok');
        expect(overview.audit.status).not.toBe('ok');
        expect(overview.operators.status).not.toBe('ok');
      } else {
        const err = await invokeCapability(row).catch((e) => e);
        expect(err).toBeInstanceOf(ApiError);
        expect((err as ApiError).code).toBe('NO_ACTIVE_TENANT');
        expect(fetchMock).not.toHaveBeenCalled();
      }
    });
  }
});

// ===========================================================================
// 3. Mutation rows: contract-correct headers (incl. FE-004 non-uniformity)
//    Read rows: NO mutation artifacts.
// ===========================================================================

describe('parity matrix — header obligation per § 2.4.1/§ 2.4.3 (mutation vs read)', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, OPERATOR_TOKEN);
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  for (const row of PARITY_MATRIX) {
    it(`#${row.id} ${row.capability} — header obligation '${row.header}' is honored`, async () => {
      const fetchMock = routedFetch();
      vi.stubGlobal('fetch', fetchMock);
      await invokeCapability(row);

      // The capability's OWN call (skip the overview's reused-leg
      // composition — row 16 is asserted in § 4). Use the call whose URL
      // matches the row's producer path; fall back to the first call.
      const ownCall =
        row.id === 16
          ? null
          : (fetchMock.mock.calls.find((c) =>
              String(c[0]).includes(row.producerPath),
            ) ?? fetchMock.mock.calls[0]);

      if (row.id === 16) {
        // Composed overview: NO mutation artifacts on ANY leg, all GET.
        for (const [, init] of fetchMock.mock.calls) {
          expect((init as RequestInit).method).toBe('GET');
          expect((init as RequestInit).body).toBeUndefined();
          const h = (init as RequestInit).headers as Record<string, string>;
          expect(h['X-Operator-Reason']).toBeUndefined();
          expect(h['Idempotency-Key']).toBeUndefined();
        }
        return;
      }

      const init = ownCall![1] as RequestInit;
      const h = init.headers as Record<string, string>;

      switch (row.header) {
        case 'reason+idem':
          // create / accounts mutations: BOTH headers present.
          expect(h['X-Operator-Reason']).toBeTruthy();
          expect(h['Idempotency-Key']).toBeTruthy();
          break;
        case 'reason-only':
          // FE-004 NON-uniformity (the key correctness risk): roles /
          // status carry the reason but the producer does NOT list
          // Idempotency-Key — sending it would be a header-matrix-drift
          // defect. Absence is asserted.
          expect(h['X-Operator-Reason']).toBeTruthy();
          expect(h['Idempotency-Key']).toBeUndefined();
          expect('Idempotency-Key' in h).toBe(false);
          break;
        case 'reason-no-idem':
          // export: GET with producer-mandated audit reason, NO idem.
          expect((init.method ?? 'GET')).toBe('GET');
          expect(h['X-Operator-Reason']).toBeTruthy();
          expect(h['Idempotency-Key']).toBeUndefined();
          break;
        case 'none':
          // read rows + self change-password: NO mutation artifacts.
          expect(h['X-Operator-Reason']).toBeUndefined();
          expect(h['Idempotency-Key']).toBeUndefined();
          if (row.kind === 'read') {
            expect(init.method ?? 'GET').toBe('GET');
            expect(init.body).toBeUndefined();
          }
          break;
      }
    });
  }

  it('FE-004 per-endpoint NON-uniformity is real: create has Idempotency-Key, roles/status do NOT', async () => {
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);

    await operatorsApi.createOperator(
      {
        email: 'new@x.io',
        displayName: 'New',
        password: 'Sup3rSecret!pw',
        roles: ['SUPPORT_LOCK'],
        tenantId: 'wms',
      },
      'onboarding',
      'idem-create-1',
    );
    await operatorsApi.editOperatorRoles('op-1', ['SUPPORT_LOCK'], 'realign');
    await operatorsApi.changeOperatorStatus('op-1', 'SUSPENDED', 'policy');

    const [createInit, rolesInit, statusInit] = fetchMock.mock.calls.map(
      (c) => c[1] as RequestInit,
    );
    const ch = createInit.headers as Record<string, string>;
    const rh = rolesInit.headers as Record<string, string>;
    const sh = statusInit.headers as Record<string, string>;

    expect(ch['Idempotency-Key']).toBe('idem-create-1');
    expect(rh['Idempotency-Key']).toBeUndefined();
    expect(sh['Idempotency-Key']).toBeUndefined();
    // All three still carry the audit reason.
    expect(ch['X-Operator-Reason']).toBe('onboarding');
    expect(rh['X-Operator-Reason']).toBe('realign');
    expect(sh['X-Operator-Reason']).toBe('policy');
  });

  it('mutation rows are reason-gated: an empty reason ⇒ NO fetch (reason+confirm gate fail-safe)', async () => {
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);

    // A representative reason-bearing mutation per slice.
    const empty1 = await accountsApi
      .lockAccount('acc-1', { reason: '   ' }, 'k')
      .catch((e) => e);
    const empty2 = await operatorsApi
      .editOperatorRoles('op-1', ['SUPPORT_LOCK'], '   ')
      .catch((e) => e);
    expect(empty1).toBeInstanceOf(ApiError);
    expect((empty1 as ApiError).code).toBe('REASON_REQUIRED');
    expect(empty2).toBeInstanceOf(ApiError);
    expect((empty2 as ApiError).code).toBe('REASON_REQUIRED');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

// ===========================================================================
// 4. The `dashboards` row — composed overview, per-source isolated, NOT
//    Grafana (ADR-MONO-015 D2). Read-only — no mutation artifacts.
// ===========================================================================

describe('parity matrix — dashboards (#16) = ADR-MONO-015 composed overview, NOT Grafana', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, OPERATOR_TOKEN);
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  const row = PARITY_MATRIX.find((r) => r.id === 16)!;

  it('maps to features/dashboards § 2.4.4, composed (no new producer), not Grafana', () => {
    expect(row.featureModule).toBe('features/dashboards');
    expect(row.contractSection).toBe('2.4.4');
    expect(row.producerPath).toBe(''); // no dedicated producer endpoint
    expect(row.notes).toContain('NOT');
    expect(row.notes).toContain('Grafana');
    expect(row.kind).toBe('read');
  });

  it('is a bounded fan-out over EXACTLY the 3 existing reads — one set per load (no Grafana/observability call)', async () => {
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);

    await dashboardsApi.getOperatorOverview();

    expect(fetchMock).toHaveBeenCalledTimes(3);
    const urls = fetchMock.mock.calls.map((c) => String(c[0]));
    for (const leg of OVERVIEW_FANOUT_LEGS) {
      expect(urls.some((u) => u.includes(leg))).toBe(true);
    }
    // No grafana / metrics / observability endpoint is ever called.
    for (const u of urls) {
      expect(u).not.toMatch(/grafana|\/metrics|observability|\/dashboard/i);
    }
  });

  it('per-source isolation: one leg 503 degrades THAT card only; a 401 on any leg = whole-overview re-login', async () => {
    // accounts 503 → accounts card degraded; audit + operators ok.
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        const u = String(url);
        if (u.includes('/api/admin/accounts'))
          return Promise.resolve(jsonResponse({ code: 'CIRCUIT_OPEN' }, 503));
        if (u.includes('/api/admin/audit'))
          return Promise.resolve(jsonResponse(AUDIT_PAGE));
        if (u.includes('/api/admin/operators'))
          return Promise.resolve(jsonResponse(OPERATORS_PAGE));
        return Promise.resolve(jsonResponse({}));
      }),
    );
    const overview = await dashboardsApi.getOperatorOverview();
    expect(overview.accounts.status).toBe('degraded');
    expect(overview.audit.status).toBe('ok');
    expect(overview.operators.status).toBe('ok');

    // 401 on ANY leg → the WHOLE fan-out rejects (no per-card degrade —
    // no partial authed state; the operator token is shared).
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        const u = String(url);
        if (u.includes('/api/admin/operators') && !u.includes('?'))
          return Promise.resolve(jsonResponse(OPERATORS_PAGE));
        if (u.includes('/api/admin/operators'))
          return Promise.resolve(jsonResponse(OPERATORS_PAGE));
        if (u.includes('/api/admin/audit'))
          return Promise.resolve(jsonResponse({ code: 'TOKEN_INVALID' }, 401));
        return Promise.resolve(jsonResponse(ACCOUNTS_PAGE));
      }),
    );
    const err = await dashboardsApi.getOperatorOverview().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(401);
  });
});
