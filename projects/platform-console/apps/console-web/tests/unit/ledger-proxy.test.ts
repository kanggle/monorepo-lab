import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin finance ledger-ops proxy route handlers (TASK-PC-FE-072 —
 * § 2.4.7.1):
 *   - read GET (trial-balance, periods list/detail, entry, discrepancy
 *     queue/detail): domain-facing IAM OIDC access token attached
 *     server-side (NOT the operator token); no mutation artifacts;
 *     STRICTLY READ-ONLY (GET-only routes).
 *   - 401 → 401; 403 → 403; 404 passthrough; 400/422 passthrough;
 *     503/timeout → 503.
 *   - **no 429 / Retry-After branch** (a stray 429 lands as a passthrough,
 *     NO retry storm, NO Retry-After branch).
 *   - There is NO mutation proxy route at all.
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
    OIDC_ISSUER_URL: 'http://iam.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    IAM_ADMIN_API_BASE: 'http://iam.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
    LEDGER_BASE_URL: 'http://finance.local',
    LEDGER_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { GET as trialBalanceGET } from '@/app/api/ledger/trial-balance/route';
import { GET as periodsGET } from '@/app/api/ledger/periods/route';
import { GET as periodGET } from '@/app/api/ledger/periods/[periodId]/route';
import { GET as entryGET } from '@/app/api/ledger/entries/[entryId]/route';
import { GET as discrepanciesGET } from '@/app/api/ledger/reconciliation/discrepancies/route';
import { GET as discrepancyGET } from '@/app/api/ledger/reconciliation/discrepancies/[id]/route';
import { POST as resolvePOST } from '@/app/api/ledger/reconciliation/discrepancies/[id]/resolve/route';
import * as resolveRoute from '@/app/api/ledger/reconciliation/discrepancies/[id]/resolve/route';
import * as discrepancyRoute from '@/app/api/ledger/reconciliation/discrepancies/[id]/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}
function ledgerError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const M = (amount: string, currency = 'KRW') => ({ amount, currency });
const TB_ENV = {
  data: {
    accounts: [],
    grandDebitTotal: M('0'),
    grandCreditTotal: M('0'),
    grandBaseDebitTotal: M('0'),
    grandBaseCreditTotal: M('0'),
    inBalance: true,
  },
  meta: { timestamp: 'x' },
};
const PERIODS_ENV = {
  data: [],
  meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
};
const DISC_ENV = {
  data: [
    {
      discrepancyId: 'd-1',
      type: 'AMOUNT_MISMATCH',
      externalRef: 'bank-1',
      journalEntryId: 'je-9',
      expectedMinor: '1234567890123',
      actualMinor: '1234567890124',
      currency: 'KRW',
      status: 'OPEN',
    },
  ],
  meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ledger/trial-balance proxy (read-only)', () => {
  it('attaches the IAM OIDC access token (NOT the operator token), GET, no mutation artifacts', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(TB_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await trialBalanceGET();
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    expect(String(url)).toContain(
      'http://finance.local/api/finance/ledger/trial-balance',
    );
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await trialBalanceGET();
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('403 TENANT_FORBIDDEN → 403', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('TENANT_FORBIDDEN', 403)),
    );
    const res = await trialBalanceGET();
    expect(res.status).toBe(403);
  });

  it('503 from the ledger → 503 (ledger section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await trialBalanceGET();
    expect(res.status).toBe(503);
  });

  it('a stray 429 falls through as a passthrough — NO Retry-After branch, NO retry storm', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ code: 'RATE_LIMIT_EXCEEDED', message: 'x' }),
        {
          status: 429,
          headers: { 'Content-Type': 'application/json', 'Retry-After': '1' },
        },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await trialBalanceGET();
    expect(res.status).toBe(429);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(res.headers.get('Retry-After')).toBeNull();
  });
});

