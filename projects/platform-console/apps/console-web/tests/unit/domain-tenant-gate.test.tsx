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
