import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Composition core moved from the former BFF (TASK-PC-FE-302 / ADR-MONO-081 —
 * contract § 2.4.9). These cells pin the rules the move must NOT change
 * (ADR-MONO-081 D2) plus the two the move adds on purpose (R1 log line,
 * R2 timeout without circuit-breaker).
 */

// The schema defaults of `shared/config/env.ts` for the six bases a leg reads.
vi.mock('@/shared/config/env', () => ({
  getServerEnv: () => ({
    IAM_ADMIN_API_BASE: 'http://iam.local',
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    FINANCE_BASE_URL: 'http://finance.local',
    // TASK-PC-FE-364: the finance leg is the ledger read — a DIFFERENT base from
    // FINANCE_BASE_URL in deployment (here only the host differs, so a leg that
    // picked the wrong base is visible in the URL assertions below).
    LEDGER_BASE_URL: 'http://ledger.finance.local',
    ERP_BASE_URL: 'http://erp.local',
    ECOMMERCE_ADMIN_BASE_URL: 'http://ecommerce.local/api/admin',
  }),
}));

vi.mock('@/shared/config/demo-backend', () => ({
  resolveBackendUrl: async (u: string) => u,
}));

const logs: { level: string; event: string; fields: Record<string, unknown> }[] = [];
vi.mock('@/shared/lib/logger', () => ({
  logger: {
    info: (event: string, fields: Record<string, unknown>) =>
      logs.push({ level: 'info', event, fields }),
    warn: (event: string, fields: Record<string, unknown>) =>
      logs.push({ level: 'warn', event, fields }),
    error: (event: string, fields: Record<string, unknown>) =>
      logs.push({ level: 'error', event, fields }),
  },
  newRequestId: () => 'req-1',
}));

import {
  CARD_ORDER,
  compose,
  domainHealthLegs,
  operatorOverviewLegs,
  type FetchLeg,
  type LegSpec,
} from '@/shared/composition/console-composition';

const FIXTURE_PATH = path.resolve(
  __dirname,
  '../../../../../specs/contracts/fixtures/operator-overview-leg-bodies.json',
);
const fixture = JSON.parse(readFileSync(FIXTURE_PATH, 'utf8')) as {
  legs: Record<string, { body: unknown }>;
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** Which leg a URL belongs to — by the path each leg uses (TASK-PC-FE-364). */
function legOf(url: string): string {
  if (url.includes('/api/admin/accounts')) return 'iam';
  if (url.includes('/dashboard/orders')) return 'wms';
  if (url.includes('/procurement/po')) return 'scm';
  if (url.includes('/reconciliation/discrepancies')) return 'finance';
  if (url.includes('/approval/inbox')) return 'erp';
  if (url.includes('/api/admin/orders')) return 'ecommerce';
  throw new Error(`unexpected leg url ${url}`);
}

const CREDS = {
  tenant: 'acme',
  operatorToken: 'OP-TOKEN',
  domainFacingToken: 'DOMAIN-TOKEN',
  requestId: 'req-1',
};

/** The exact URL each card asks — the § 2.4.9.1 producer table, one per card. */
const EXPECTED_LEG_URLS: Record<string, string> = {
  iam: 'http://iam.local/api/admin/accounts?status=LOCKED&page=0&size=1&tenantId=acme',
  wms: 'http://wms.local/api/v1/admin/dashboard/orders?status=RECEIVED&page=0&size=1',
  scm: 'http://scm.local/api/v1/procurement/po?status=DRAFT&page=0&size=1',
  finance:
    'http://ledger.finance.local/api/finance/ledger/reconciliation/discrepancies?status=OPEN&page=0&size=1',
  erp: 'http://erp.local/api/erp/approval/inbox?page=0&size=1',
  ecommerce: 'http://ecommerce.local/api/admin/orders?status=CONFIRMED&page=0&size=1',
};

beforeEach(() => {
  logs.length = 0;
});

describe('operator overview — producer bodies pass through verbatim (AC-1)', () => {
  it('every ok card carries the fixture body byte-equal, in fixed order', async () => {
    const fetchLeg: FetchLeg = async (url) => json(fixture.legs[legOf(url)].body);
    const r = await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg,
    });
    expect(r.unauthorized).toBe(false);
    if (r.unauthorized) return;
    expect(r.envelope.cards.map((c) => c.domain)).toEqual([...CARD_ORDER]);
    for (const c of r.envelope.cards) {
      expect(c.status).toBe('ok');
      expect(c.data).toEqual(fixture.legs[c.domain].body);
      expect('reason' in c).toBe(false);
    }
    expect(typeof r.envelope.asOf).toBe('string');
  });
});

