import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs';
import path from 'node:path';

/**
 * TASK-PC-FE-292 — a domain section renders only once a tenant is ASSUMED.
 *
 * Without an assumed token the domain clients fall back to the base IAM token,
 * which for this console client carries `tenant_id=iam`; the gateways reject it
 * and the pages turn the 401 into «세션 만료». Measured 2026-09-16: 6 domain
 * sections, 0 of them had a «select a tenant» gate.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));

/**
 * TASK-MONO-718 — the gate now also asks whether the ASSUMED tenant is one the
 * section's product serves, so it needs the registry. `catalogResult` is what
 * `getCatalog()` returns (or throws, when set to an Error).
 */
let catalogResult: unknown = {
  degraded: false,
  products: [{ productKey: 'ecommerce', available: true, tenants: ['ecommerce'] }],
};
vi.mock('@/features/catalog', () => ({
  getCatalog: async () => {
    if (catalogResult instanceof Error) throw catalogResult;
    return catalogResult;
  },
}));

import { renderToStaticMarkup } from 'react-dom/server';
import { DomainTenantGate, tenantSelectionRequired } from '@/widgets/domain-tenant-gate';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  REFRESH_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
} from '@/shared/lib/session';

beforeEach(() => cookieJar.clear());

function operatorSession() {
  cookieJar.set(ACCESS_COOKIE, 'base.iam');
  cookieJar.set(OPERATOR_COOKIE, 'op');
  cookieJar.set(REFRESH_COOKIE, 'ref');
}

describe('tenantSelectionRequired', () => {
  it('🔴 logged in, no assumed token → required (the live 2026-09-16 state)', async () => {
    operatorSession();
    expect(await tenantSelectionRequired()).toBe(true);
  });

  it('🔴 a tenant cookie WITHOUT its assumed token is still required (a pre-292 session carrying `iam`)', async () => {
    operatorSession();
    cookieJar.set(TENANT_COOKIE, 'iam');
    expect(await tenantSelectionRequired()).toBe(true);
  });

  it('assumed token present → not required (control: selecting a tenant opens the section, as today)', async () => {
    operatorSession();
    cookieJar.set(TENANT_COOKIE, 'ecommerce');
    cookieJar.set(ASSUMED_TOKEN_COOKIE, 'assumed');
    expect(await tenantSelectionRequired()).toBe(false);
  });

  it('sample visitor (no session cookies at all) → not required (answered by the sample router)', async () => {
    expect(await tenantSelectionRequired()).toBe(false);
  });
});

describe('DomainTenantGate', () => {
  const child = <p data-testid="section-body">body</p>;

  it('renders the gate, not the section, when a tenant is required', async () => {
    operatorSession();
    const out = await DomainTenantGate({ section: 'E-Commerce', children: child });
    const html = renderToStaticMarkup(out);
    expect(html).toContain('domain-no-tenant');
    expect(html).not.toContain('section-body');
  });

  it('renders the section untouched once assumed', async () => {
    operatorSession();
    cookieJar.set(ASSUMED_TOKEN_COOKIE, 'assumed');
    const out = await DomainTenantGate({ section: 'E-Commerce', children: child });
    expect(renderToStaticMarkup(out)).toContain('section-body');
    expect(renderToStaticMarkup(out)).not.toContain('domain-no-tenant');
  });
});

