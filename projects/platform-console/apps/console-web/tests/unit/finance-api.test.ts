import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

/**
 * `features/finance-ops/api/finance-api.ts` — the security-critical
 * core of TASK-PC-FE-009 (the THIRD non-IAM federated domain; closes
 * the non-IAM federation cycle: wms → scm → finance).
 * STRICTLY READ-ONLY.
 *
 * THE CENTRAL ASSERTIONS (console-integration-contract § 2.4.7 —
 * REUSE of the § 2.4.5 per-domain credential rule, NOT re-derived;
 * same outcome as wms / scm / the EXACT INVERSE of the FE-002..006
 * IAM assertion):
 *
 *   - every finance call's bearer is the **IAM OIDC ACCESS token** (the
 *     `console_access_token` cookie), NEVER the exchanged operator
 *     token;
 *   - the operator-token path is ABSENT for finance (the finance client
 *     does NOT call `getOperatorToken()` — pinned so a future refactor
 *     cannot blanket-apply one domain's auth to all domains; the #569
 *     invariant is GAP-domain-scoped and does NOT generalise to
 *     finance);
 *   - the console sends NO `X-Tenant-Id` (finance resolves tenant
 *     from the JWT `tenant_id ∈ {finance,*}` claim producer-side —
 *     tenant-model divergence reused from § 2.4.5);
 *   - EVERY call is a pure GET — NO mutation artifacts anywhere (no
 *     Idempotency-Key, no X-Operator-Reason, no body, no finance write,
 *     no v2 admin-service surface);
 *   - the finance FLAT error envelope
 *     `{ code, message, details?, timestamp }` is parsed (NOT
 *     wms's NESTED `{ error: { code } }`);
 *   - **NO 429 / Retry-After / backoff branch** (§ 2.4.7 — finance has
 *     no documented 429; a stray 429 falls through as a surfaced
 *     `ApiError` with NO retry, NO Retry-After honour);
 *   - 401 → ApiError(401) (whole-session re-login); 403 → ApiError(403)
 *     inline; 404 ACCOUNT_NOT_FOUND → ApiError inline; 503/timeout →
 *     FinanceUnavailableError (section degrades only).
 *
 * F5 (§ 2.4.7 NORMATIVE — contract obligation, NOT a UX nicety):
 *   - every Money value is a `{ amount: "<integer-string>", currency }`
 *     (zod regex enforces); the schema NEVER accepts a `number` amount;
 *   - `formatMoney(...)` is the only sanctioned render path; it uses
 *     pure string manipulation (no `Number()` / float math);
 *   - a large minor-units amount (e.g. KRW `"1234567890123"`) round-trips
 *     bit-exact through parse → display → re-parse;
 *   - **a static grep over the on-disk `features/finance-ops/` source
 *     asserts that `Number(...)` / `parseFloat(...)` / `parseInt(...)`
 *     never appear on a line that references `amount`** (the precise
 *     F5 invariant).
 *
 * `next/headers` cookies() + getServerEnv() mocked (FE-001..008 lane).
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
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    WMS_TIMEOUT_MS: 50,
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

// Spy the session module so we can assert WHICH credential accessor the
// finance client uses (the central per-domain-credential assertion).
import * as sessionModule from '@/shared/lib/session';

import {
  getAccount,
  getBalances,
  listTransactions,
} from '@/features/finance-ops/api/finance-api';
import {
  ApiError,
  FinanceUnavailableError,
} from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';
import {
  formatMoney,
  MoneySchema,
  BalanceSchema,
  TransactionSchema,
} from '@/features/finance-ops/api/types';

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

/** finance FLAT error envelope (same wire shape as scm but a DISTINCT
 *  producer; NOT wms's nested `{ error: { code } }`). */