describe('GET /api/ledger/periods proxy (read-only)', () => {
  it('forwards page + size', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PERIODS_ENV));
    vi.stubGlobal('fetch', fetchMock);
    const res = await periodsGET(
      new Request('http://console.local/api/ledger/periods?page=1&size=50'),
    );
    expect(res.status).toBe(200);
    const upstream = new URL(String(fetchMock.mock.calls[0][0]));
    expect(upstream.searchParams.get('page')).toBe('1');
    expect(upstream.searchParams.get('size')).toBe('50');
  });
});

describe('GET /api/ledger/periods/{periodId} proxy (read-only)', () => {
  it('404 ACCOUNTING_PERIOD_NOT_FOUND → 404 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('ACCOUNTING_PERIOD_NOT_FOUND', 404)),
    );
    const res = await periodGET(
      new Request('http://console.local/api/ledger/periods/nope'),
      { params: Promise.resolve({ periodId: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const b = await res.json();
    expect(b.code).toBe('ACCOUNTING_PERIOD_NOT_FOUND');
  });
});

describe('GET /api/ledger/entries/{entryId} proxy (read-only)', () => {
  it('404 JOURNAL_ENTRY_NOT_FOUND → 404 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('JOURNAL_ENTRY_NOT_FOUND', 404)),
    );
    const res = await entryGET(
      new Request('http://console.local/api/ledger/entries/nope'),
      { params: Promise.resolve({ entryId: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const b = await res.json();
    expect(b.code).toBe('JOURNAL_ENTRY_NOT_FOUND');
  });
});

describe('GET /api/ledger/reconciliation/discrepancies proxy (read-only)', () => {
  it('forwards the status filter + paginates; F5 minor-units strings preserved', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DISC_ENV));
    vi.stubGlobal('fetch', fetchMock);
    const res = await discrepanciesGET(
      new Request(
        'http://console.local/api/ledger/reconciliation/discrepancies?status=OPEN&page=0&size=20',
      ),
    );
    expect(res.status).toBe(200);
    const upstream = new URL(String(fetchMock.mock.calls[0][0]));
    expect(upstream.pathname).toBe(
      '/api/finance/ledger/reconciliation/discrepancies',
    );
    expect(upstream.searchParams.get('status')).toBe('OPEN');
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('GET');
    const body = await res.json();
    // The minor-units strings travel through untouched.
    expect(body.data[0].expectedMinor).toBe('1234567890123');
    expect(typeof body.data[0].expectedMinor).toBe('string');
  });
});

describe('GET /api/ledger/reconciliation/discrepancies/{id} proxy (read-only)', () => {
  it('404 RECONCILIATION_DISCREPANCY_NOT_FOUND → 404 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(ledgerError('RECONCILIATION_DISCREPANCY_NOT_FOUND', 404)),
    );
    const res = await discrepancyGET(
      new Request(
        'http://console.local/api/ledger/reconciliation/discrepancies/nope',
      ),
      { params: Promise.resolve({ id: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const b = await res.json();
    expect(b.code).toBe('RECONCILIATION_DISCREPANCY_NOT_FOUND');
  });
});

// ===========================================================================
// POST .../discrepancies/{id}/resolve — the ONLY mutation proxy (PC-FE-073).
// ===========================================================================

const RESOLVED = {
  discrepancyId: 'd-1',
  type: 'AMOUNT_MISMATCH',
  externalRef: 'bank-1',
  journalEntryId: 'je-9',
  expectedMinor: '100',
  actualMinor: '105',
  currency: 'KRW',
  status: 'RESOLVED',
  resolution: {
    resolutionType: 'WRITTEN_OFF',
    note: 'fx gap',
    resolvedBy: 'op-1',
    resolvedAt: '2026-05-20T00:00:00Z',
  },
};

describe('discrepancy resolve proxy — method exposure', () => {
  it('the discrepancy-by-id route stays GET-only (the resolve is a SEPARATE sub-route)', () => {
    expect(typeof discrepancyRoute.GET).toBe('function');
    expect((discrepancyRoute as Record<string, unknown>).POST).toBeUndefined();
  });

  it('the resolve route: POST only (no GET/PUT/PATCH/DELETE)', () => {
    expect(typeof resolveRoute.POST).toBe('function');
    expect((resolveRoute as Record<string, unknown>).GET).toBeUndefined();
    expect((resolveRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((resolveRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((resolveRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });
});

describe('POST /api/ledger/reconciliation/discrepancies/{id}/resolve', () => {
  function resolveReq(id: string, body: unknown) {
    return resolvePOST(
      new Request(
        `http://console.local/api/ledger/reconciliation/discrepancies/${id}/resolve`,
        { method: 'POST', body: JSON.stringify(body) },
      ),
      { params: Promise.resolve({ id }) },
    );
  }

  it('forwards body { resolutionType, note } with the domain-facing token; NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ data: RESOLVED, meta: { timestamp: 'x' } }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await resolveReq('d-1', {
      resolutionType: 'WRITTEN_OFF',
      note: 'fx gap',
    });
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    expect(String(url)).toBe(
      'http://finance.local/api/finance/ledger/reconciliation/discrepancies/d-1/resolve',
    );
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    // THE KEY DEVIATION: NO Idempotency-Key (the producer defines none).
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['X-Tenant-Id']).toBeUndefined();
    const body = JSON.parse(String((init as RequestInit).body));
    expect(body).toEqual({ resolutionType: 'WRITTEN_OFF', note: 'fx gap' });
    expect(body.idempotencyKey).toBeUndefined();
    // The resolved discrepancy is forwarded (status RESOLVED + resolution).
    const out = await res.json();
    expect(out.status).toBe('RESOLVED');
  });

  it('empty note → 400 VALIDATION_ERROR with NO upstream call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await resolveReq('d-1', {
      resolutionType: 'WRITTEN_OFF',
      note: '',
    });
    expect(res.status).toBe(400);
    const b = await res.json();
    expect(b.code).toBe('VALIDATION_ERROR');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('whitespace-only note → 400 VALIDATION_ERROR with NO upstream call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await resolveReq('d-1', {
      resolutionType: 'WRITTEN_OFF',
      note: '   ',
    });
    expect(res.status).toBe(400);
    const b = await res.json();
    expect(b.code).toBe('VALIDATION_ERROR');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('out-of-vocabulary resolutionType → 400 VALIDATION_ERROR with NO upstream call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await resolveReq('d-1', {
      resolutionType: 'NOT_A_REAL_TYPE',
      note: 'x',
    });
    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('409 RECONCILIATION_ALREADY_RESOLVED passes through inline-actionably', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('RECONCILIATION_ALREADY_RESOLVED', 409)),
    );
    const res = await resolveReq('d-1', {
      resolutionType: 'ACCEPTED',
      note: 'x',
    });
    expect(res.status).toBe(409);
    const b = await res.json();
    expect(b.code).toBe('RECONCILIATION_ALREADY_RESOLVED');
  });

  it('422 RECONCILIATION_PERIOD_LOCKED passes through inline-actionably', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('RECONCILIATION_PERIOD_LOCKED', 422)),
    );
    const res = await resolveReq('d-1', {
      resolutionType: 'ACCEPTED',
      note: 'x',
    });
    expect(res.status).toBe(422);
    const b = await res.json();
    expect(b.code).toBe('RECONCILIATION_PERIOD_LOCKED');
  });

  it('404 RECONCILIATION_DISCREPANCY_NOT_FOUND passes through', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(ledgerError('RECONCILIATION_DISCREPANCY_NOT_FOUND', 404)),
    );
    const res = await resolveReq('nope', {
      resolutionType: 'ACCEPTED',
      note: 'x',
    });
    expect(res.status).toBe(404);
  });

  it('no IAM session → 401, no upstream call', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await resolveReq('d-1', {
      resolutionType: 'ACCEPTED',
      note: 'x',
    });
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 from the ledger → 503 (ledger section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await resolveReq('d-1', {
      resolutionType: 'ACCEPTED',
      note: 'x',
    });
    expect(res.status).toBe(503);
  });
});