describe('🔴 control 1 — one leg down degrades only its card (AC-2)', () => {
  it.each([
    ['5xx', async () => json({ code: 'INTERNAL' }, 500), 'DOWNSTREAM_ERROR'],
    ['network', async () => { throw new TypeError('fetch failed'); }, 'DOWNSTREAM_ERROR'],
    ['unreadable body', async () => new Response('not json', { status: 200 }), 'DOWNSTREAM_ERROR'],
  ])('scm %s → scm degraded, the other five ok, still a 200 envelope', async (_n, scmAnswer, reason) => {
    const fetchLeg: FetchLeg = async (url) =>
      legOf(url) === 'scm' ? (scmAnswer as () => Promise<Response>)() : json(fixture.legs[legOf(url)].body);
    const r = await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg,
    });
    expect(r.unauthorized).toBe(false);
    if (r.unauthorized) return;
    const scm = r.envelope.cards.find((c) => c.domain === 'scm')!;
    expect(scm).toEqual({ domain: 'scm', status: 'degraded', reason });
    expect(r.envelope.cards.filter((c) => c.status === 'ok')).toHaveLength(5);
  });

  it('a leg slower than its timeout → that card TIMEOUT, others ok (AC-6)', async () => {
    const fetchLeg: FetchLeg = (url, init) =>
      legOf(url) === 'wms'
        ? new Promise<Response>((_, reject) => {
            init.signal?.addEventListener('abort', () => reject(new Error('aborted')));
          })
        : Promise.resolve(json(fixture.legs[legOf(url)].body));
    const r = await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg,
      timeoutMs: 20,
    });
    if (r.unauthorized) throw new Error('unexpected 401');
    expect(r.envelope.cards.find((c) => c.domain === 'wms')).toEqual({
      domain: 'wms',
      status: 'degraded',
      reason: 'TIMEOUT',
    });
    expect(r.envelope.cards.filter((c) => c.status === 'ok')).toHaveLength(5);
  });

  it('all six down → still an envelope (never blank, never 503)', async () => {
    const r = await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg: async () => json({}, 502),
    });
    if (r.unauthorized) throw new Error('unexpected 401');
    expect(r.envelope.cards).toHaveLength(6);
    expect(r.envelope.cards.every((c) => c.status !== 'ok')).toBe(true);
    expect(logs.some((l) => l.event === 'console_composition_all_down')).toBe(true);
  });
});

describe('🔴 control 2 — a data leg 401 collapses the composition (AC-3)', () => {
  it('erp 401 → unauthorized, NOT a degraded card with the rest 200', async () => {
    const fetchLeg: FetchLeg = async (url) =>
      legOf(url) === 'erp' ? json({ code: 'TOKEN_INVALID' }, 401) : json(fixture.legs[legOf(url)].body);
    const r = await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg,
    });
    expect(r).toEqual({ unauthorized: true, domain: 'erp' });
  });

  it('403 is a per-card `forbidden`, not a 401 (TENANT_FORBIDDEN read from the body)', async () => {
    const fetchLeg: FetchLeg = async (url) => {
      const leg = legOf(url);
      if (leg === 'finance') return json({ code: 'TENANT_FORBIDDEN' }, 403);
      if (leg === 'wms') return json({ code: 'NOPE' }, 403);
      return json(fixture.legs[leg].body);
    };
    const r = await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg,
    });
    if (r.unauthorized) throw new Error('unexpected 401');
    const by = Object.fromEntries(r.envelope.cards.map((c) => [c.domain, c]));
    expect(by.finance).toEqual({ domain: 'finance', status: 'forbidden', reason: 'TENANT_FORBIDDEN' });
    expect(by.wms).toEqual({ domain: 'wms', status: 'forbidden', reason: 'PERMISSION_DENIED' });
  });
});