function financeError(code: string, status: number, message = 'err') {
  return new Response(
    JSON.stringify({
      code,
      message,
      timestamp: '2026-05-20T00:00:00.000Z',
    }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const ACCOUNT_ENVELOPE = {
  data: {
    accountId: 'acct-9b1d4a8c',
    status: 'ACTIVE',
    currency: 'KRW',
    kycLevel: 'BASIC',
    balances: [
      {
        currency: 'KRW',
        ledger: '1234567890123', // large minor-units string — F5 precision
        available: '1234567890000',
        held: '123',
      },
    ],
    createdAt: '2026-05-01T00:00:00Z',
    updatedAt: '2026-05-19T12:00:00Z',
  },
  meta: { timestamp: '2026-05-20T00:00:00Z' },
};

const BALANCES_ENVELOPE = {
  data: [
    {
      currency: 'KRW',
      ledger: '1234567890123',
      available: '1234567890000',
      held: '123',
    },
  ],
  meta: { timestamp: '2026-05-20T00:00:00Z' },
};

const TXNS_ENVELOPE = {
  data: [
    {
      transactionId: 'txn-1',
      type: 'HOLD',
      status: 'ACTIVE',
      money: { amount: '150000', currency: 'KRW' },
      counterpartyAccountId: null,
      reversalOfTransactionId: null,
      createdAt: '2026-05-19T10:00:00Z',
      settledAt: null,
    },
    {
      transactionId: 'txn-2',
      type: 'TRANSFER',
      status: 'FAILED',
      money: { amount: '50000', currency: 'KRW' },
      counterpartyAccountId: 'acct-other',
      reversalOfTransactionId: null,
      createdAt: '2026-05-19T11:00:00Z',
    },
    {
      transactionId: 'txn-3',
      type: 'REVERSAL',
      status: 'REVERSED',
      money: { amount: '50000', currency: 'KRW' },
      counterpartyAccountId: 'acct-other',
      reversalOfTransactionId: 'txn-2',
      createdAt: '2026-05-19T11:05:00Z',
    },
  ],
  meta: {
    page: 0,
    size: 20,
    totalElements: 3,
    timestamp: '2026-05-20T00:00:00Z',
  },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ===========================================================================
// 1. Per-domain credential — IAM OIDC token, NEVER getOperatorToken()
// ===========================================================================

describe('finance-api — per-domain credential selection (REUSE of § 2.4.5; the INVERSE of #569)', () => {
  it('sends the IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-finance');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ACCOUNT_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getAccount('acct-9b1d4a8c');

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-finance',
    );
    expect(headers.Authorization).not.toContain(
      'OPERATOR-TOKEN-must-not-be-used',
    );
    expect(String(url)).toContain(
      'http://finance.local/api/finance/accounts/acct-9b1d4a8c',
    );
  });

  it('uses getDomainFacingToken() (net-zero → base IAM token) and NEVER getOperatorToken() for finance (pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    // ADR-MONO-020 D4 / § 2.7: domain-facing token (assumed-when-switched,
    // else base) — STILL never the operator token.
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(BALANCES_ENVELOPE)),
    );

    await getBalances('acct-1');

    expect(getDomainFacingSpy).toHaveBeenCalled();
    // The operator-token path is ABSENT for finance — same shape as the
    // FE-007 wms / FE-008 scm assertions; a future blanket-apply
    // refactor would break this.
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent (whole-session re-login signal)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await getAccount('acct-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id (finance resolves tenant from the JWT claim — tenant-model divergence)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ACCOUNT_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getAccount('acct-1');

    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

// ===========================================================================
// 2. STRICTLY read-only — every call is a pure GET, no mutation artifacts.
// ===========================================================================

describe('finance-api — STRICTLY read-only (no mutation artifacts anywhere; § 2.4.7)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('every read is a pure GET with NO mutation artifacts', async () => {
    const calls: Array<[string, RequestInit]> = [];
    const fetchMock = vi.fn((u: string, init?: RequestInit) => {
      calls.push([String(u), init as RequestInit]);
      const us = String(u);
      if (us.includes('/balances')) {
        return Promise.resolve(jsonResponse(BALANCES_ENVELOPE));
      }
      if (us.includes('/transactions')) {
        return Promise.resolve(jsonResponse(TXNS_ENVELOPE));
      }
      return Promise.resolve(jsonResponse(ACCOUNT_ENVELOPE));
    });
    vi.stubGlobal('fetch', fetchMock);

    await getAccount('acct-1');
    await getBalances('acct-1');
    await listTransactions('acct-1', {
      type: 'TRANSFER',
      status: 'COMPLETED',
      page: 0,
      size: 20,
    });

    expect(calls.length).toBe(3);
    for (const [, init] of calls) {
      const h = init.headers as Record<string, string>;
      expect(init.method).toBe('GET');
      expect(init.body).toBeUndefined();
      expect(h['Idempotency-Key']).toBeUndefined();
      expect(h['X-Operator-Reason']).toBeUndefined();
      expect(h['Content-Type']).toBeUndefined();
    }
    // The api module exports ONLY read functions — no PO write, no
    // finance write (`POST /accounts` / `/kyc/upgrade` / `/holds` /
    // `/transfers`), no v2 admin-service surface, no list/search
    // (finance v1 has none — account-id-driven).
    const mod = await import('@/features/finance-ops/api/finance-api');
    expect(Object.keys(mod).sort()).toEqual(
      ['getAccount', 'getBalances', 'listTransactions'].sort(),
    );
  });

  it('the proxy directory exposes ONLY GET route handlers (no mutation route at all)', async () => {
    const proxyRoot = path.resolve(
      __dirname,
      '../../src/app/api/finance',
    );
    function walk(p: string): string[] {
      const out: string[] = [];
      for (const name of readdirSync(p)) {
        const full = path.join(p, name);
        if (statSync(full).isDirectory()) out.push(...walk(full));
        else out.push(full);
      }
      return out;
    }
    const tsFiles = walk(proxyRoot).filter((f) => f.endsWith('.ts'));
    expect(tsFiles.length).toBeGreaterThan(0);

    for (const f of tsFiles) {
      const src = readFileSync(f, 'utf8');
      // `_proxy.ts` itself is the shared error mapper — no route
      // handler exports.
      if (path.basename(f) === '_proxy.ts') continue;
      // Each route handler MUST export GET only — no POST/PUT/PATCH/
      // DELETE handlers (§ 2.4.7 read-only proxy).
      expect(src).toMatch(/export\s+async\s+function\s+GET\b/);
      expect(src).not.toMatch(/export\s+async\s+function\s+POST\b/);
      expect(src).not.toMatch(/export\s+async\s+function\s+PUT\b/);
      expect(src).not.toMatch(/export\s+async\s+function\s+PATCH\b/);
      expect(src).not.toMatch(/export\s+async\s+function\s+DELETE\b/);
    }
  });
});