describe('TASK-MONO-718 — the assumed tenant must be one the section serves', () => {
  const child = <p data-testid="section-body">body</p>;

  /** Logged in AND switched into `demo-corp` — the exact live state measured. */
  function assumedInto(tenant: string) {
    operatorSession();
    cookieJar.set(TENANT_COOKIE, tenant);
    cookieJar.set(ASSUMED_TOKEN_COOKIE, 'assumed');
  }

  beforeEach(() => {
    catalogResult = {
      degraded: false,
      products: [
        // 🔴 2026-09-22 15차 창 실측값. 이전 판은 `['ecommerce']` 였는데 라이브는
        //    그 모양을 내지 않는다 — `demo-corp` 는 ecommerce 에 **자격이 있다**.
        { productKey: 'ecommerce', available: true, tenants: ['demo-corp', 'ecommerce'] },
      ],
    };
  });

  it('🔴🔴 TASK-MONO-719 — `demo-corp` does NOT trip this gate, because the registry SERVES it', async () => {
    // 🔴 This cell used to assert the opposite, on a fixture of
    // `tenants: ['ecommerce']`. The 2026-09-22 demo window measured the live
    // registry and it does not produce that shape:
    //     ecommerce -> [demo-corp, ecommerce]
    // so `product.tenants.includes('demo-corp')` is TRUE and the notice can
    // never render. The screen the operator actually saw was the ordinary
    // «표시할 주문이 없습니다» — while tenant=ecommerce held 5 orders at the
    // same instant, through the same URL.
    //
    // 🔵 The registry is not wrong: `demo-corp` IS entitled to the ecommerce
    //    product (it carries the operator roles — TASK-BE-576). That the ROWS
    //    live under `tenant_id=ecommerce` is a SEPARATE proposition. This gate
    //    asks entitlement; the failure lives in data ownership.
    // ⇒ TASK-MONO-719 owner decision ⓑ moves that case to `OtherTenantHint`,
    //   beside the empty list. This cell pins that the gate stays out of it.
    assumedInto('demo-corp');
    const out = await DomainTenantGate({
      section: 'E-Commerce',
      productKey: 'ecommerce',
      children: child,
    });
    const html = renderToStaticMarkup(out);
    expect(html).toContain('section-body');
    expect(html).not.toContain('domain-tenant-mismatch');
  });

  it('🔴 a tenant the product genuinely does NOT serve → the mismatch notice (a shape the live system CAN produce)', async () => {
    // Reachable because `selectableTenants()` is the union ACROSS products: a
    // tenant registered only under scm is selectable, and walking into
    // /ecommerce with it is exactly the case this notice was written for.
    // 🔵 That is the honest scope of the 718 gate — it was never the case the
    //    demo measured, and this fixture says which one it is.
    catalogResult = {
      degraded: false,
      products: [
        { productKey: 'ecommerce', available: true, tenants: ['demo-corp', 'ecommerce'] },
        { productKey: 'scm', available: true, tenants: ['demo-corp', 'other-corp'] },
      ],
    };
    assumedInto('other-corp');
    const out = await DomainTenantGate({
      section: 'E-Commerce',
      productKey: 'ecommerce',
      children: child,
    });
    const html = renderToStaticMarkup(out);
    expect(html).toContain('domain-tenant-mismatch');
    expect(html).not.toContain('section-body');
    // It must NAME both sides — a notice that says only "wrong tenant" leaves
    // the operator to guess which one to switch to.
    expect(html).toContain('other-corp');
    expect(html).toContain('ecommerce');
  });

  it('control — assumed into a tenant the product DOES serve → the section renders', async () => {
    assumedInto('ecommerce');
    const out = await DomainTenantGate({
      section: 'E-Commerce',
      productKey: 'ecommerce',
      children: child,
    });
    const html = renderToStaticMarkup(out);
    expect(html).toContain('section-body');
    expect(html).not.toContain('domain-tenant-mismatch');
  });

  it('🔵 opt-in — without productKey the gate behaves exactly as before', async () => {
    // wms · scm · erp · finance all rendered their data under `demo-corp` in the
    // same window, so TASK-MONO-718 § 제외 keeps them out. This cell pins that
    // the widening is a per-section choice, not a side effect of the change.
    assumedInto('demo-corp');
    const out = await DomainTenantGate({ section: 'WMS', children: child });
    expect(renderToStaticMarkup(out)).toContain('section-body');
  });

  it.each([
    ['a degraded registry', { degraded: true, products: [] }],
    ['a registry that throws', new Error('registry down')],
    ['a product that is absent', { degraded: false, products: [] }],
    [
      'a product with no tenants',
      {
        degraded: false,
        products: [{ productKey: 'ecommerce', available: true, tenants: [] }],
      },
    ],
  ])(
    '🔴 %s cannot prove a mismatch → the section renders (never block on "I could not check")',
    async (_label, result) => {
      // A gate that blocked here would turn a registry blip into a dead
      // section — the opposite of what this notice is for.
      assumedInto('demo-corp');
      catalogResult = result;
      const out = await DomainTenantGate({
        section: 'E-Commerce',
        productKey: 'ecommerce',
        children: child,
      });
      const html = renderToStaticMarkup(out);
      expect(html).toContain('section-body');
      expect(html).not.toContain('domain-tenant-mismatch');
    },
  );

  it('🔴 no tenant assumed at all → the ORIGINAL «select a tenant» gate still wins', async () => {
    // Order matters: the mismatch notice must never pre-empt the case the
    // 292 gate already owns, or the operator is told to switch tenants while
    // having selected none.
    operatorSession();
    const out = await DomainTenantGate({
      section: 'E-Commerce',
      productKey: 'ecommerce',
      children: child,
    });
    const html = renderToStaticMarkup(out);
    expect(html).toContain('domain-no-tenant');
    expect(html).not.toContain('domain-tenant-mismatch');
  });

  it('the ecommerce layout is the ONLY section that passes productKey', () => {
    const CONSOLE = path.resolve(__dirname, '..', '..', 'src', 'app', '(console)');
    const withKey = ['ecommerce', 'wms', 'scm', 'erp', 'finance', 'ledger'].filter(
      (s) => /productKey=/.test(readFileSync(path.join(CONSOLE, s, 'layout.tsx'), 'utf8')),
    );
    expect(withKey).toEqual(['ecommerce']);
  });
});

describe('every domain section is behind the gate (Failure Scenario 3)', () => {
  const CONSOLE = path.resolve(__dirname, '..', '..', 'src', 'app', '(console)');
  const DOMAIN_SECTIONS = ['ecommerce', 'wms', 'scm', 'erp', 'finance', 'ledger'];
  const DOMAIN_FEATURE = /from '@\/features\/(ecommerce|wms|scm|erp|finance|ledger)-/;

  function files(dir: string): string[] {
    return readdirSync(dir).flatMap((n) => {
      const f = path.join(dir, n);
      return statSync(f).isDirectory() ? files(f) : /\.tsx?$/.test(n) ? [f] : [];
    });
  }

  it.each(DOMAIN_SECTIONS)('%s/layout.tsx wraps the section in DomainTenantGate', (section) => {
    const layout = path.join(CONSOLE, section, 'layout.tsx');
    expect(existsSync(layout), `${section} has no layout.tsx`).toBe(true);
    const src = readFileSync(layout, 'utf8');
    expect(src).toMatch(/<DomainTenantGate[\s>]/);
    expect(src).toContain('{children}');
  });

  it('🔵 non-vacuity + no section left out: every (console) section that imports a domain feature is listed', () => {
    const importing = readdirSync(CONSOLE)
      .filter((d) => statSync(path.join(CONSOLE, d)).isDirectory())
      .filter((d) => files(path.join(CONSOLE, d)).some((f) => DOMAIN_FEATURE.test(readFileSync(f, 'utf8'))));
    expect(importing.length).toBeGreaterThan(0);
    expect(importing.filter((d) => !DOMAIN_SECTIONS.includes(d))).toEqual([]);
  });
});