describe('credentials per leg (AC-5 — ADR-MONO-017 D4)', () => {
  it('IAM = operator token + X-Tenant-Id; every other leg = domain-facing token, no X-Tenant-Id', async () => {
    const seen: Record<string, Record<string, string>> = {};
    const fetchLeg: FetchLeg = async (url, init) => {
      seen[legOf(url)] = init.headers as Record<string, string>;
      return json(fixture.legs[legOf(url)].body);
    };
    await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg,
    });
    expect(seen.iam.Authorization).toBe('Bearer OP-TOKEN');
    expect(seen.iam['X-Tenant-Id']).toBe('acme');
    for (const d of ['wms', 'scm', 'finance', 'erp', 'ecommerce']) {
      expect(seen[d].Authorization).toBe('Bearer DOMAIN-TOKEN');
      expect('X-Tenant-Id' in seen[d]).toBe(false);
    }
  });

  it('every leg is a GET with no body (read-only)', async () => {
    const inits: RequestInit[] = [];
    await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg: async (url, init) => {
        inits.push(init);
        return json(fixture.legs[legOf(url)].body);
      },
    });
    expect(inits).toHaveLength(6);
    for (const i of inits) {
      expect(i.method).toBe('GET');
      expect(i.body).toBeUndefined();
    }
  });

  it('scm goes through the gateway path the procurement screen uses — not the docker-only service path', async () => {
    const urls: string[] = [];
    await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg: async (url) => {
        urls.push(url);
        return json(fixture.legs[legOf(url)].body);
      },
    });
    const scm = urls.find((u) => u.includes('/procurement/po'))!;
    expect(scm).toContain('/api/v1/procurement/po');
    expect(scm).not.toMatch(/\/api\/procurement\//);
  });

  it('iam leg sends tenantId as a query param for the ACTIVE (selected) tenant, not only X-Tenant-Id (TASK-PC-FE-304)', async () => {
    const urls: string[] = [];
    await compose(await operatorOverviewLegs({ ...CREDS, tenant: 'ecommerce' }), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg: async (url) => {
        urls.push(url);
        return json(fixture.legs[legOf(url)].body);
      },
    });
    const iam = urls.find((u) => u.includes('/api/admin/accounts'))!;
    expect(iam).toBeDefined();
    expect(new URL(iam).searchParams.get('tenantId')).toBe('ecommerce');
  });

});

describe('TASK-PC-FE-364 — one call per card, swapped one-for-one', () => {
  it('🔴 exactly ONE call per card and six in total — each to the § 2.4.9.1 URL', async () => {
    const urls: string[] = [];
    const r = await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg: async (url) => {
        urls.push(url);
        return json(fixture.legs[legOf(url)].body);
      },
    });
    if (r.unauthorized) throw new Error('unexpected 401');
    // Total unchanged by the swap (owner decision «one call per card»).
    expect(urls).toHaveLength(6);
    const perCard = Object.fromEntries(
      CARD_ORDER.map((d) => [d, urls.filter((u) => legOf(u) === d)]),
    );
    for (const d of CARD_ORDER) {
      expect(perCard[d], `${d}: calls`).toEqual([EXPECTED_LEG_URLS[d]]);
    }
  });

  it('every leg is a real call — no card is decided up front (finance needs no default account)', async () => {
    const legs = await operatorOverviewLegs(CREDS);
    expect(legs).toHaveLength(6);
    for (const l of legs) {
      expect('decided' in l, `${l.domain} decided without a call`).toBe(false);
    }
  });

  it('the finance leg is the ledger read at LEDGER_BASE_URL — not account-service balances', async () => {
    const legs = (await operatorOverviewLegs(CREDS)) as Extract<LegSpec, { url: string }>[];
    const finance = legs.find((l) => l.domain === 'finance')!;
    expect(finance.url.startsWith('http://ledger.finance.local/')).toBe(true);
    expect(finance.url).not.toContain('/balances');
    expect(finance.url).not.toContain('http://finance.local/');
  });

  it('a 403 on a swapped leg is still a per-card `forbidden` — the other five stay ok', async () => {
    const fetchLeg: FetchLeg = async (url) =>
      legOf(url) === 'scm'
        ? json({ code: 'PERMISSION_DENIED', message: 'Access denied' }, 403)
        : json(fixture.legs[legOf(url)].body);
    const r = await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg,
    });
    if (r.unauthorized) throw new Error('unexpected 401');
    expect(r.envelope.cards.find((c) => c.domain === 'scm')).toEqual({
      domain: 'scm',
      status: 'forbidden',
      reason: 'PERMISSION_DENIED',
    });
    expect(r.envelope.cards.filter((c) => c.status === 'ok')).toHaveLength(5);
  });

  it('an unlinked erp inbox (no meta.actorEmployeeId) passes through verbatim — the composer does not reshape it', async () => {
    const notLinked = (
      JSON.parse(readFileSync(FIXTURE_PATH, 'utf8')) as {
        variants: { erpNotLinked: { body: unknown } };
      }
    ).variants.erpNotLinked.body;
    const r = await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg: async (url) =>
        legOf(url) === 'erp' ? json(notLinked) : json(fixture.legs[legOf(url)].body),
    });
    if (r.unauthorized) throw new Error('unexpected 401');
    expect(r.envelope.cards.find((c) => c.domain === 'erp')).toEqual({
      domain: 'erp',
      status: 'ok',
      data: notLinked,
    });
  });
});