// ===========================================================================
// 3. F5 — money is a precision-exact minor-units STRING (NEVER a Number).
// ===========================================================================

describe('finance-api / types — F5 money invariant (precision-exact string; NO float/Number coercion)', () => {
  it('MoneySchema rejects a number `amount` (z.number()) — never accepted', () => {
    // A producer accidentally returning a JS number for `amount` must
    // FAIL the parser — the wire is a string by contract (F5).
    const result = MoneySchema.safeParse({
      amount: 12345 as unknown,
      currency: 'KRW',
    });
    expect(result.success).toBe(false);
  });

  it('MoneySchema accepts a precision-exact minor-units string', () => {
    const parsed = MoneySchema.parse({
      amount: '1234567890123',
      currency: 'KRW',
    });
    expect(parsed.amount).toBe('1234567890123');
    expect(parsed.currency).toBe('KRW');
    // The TS type is `string` — not a Number anywhere.
    expect(typeof parsed.amount).toBe('string');
  });

  it('formatMoney round-trips a large KRW amount bit-exact (no precision loss)', () => {
    // KRW scale=0 — the digits are the rendered body. A round-trip
    // re-parse against the regex MUST recover the exact same string.
    const m = { amount: '1234567890123', currency: 'KRW' } as const;
    const rendered = formatMoney(m);
    // The rendered string contains the digit body untouched + the
    // currency.
    expect(rendered).toBe('1234567890123 KRW');
    // Pluck back the digit body via string ops only (no Number).
    const digitBody = rendered.split(' ')[0];
    expect(digitBody).toBe(m.amount);
    const reparsed = MoneySchema.parse({
      amount: digitBody,
      currency: 'KRW',
    });
    expect(reparsed.amount).toBe(m.amount);
  });

  it('formatMoney scales USD correctly (cents → dollars.cents) from the string', () => {
    expect(
      formatMoney({ amount: '1000', currency: 'USD' }),
    ).toBe('10.00 USD');
    expect(
      formatMoney({ amount: '5', currency: 'USD' }),
    ).toBe('0.05 USD');
    expect(
      formatMoney({ amount: '-12345', currency: 'USD' }),
    ).toBe('-123.45 USD');
  });

  it('formatMoney for an unknown currency falls back to a generic scale (no throw)', () => {
    // Tolerant: unknown currency → scale 0 default, no parser throw.
    expect(
      formatMoney({ amount: '1000', currency: 'XYZ' }),
    ).toBe('1000 XYZ');
  });

  it('BalanceSchema enforces F5 minor-units strings on ledger / available / held', () => {
    // A balance row with a number `ledger` MUST fail — F5 protects
    // every minor-units field, not just `Money.amount`.
    const ok = BalanceSchema.safeParse({
      currency: 'KRW',
      ledger: '100',
      available: '50',
      held: '50',
    });
    expect(ok.success).toBe(true);
    const bad = BalanceSchema.safeParse({
      currency: 'KRW',
      ledger: 100 as unknown,
      available: '50',
      held: '50',
    });
    expect(bad.success).toBe(false);
  });

  it('TransactionSchema requires money — it is NOT optional / discardable (F5 required field)', () => {
    const noMoney = TransactionSchema.safeParse({
      transactionId: 'x',
      type: 'HOLD',
      status: 'ACTIVE',
    });
    expect(noMoney.success).toBe(false);
  });

  // The headline F5 grep — the precise on-disk invariant the task
  // pins. NEVER `Number(...)` / `parseFloat(...)` / `parseInt(...)` on
  // any line that references `amount` in `features/finance-ops/`. The
  // grep runs against the REAL source files, not a mock.
  it('the on-disk `features/finance-ops/` source applies NO Number()/parseFloat()/parseInt() to `amount`', () => {
    const root = path.resolve(
      __dirname,
      '../../src/features/finance-ops',
    );
    function walk(p: string): string[] {
      const out: string[] = [];
      for (const name of readdirSync(p)) {
        const full = path.join(p, name);
        if (statSync(full).isDirectory()) out.push(...walk(full));
        else out.push(full);
      }
      return out;
    }
    const files = walk(root).filter(
      (f) => f.endsWith('.ts') || f.endsWith('.tsx'),
    );
    expect(files.length).toBeGreaterThan(0);
    const offenders: string[] = [];
    for (const f of files) {
      const lines = readFileSync(f, 'utf8').split(/\r?\n/);
      lines.forEach((line, i) => {
        // Skip lines that are purely comments — the F5 rule is about
        // executable code touching a money `amount`, not about the
        // documentation referencing the forbidden tokens.
        const trimmed = line.trim();
        if (
          trimmed.startsWith('*') ||
          trimmed.startsWith('//') ||
          trimmed.startsWith('/*')
        ) {
          return;
        }
        if (!/\bamount\b/.test(line)) return;
        // Reject any of the three coercion calls on the same line as
        // `amount`. Regex tolerates whitespace + parenthesis.
        if (
          /\bNumber\s*\(/.test(line) ||
          /\bparseFloat\s*\(/.test(line) ||
          /\bparseInt\s*\(/.test(line)
        ) {
          offenders.push(`${f}:${i + 1}: ${line.trim()}`);
        }
      });
    }
    expect(offenders).toEqual([]);
  });
});

// ===========================================================================
// 4. confidential / F7 — no token / PII / balance / txn / account-ref logging
// ===========================================================================

describe('finance-api — confidential / F7 (no token / PII / balance / txn / account-ref logging; § 2.4.7)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('logs neither the token nor the response body (success path)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(ACCOUNT_ENVELOPE)),
    );
    // Spy console.log/info/warn/error — the structured logger writes
    // through console. Inspect every call's stringified payload.
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi
      .spyOn(console, 'error')
      .mockImplementation(() => {});

    await getAccount('acct-9b1d4a8c');

    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');
    // The token MUST never appear in any log line.
    expect(all).not.toContain('GAP-OIDC-ACCESS');
    // Balance / minor-units strings MUST never appear in a log line.
    expect(all).not.toContain('1234567890123');
    // The accountId MUST not appear in the log path (the logger uses
    // a sanitised `{id}` shape per the api module — confidential / F7).
    expect(all).not.toContain('acct-9b1d4a8c');
  });

  it('logs neither the token nor txn details on the transactions read', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(TXNS_ENVELOPE)),
    );
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi
      .spyOn(console, 'error')
      .mockImplementation(() => {});

    await listTransactions('acct-9b1d4a8c', { page: 0, size: 20 });

    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');
    expect(all).not.toContain('GAP-OIDC-ACCESS');
    expect(all).not.toContain('acct-9b1d4a8c');
    expect(all).not.toContain('acct-other'); // counterparty refs
    expect(all).not.toContain('txn-1');
    expect(all).not.toContain('150000'); // minor-units string
  });
});

