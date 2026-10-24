import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

/**
 * TASK-PC-FE-368 (ADR-MONO-084 D7) — the operator overview's per-card HEADLINE
 * METRIC, composed in the console server (`operator-overview-headline.ts`).
 *
 * What this file pins (contract § 2.4.9.1 «Card headline metric»):
 *   - which metric each domain contributes, asked with `period=week`;
 *   - AC-1/AC-3 failure isolation, both directions: a metrics read that fails
 *     (5xx · 403 · unparseable · network · timeout) changes that card's `headline`
 *     and nothing else, never becomes a 0; a card leg that fails leaves its
 *     healthy headline in place;
 *   - AC-2 shape control: the old fields are untouched and in their old order, the
 *     headline is appended, the card order is unchanged;
 *   - a metrics `401` is the session's (whole 401), exactly like a card leg's.
 * Same seams as `dashboard-composition-routes.test.ts` (cookie jar, env, fetch).
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));

vi.mock('@/shared/config/env', () => ({
  getServerEnv: () => ({
    IAM_ADMIN_API_BASE: 'http://iam.local',
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    FINANCE_BASE_URL: 'http://finance.local',
    LEDGER_BASE_URL: 'http://ledger.local',
    ERP_BASE_URL: 'http://erp.local',
    ECOMMERCE_ADMIN_BASE_URL: 'http://ecommerce.local/api/admin',
  }),
}));

vi.mock('@/shared/config/demo-backend', () => ({
  resolveBackendUrl: async (u: string) => u,
}));

import { composeOperatorOverview } from '@/shared/composition/operator-overview-composition';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
} from '@/shared/lib/session';
import { OperatorOverviewSchema } from '@/features/operator-overview/api/operator-overview-types';

type Body = { body: unknown };
const fixture = JSON.parse(
  readFileSync(
    path.resolve(__dirname, '../../../../specs/contracts/fixtures/operator-overview-leg-bodies.json'),
    'utf8',
  ),
) as { legs: Record<string, Body>; headlineMetrics: Record<string, Body> };

const DOMAINS = ['iam', 'wms', 'scm', 'finance', 'erp', 'ecommerce'] as const;
type Domain = (typeof DOMAINS)[number];

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** `<domain>` for a card leg, `<domain>:metrics` for a headline-metric leg. */
function legOf(url: string): string {
  if (url.includes('/api/admin/metrics')) return 'iam:metrics';
  if (url.includes('/dashboard/metrics')) return 'wms:metrics';
  if (url.includes('/po/metrics')) return 'scm:metrics';
  if (url.includes('/api/finance/accounts/metrics')) return 'finance:metrics';
  if (url.includes('/approval/metrics')) return 'erp:metrics';
  if (url.includes('/api/admin/orders/metrics')) return 'ecommerce:metrics';
  if (url.includes('/api/admin/accounts')) return 'iam';
  if (url.includes('/dashboard/orders')) return 'wms';
  if (url.includes('/procurement/po')) return 'scm';
  if (url.includes('/reconciliation/discrepancies')) return 'finance';
  if (url.includes('/approval/inbox')) return 'erp';
  if (url.includes('/api/admin/orders')) return 'ecommerce';
  throw new Error(`unexpected url ${url}`);
}

function healthy(leg: string): Response {
  return leg.endsWith(':metrics')
    ? json(fixture.headlineMetrics[leg.split(':')[0]].body)
    : json(fixture.legs[leg].body);
}

let fetchMock: ReturnType<typeof vi.fn>;
/** Per-leg override; return `undefined` to fall through to the healthy answer. */
let override: (leg: string, init: RequestInit | undefined) => Promise<Response | undefined>;

function fullSession(tenant = 'acme') {
  cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
  cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
  cookieJar.set(TENANT_COOKIE, tenant);
  cookieJar.set(ASSUMED_TOKEN_COOKIE, 'GAP-ASSUMED');
}

beforeEach(() => {
  cookieJar.clear();
  override = async () => undefined;
  fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const leg = legOf(String(url));
    return (await override(leg, init)) ?? healthy(leg);
  });
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

type WireCard = { domain: Domain; status: string; headline?: { status: string; metrics?: unknown[] } } & Record<
  string,
  unknown
>;

async function run(): Promise<{ status: number; cards: WireCard[]; raw: string }> {
  const answer = await composeOperatorOverview();
  const raw = JSON.stringify(answer.body);
  return { status: answer.status, cards: (answer.body as { cards: WireCard[] }).cards ?? [], raw };
}

const metricsUrls = () =>
  fetchMock.mock.calls.map(([u]) => String(u)).filter((u) => legOf(u).endsWith(':metrics'));