describe('R1 — one structured log line per leg, no secrets (AC-7)', () => {
  it('six console_composition_leg lines with route/domain/status/latencyMs/requestId', async () => {
    await compose(await operatorOverviewLegs(CREDS), {
      route: 'operator-overview',
      requestId: 'req-1',
      fetchLeg: async (url) =>
        legOf(url) === 'scm' ? json({}, 500) : json(fixture.legs[legOf(url)].body),
    });
    const lines = logs.filter((l) => l.event === 'console_composition_leg');
    expect(lines.map((l) => l.fields.domain).sort()).toEqual([...CARD_ORDER].sort());
    for (const l of lines) {
      expect(l.fields.route).toBe('operator-overview');
      expect(typeof l.fields.latencyMs).toBe('number');
      expect(l.fields.requestId).toBe('req-1');
    }
    expect(lines.find((l) => l.fields.domain === 'scm')!.fields).toMatchObject({
      status: 'degraded',
      reason: 'DOWNSTREAM_ERROR',
    });
    const dumped = JSON.stringify(logs);
    expect(dumped).not.toContain('OP-TOKEN');
    expect(dumped).not.toContain('DOMAIN-TOKEN');
    expect(dumped).not.toContain('acc-7');
  });
});

describe('domain health legs (§ 2.4.9.2)', () => {
  const healthBy = (answers: Record<string, () => Promise<Response>>): FetchLeg =>
    async (url) => {
      const domain = CARD_ORDER.find((d) =>
        url.startsWith(
          {
            iam: 'http://iam.local',
            wms: 'http://wms.local',
            scm: 'http://scm.local',
            finance: 'http://finance.local',
            erp: 'http://erp.local',
            ecommerce: 'http://ecommerce.local',
          }[d],
        ),
      )!;
      return (answers[domain] ?? (async () => json({ status: 'UP' })))();
    };

  it('six public /actuator/health calls, no Authorization, no X-Tenant-Id', async () => {
    const legs = await domainHealthLegs('req-1');
    expect(legs).toHaveLength(6);
    for (const l of legs as Extract<LegSpec, { url: string }>[]) {
      expect(l.kind).toBe('health');
      expect(l.url).toMatch(/^http:\/\/[a-z]+\.local\/actuator\/health$/);
      expect('Authorization' in l.headers).toBe(false);
      expect('X-Tenant-Id' in l.headers).toBe(false);
    }
  });

  it('a health 401/403 degrades that card — never a composition 401 (no cross-leg collapse)', async () => {
    const r = await compose(await domainHealthLegs('req-1'), {
      route: 'domain-health',
      requestId: 'req-1',
      fetchLeg: healthBy({
        iam: async () => json({}, 401),
        wms: async () => json({}, 403),
      }),
    });
    if (r.unauthorized) throw new Error('health must not collapse to 401');
    const by = Object.fromEntries(r.envelope.cards.map((c) => [c.domain, c]));
    expect(by.iam).toEqual({ domain: 'iam', status: 'degraded', reason: 'DOWNSTREAM_ERROR' });
    expect(by.wms).toEqual({ domain: 'wms', status: 'degraded', reason: 'DOWNSTREAM_ERROR' });
    expect(by.scm).toEqual({ domain: 'scm', status: 'ok', data: { status: 'UP' } });
  });

  it('🔴 Spring 503 + a DOWN health document is an ok card with status DOWN (not degraded)', async () => {
    const r = await compose(await domainHealthLegs('req-1'), {
      route: 'domain-health',
      requestId: 'req-1',
      fetchLeg: healthBy({
        finance: async () => json({ status: 'DOWN' }, 503),
        erp: async () => json({ status: 'OUT_OF_SERVICE' }, 503),
        // A gateway error envelope on 503 is NOT a health document.
        scm: async () => json({ code: 'SERVICE_UNAVAILABLE', status: 503 }, 503),
      }),
    });
    if (r.unauthorized) throw new Error('unexpected 401');
    const by = Object.fromEntries(r.envelope.cards.map((c) => [c.domain, c]));
    expect(by.finance).toEqual({ domain: 'finance', status: 'ok', data: { status: 'DOWN' } });
    expect(by.erp).toEqual({ domain: 'erp', status: 'ok', data: { status: 'OUT_OF_SERVICE' } });
    expect(by.scm).toEqual({ domain: 'scm', status: 'degraded', reason: 'DOWNSTREAM_ERROR' });
  });

  it('never produces CIRCUIT_OPEN — there is no breaker (R2)', async () => {
    const r = await compose(await domainHealthLegs('req-1'), {
      route: 'domain-health',
      requestId: 'req-1',
      fetchLeg: async () => json({}, 503),
    });
    if (r.unauthorized) throw new Error('unexpected 401');
    expect(r.envelope.cards.some((c) => c.reason === 'CIRCUIT_OPEN')).toBe(false);
  });
});