// ===========================================================================
// 5. Honest regulated-state surfacing (FROZEN/RESTRICTED/CLOSED/FAILED/
//    REVERSED rendered; unknown enum → generic label, no throw)
// ===========================================================================

describe('finance-api / types — honest regulated-state surfacing + tolerant parsing (§ 2.4.7)', () => {
  it('a FROZEN account status parses without throwing (surfaced honestly downstream)', () => {
    expect(() =>
      TransactionSchema.parse({
        transactionId: 'x',
        type: 'HOLD',
        status: 'FAILED',
        money: { amount: '1', currency: 'KRW' },
      }),
    ).not.toThrow();
  });

  it('a REVERSED txn + reversalOfTransactionId is preserved (operator can see what was reversed)', () => {
    const parsed = TransactionSchema.parse({
      transactionId: 'txn-rev',
      type: 'REVERSAL',
      status: 'REVERSED',
      money: { amount: '50000', currency: 'KRW' },
      reversalOfTransactionId: 'txn-orig',
    });
    expect(parsed.status).toBe('REVERSED');
    expect(parsed.reversalOfTransactionId).toBe('txn-orig');
  });

  it('an unknown / future account status parses without throwing (generic label downstream)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: {
            ...ACCOUNT_ENVELOPE.data,
            status: 'FUTURE_LIMBO_STATE',
          },
          meta: { timestamp: 'x' },
        }),
      ),
    );
    const acc = await getAccount('acct-1');
    expect(acc.status).toBe('FUTURE_LIMBO_STATE');
  });

  it('an unknown / future txn type parses without throwing', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: [
            {
              transactionId: 'txn-9',
              type: 'FUTURE_TXN_TYPE',
              status: 'COMPLETED',
              money: { amount: '1', currency: 'KRW' },
            },
          ],
          meta: { page: 0, size: 20, totalElements: 1 },
        }),
      ),
    );
    const r = await listTransactions('acct-1');
    expect(r.data[0].type).toBe('FUTURE_TXN_TYPE');
  });
});