describe('headline metric — which metric each card carries, asked for the week', () => {
  it('each card carries its domain headline from its own metrics response (current vs previous, integer strings)', async () => {
    fullSession();
    const { status, cards } = await run();
    expect(status).toBe(200);
    const by = Object.fromEntries(cards.map((c) => [c.domain, c.headline])) as Record<string, any>;

    const pick = (d: Domain) =>
      by[d].metrics.map((m: any) => [m.key, m.unit, m.currency, m.current, m.previous]);

    expect(by.iam.status).toBe('ok');
    expect(pick('iam')).toEqual([['loginFailure', 'count', null, '7', '4']]);
    expect(pick('wms')).toEqual([['qtyShipped', 'count', null, '640', '700']]);
    // scm: every currency, never added together.
    expect(pick('scm')).toEqual([
      ['poAmount:KRW', 'money', 'KRW', '2450000', '1800000'],
      ['poAmount:USD', 'money', 'USD', '1200', '0'],
    ]);
    expect(pick('finance')).toEqual([['transferAmount:KRW', 'money', 'KRW', '1250000', '1500000']]);
    expect(pick('erp')).toEqual([['approved', 'count', null, '8', '8']]);
    expect(pick('ecommerce')).toEqual([['revenue', 'money', 'KRW', '3210000', '2900000']]);
    for (const d of DOMAINS) expect(by[d].period).toBe('week');
  });

  it('every metrics request asks period=week; iam also scopes by the tenantId query param', async () => {
    fullSession('acme');
    await run();
    const urls = metricsUrls();
    expect(urls).toHaveLength(6);
    for (const u of urls) expect(new URL(u).searchParams.get('period')).toBe('week');
    const iam = urls.find((u) => legOf(u) === 'iam:metrics')!;
    expect(new URL(iam).searchParams.get('tenantId')).toBe('acme');
    // finance reads the ACCOUNT service base, not the ledger base the card leg uses.
    expect(urls.find((u) => legOf(u) === 'finance:metrics')).toMatch(/^http:\/\/finance\.local\//);
  });

  it('the wire headline parses with the production card schema', async () => {
    fullSession();
    const answer = await composeOperatorOverview();
    const parsed = OperatorOverviewSchema.parse(answer.body);
    expect(parsed.cards.every((c) => c.headline?.status === 'ok')).toBe(true);
  });
});

describe('AC-2 — shape control: old bytes unchanged, a field appended, order unchanged', () => {
  it('card order, per-card key order and the old fields are exactly what they were; `headline` comes last', async () => {
    fullSession();
    const { cards } = await run();
    expect(cards.map((c) => c.domain)).toEqual([...DOMAINS]);
    for (const c of cards) {
      expect(Object.keys(c)).toEqual(['domain', 'status', 'data', 'headline']);
      expect(c.status).toBe('ok');
      expect(c.data).toEqual(fixture.legs[c.domain].body);
    }
  });

  it('the card legs are still exactly one call per card (the headline is a separate pass)', async () => {
    fullSession();
    await run();
    const cardLegs = fetchMock.mock.calls.map(([u]) => legOf(String(u))).filter((l) => !l.endsWith(':metrics'));
    expect(cardLegs.sort()).toEqual([...DOMAINS].sort());
  });
});

describe('AC-1/AC-3 — a metrics read failing changes only that card\'s headline, and is never a 0', () => {
  const failures: Array<[string, () => Promise<Response>]> = [
    ['503', async () => json({ code: 'UNAVAILABLE' }, 503)],
    ['500', async () => json({ code: 'BOOM' }, 500)],
    ['404 (route not deployed)', async () => json({ code: 'NOT_FOUND' }, 404)],
    ['unparseable body (not JSON)', async () => new Response('<html>gateway</html>', { status: 200 })],
    ['JSON that is not a metrics response', async () => json({ hello: 'world' })],
    ['a metrics response without the expected key', async () => json({
      period: 'week',
      timezone: 'Asia/Seoul',
      current: { from: 'a', to: 'b' },
      previous: { from: 'c', to: 'd' },
      metrics: [{ key: 'somethingElse', unit: 'count', currency: null, current: '0', previous: '0' }],
      series: [],
    })],
    ['network error', async () => { throw new TypeError('fetch failed'); }],
  ];

  for (const domain of DOMAINS) {
    for (const [name, fail] of failures) {
      it(`${domain} metrics ${name} → ${domain} headline degraded; every card (old fields) and every other headline intact`, async () => {
        fullSession();
        const baseline = await run();
        override = async (leg) => (leg === `${domain}:metrics` ? fail() : undefined);
        const got = await run();
        expect(got.status).toBe(200);

        const mine = got.cards.find((c) => c.domain === domain)!;
        expect(mine.headline).toEqual({ status: 'degraded' });
        // The failed metric is not shown as a number of any kind — least of all 0.
        expect(JSON.stringify(mine.headline)).not.toMatch(/current|previous|"0"/);
        // The card's own number is intact.
        expect(mine.status).toBe('ok');
        expect(mine.data).toEqual(fixture.legs[domain].body);

        // Every OTHER card, including its headline, is byte-identical to the healthy run.
        for (const c of got.cards.filter((x) => x.domain !== domain)) {
          expect(c).toEqual(baseline.cards.find((b) => b.domain === c.domain));
        }
      });
    }

    it(`${domain} metrics 403 → ${domain} headline forbidden («권한 없음»), card and the others intact`, async () => {
      fullSession();
      const baseline = await run();
      override = async (leg) =>
        leg === `${domain}:metrics` ? json({ code: 'PERMISSION_DENIED' }, 403) : undefined;
      const got = await run();
      expect(got.status).toBe(200);
      const mine = got.cards.find((c) => c.domain === domain)!;
      expect(mine.headline).toEqual({ status: 'forbidden' });
      expect(mine.status).toBe('ok');
      for (const c of got.cards.filter((x) => x.domain !== domain)) {
        expect(c).toEqual(baseline.cards.find((b) => b.domain === c.domain));
      }
    });

    it(`the reverse: ${domain} CARD leg down, its metrics fine → card degraded, headline still ok, others intact`, async () => {
      fullSession();
      const baseline = await run();
      override = async (leg) => {
        if (leg === domain) throw new TypeError('fetch failed');
        return undefined;
      };
      const got = await run();
      expect(got.status).toBe(200);
      const mine = got.cards.find((c) => c.domain === domain)!;
      expect(mine).toMatchObject({ status: 'degraded', reason: 'DOWNSTREAM_ERROR' });
      expect(mine.headline).toEqual(baseline.cards.find((b) => b.domain === domain)!.headline);
      expect(mine.headline?.status).toBe('ok');
      for (const c of got.cards.filter((x) => x.domain !== domain)) {
        expect(c).toEqual(baseline.cards.find((b) => b.domain === c.domain));
      }
    });
  }

  it('all six metrics reads down → still 200, all six cards ok, six degraded headlines', async () => {
    fullSession();
    override = async (leg) => (leg.endsWith(':metrics') ? json({}, 503) : undefined);
    const { status, cards } = await run();
    expect(status).toBe(200);
    expect(cards.map((c) => c.status)).toEqual(Array(6).fill('ok'));
    expect(cards.map((c) => c.headline)).toEqual(Array(6).fill({ status: 'degraded' }));
  });

  it('a hanging metrics read is cut at the leg timeout and degrades only its headline', async () => {
    vi.useFakeTimers();
    fullSession();
    override = (leg, init) =>
      leg === 'wms:metrics'
        ? new Promise<Response>((_resolve, reject) => {
            init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
          })
        : Promise.resolve(undefined);
    const pending = composeOperatorOverview();
    await vi.advanceTimersByTimeAsync(4001);
    const answer = await pending;
    expect(answer.status).toBe(200);
    const cards = (answer.body as { cards: WireCard[] }).cards;
    expect(cards.find((c) => c.domain === 'wms')!.headline).toEqual({ status: 'degraded' });
    expect(cards.find((c) => c.domain === 'wms')!.status).toBe('ok');
    expect(cards.filter((c) => c.domain !== 'wms').every((c) => c.headline?.status === 'ok')).toBe(true);
  });
});

describe('AC-3 — 401 is the whole session\'s, exactly like a card leg\'s', () => {
  it('a metrics read answering 401 → route-level 401 TOKEN_INVALID (not a degraded headline)', async () => {
    fullSession();
    override = async (leg) => (leg === 'erp:metrics' ? json({ code: 'TOKEN_INVALID' }, 401) : undefined);
    const answer = await composeOperatorOverview();
    expect(answer.status).toBe(401);
    expect((answer.body as { code: string }).code).toBe('TOKEN_INVALID');
  });

  it('a card leg 401 is still the whole 401 (unchanged)', async () => {
    fullSession();
    override = async (leg) => (leg === 'wms' ? json({ code: 'TOKEN_INVALID' }, 401) : undefined);
    expect((await composeOperatorOverview()).status).toBe(401);
  });

  it('no active tenant → 400 and ZERO calls of either pass', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    const answer = await composeOperatorOverview();
    expect(answer.status).toBe(400);
    expect(fetchMock).toHaveBeenCalledTimes(0);
  });
});