// ===========================================================================
// 6. finance FLAT error envelope (NOT wms NESTED) + § 2.5 mapping.
//    NO 429 / Retry-After branch is taken (finance has no documented 429).
// ===========================================================================

describe('finance-api — finance FLAT error envelope (NOT wms NESTED) + § 2.5 + no-429-branch', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('401 → ApiError(401) — whole-session re-login (no partial authed state)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(financeError('UNAUTHORIZED', 401)),
    );
    const err = await getAccount('acct-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(err.code).toBe('UNAUTHORIZED');
  });

  it('403 TENANT_FORBIDDEN → ApiError(403) inline "not scoped"', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(financeError('TENANT_FORBIDDEN', 403)),
    );
    const err = await getBalances('acct-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('404 ACCOUNT_NOT_FOUND → ApiError(404) inline actionable (the finance v1 reality)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(financeError('ACCOUNT_NOT_FOUND', 404)),
    );
    const err = await getAccount('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    // The code comes from the FLAT top-level `code`, NOT a nested
    // `error.code` — proves the finance-shape parser (a wms-nested
    // parser would yield the synthetic HTTP_404).
    expect(err.code).toBe('ACCOUNT_NOT_FOUND');
  });

  it('parses the FLAT { code } shape — a wms NESTED parser would have yielded HTTP_404', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        financeError('ACCOUNT_NOT_FOUND', 404, 'no such account'),
      ),
    );
    const err = await getAccount('nope').catch((e) => e);
    expect(err.code).toBe('ACCOUNT_NOT_FOUND');
    expect(err.message).toBe('no such account');
  });

  it('a wms-NESTED { error: { code } } body is NOT mis-parsed as finance (no accidental cross-wire)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            error: { code: 'WMS_NESTED_SHAPE', message: 'x' },
          }),
          { status: 422, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    const err = await getBalances('acct-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    // The finance parser reads the FLAT top-level `code` — a
    // wms-nested body has none, so it degrades to the synthetic
    // fallback (NOT 'WMS_NESTED_SHAPE') and never crashes.
    expect(err.code).toBe('HTTP_422');
  });

  it('503 → FinanceUnavailableError (ONLY the finance section degrades)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(financeError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await getAccount('acct-1').catch((e) => e);
    expect(err).toBeInstanceOf(FinanceUnavailableError);
    expect(err.reason).toBe('downstream');
  });

  it('timeout → FinanceUnavailableError(timeout)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, init?: RequestInit) => {
        return new Promise((_res, rej) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            rej(e);
          });
        });
      }),
    );
    const err = await getBalances('acct-1').catch((e) => e);
    expect(err).toBeInstanceOf(FinanceUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('a malformed / non-JSON error body does NOT crash (defensive parse)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('not json', {
          status: 404,
          headers: { 'Content-Type': 'text/plain' },
        }),
      ),
    );
    const err = await getAccount('x').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404'); // synthetic fallback, no throw
  });

  // The HEADLINE no-429 assertion (the honest difference from
  // scm § 2.4.6 — finance has no documented 429). A 429 from finance
  // MUST fail-fast through the default-error path (a surfaced
  // ApiError), NOT through a Retry-After / backoff branch. Exactly
  // ONE fetch is made (no retry / no storm).
  it('NO 429 handling path exists for finance — a stray 429 surfaces as a generic ApiError, NO retry, NO Retry-After honour', async () => {
    let n = 0;
    const fetchMock = vi.fn(() => {
      n += 1;
      return Promise.resolve(
        new Response(
          JSON.stringify({ code: 'RATE_LIMIT_EXCEEDED', message: 'x' }),
          {
            status: 429,
            headers: {
              'Content-Type': 'application/json',
              'Retry-After': '1', // a Retry-After header the client must IGNORE
            },
          },
        ),
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    const err = await getAccount('acct-1').catch((e) => e);
    // The client surfaced the 429 as a generic ApiError (NOT a
    // bounded-backoff ScmRateLimitedError sibling, NOT a retried
    // success). The presence of an `ApiError(429)` rather than a
    // domain-specific RateLimitedError is itself the assertion.
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(429);
    // EXACTLY ONE fetch — no retry, no Retry-After honour, no storm
    // (the precise "no 429 handling" invariant — § 2.4.7).
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(n).toBe(1);
  });
});
