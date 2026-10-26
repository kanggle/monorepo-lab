/**
 * TASK-MONO-773 S6 — the «테넌트 생성» screen `/new-tenant`: one screen, three modes
 * (console-integration-contract § 2.6.4 «The tenant-creation screen»).
 *
 * | shell state                                   | mode                        | what renders                                   |
 * |-----------------------------------------------|-----------------------------|------------------------------------------------|
 * | non-operator (access + marker, no operator)   | self — first company        | 받은 초대 → «관리자에게 초대를 요청하세요» → 양식 |
 * | operator without a `'*'` `tenant.manage` grant | self — second company       | the same self form (no empty state)            |
 * | `'*'` operator holding `tenant.manage`        | SUPER_ADMIN — first admin   | the tenant + first-admin form (`/api/tenants`) |
 *
 * The page is a Server Component: this suite calls it and walks the returned element tree (client components are
 * not executed — what is measured is WHICH component is mounted with WHICH props). 🔴 The «받은 초대» read is the
 * only network call in the non-operator mode, and it is the IAM onboarding endpoint with the access token as
 * `subjectToken` — never an operator-token call (R1).
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import type { ReactElement, ReactNode } from 'react';

const MARKER = 'console_non_operator';

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  headers: async () => new Headers({ 'x-pathname': '/new-tenant' }),
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));

vi.mock('@/shared/config/env', () => ({
  getServerEnv: () => ({
    OIDC_ISSUER_URL: 'http://iam.local',
    CONSOLE_ONBOARDING_URL: 'http://iam.local/api/admin/onboarding/organizations',
    ONBOARDING_TIMEOUT_MS: 200,
    IAM_ADMIN_API_BASE: 'http://iam.local',
    OPERATORS_TIMEOUT_MS: 200,
    ORG_NODES_TIMEOUT_MS: 200,
  }),
}));
vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

class RedirectError extends Error {
  constructor(public readonly to: string) {
    super(`NEXT_REDIRECT:${to}`);
  }
}
vi.mock('next/navigation', () => ({
  redirect: (to: string) => {
    throw new RedirectError(to);
  },
  notFound: () => {
    throw new Error('NEXT_NOT_FOUND');
  },
  usePathname: () => '/new-tenant',
  useRouter: () => ({ push: () => undefined, refresh: () => undefined }),
}));

import NewTenantPage from '@/app/(console)/new-tenant/page';
import { CreateOrganizationForm, ReceivedInvitations } from '@/features/onboarding';
import { TenantCreateScreen } from '@/features/tenants';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  REFRESH_COOKIE,
  TENANT_COOKIE,
} from '@/shared/lib/session';

function elements(node: ReactNode): ReactElement[] {
  if (Array.isArray(node)) return node.flatMap(elements);
  if (node === null || typeof node !== 'object' || !('props' in node)) return [];
  const el = node as ReactElement<{ children?: ReactNode }>;
  return [el, ...elements(el.props.children)];
}
function mounted(tree: ReactNode, type: unknown): ReactElement[] {
  return elements(tree).filter((e) => e.type === type);
}
function byTestId(tree: ReactNode, id: string): ReactElement | undefined {
  return elements(tree).find((e) => (e.props as Record<string, unknown>)['data-testid'] === id);
}
function props(el: ReactElement | undefined): Record<string, unknown> {
  return (el?.props ?? {}) as Record<string, unknown>;
}

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

type Route = (url: string, init?: RequestInit) => Response | undefined;
let routes: Route[];
let fetchSpy: ReturnType<typeof vi.fn>;

beforeEach(() => {
  cookieJar.clear();
  routes = [];
  fetchSpy = vi.fn(async (url: string, init?: RequestInit) => {
    for (const r of routes) {
      const out = r(String(url), init);
      if (out) return out;
    }
    throw new Error(`unexpected network call: ${String(url)}`);
  });
  vi.stubGlobal('fetch', fetchSpy);
});
afterEach(() => vi.unstubAllGlobals());

/** Render the page; a redirect is an assertion failure here (the modes below must RENDER). */
async function renderPage(): Promise<ReactNode> {
  const out = await Promise.resolve()
    .then(() => NewTenantPage())
    .catch((e: unknown) => e);
  expect(out, 'the page rendered (no redirect)').not.toBeInstanceOf(RedirectError);
  return out as ReactNode;
}

function receivedInvitations(reply: () => Response) {
  routes.push((url) => (url.endsWith('/api/admin/onboarding/received-invitations') ? reply() : undefined));
}
function me(reply: () => Response) {
  routes.push((url) => (url.endsWith('/api/admin/me') ? reply() : undefined));
}
function orgNodes(reply: () => Response) {
  routes.push((url) => (url.includes('/api/admin/org-nodes') ? reply() : undefined));
}

const INVITE = {
  invitationId: 'inv-1',
  tenantId: 'acme-corp',
  tenantDisplayName: 'Acme Corp',
  roles: ['SUPPORT_LOCK'],
  firstAdmin: true,
  expiresAt: '2026-10-22T10:00:00Z',
  invitedAt: '2026-10-15T10:00:00Z',
};

describe('sample visitor — no tenant creation in the sample shell (§ 2.6.4 «Sample mode»)', () => {
  it('🔴 TASK-PC-FE-376 — /new-tenant renders the notice in place (no redirect to /), with 로그인 back to here', async () => {
    const tree = await renderPage();

    expect(byTestId(tree, 'new-tenant-sample-notice')).toBeDefined();
    expect(props(byTestId(tree, 'new-tenant-sample-login')).href).toBe('/login?redirect=%2Fnew-tenant');
    expect(props(byTestId(tree, 'new-tenant-sample-overview')).href).toBe('/dashboards/overview');

    // Nothing that needs a session is mounted, and nothing is asked.
    expect(mounted(tree, CreateOrganizationForm)).toHaveLength(0);
    expect(mounted(tree, TenantCreateScreen)).toHaveLength(0);
    expect(mounted(tree, ReceivedInvitations)).toHaveLength(0);
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

describe('non-operator mode — 받은 초대 → «관리자에게 초대를 요청하세요» → self form', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'iam.acc');
    cookieJar.set(REFRESH_COOKIE, 'iam.ref');
    cookieJar.set(MARKER, '1');
  });

  it('🔴 VERIFIED + one invitation: the list state reaches the read-only panel; the form is the FIRST-company self form', async () => {
    receivedInvitations(() => json({ accountState: 'VERIFIED', items: [INVITE] }, 200));
    const tree = await renderPage();

    const [panel] = mounted(tree, ReceivedInvitations);
    expect(props(panel).state).toEqual({ kind: 'loaded', accountState: 'VERIFIED', items: [INVITE] });
    expect(props(panel).emailVerificationUrl).toBe('http://iam.local/email-verification');

    expect(byTestId(tree, 'new-tenant-ask-admin'), '«회사에 관리자가 있다면 …» copy').toBeDefined();

    const [form] = mounted(tree, CreateOrganizationForm);
    expect(props(form).mode).toBe('first');
    expect(props(form).emailVerificationUrl).toBe('http://iam.local/email-verification');
    expect(mounted(tree, TenantCreateScreen)).toHaveLength(0);
  });

  it('🔴 order on the page: 받은 초대 → ask-admin copy → form (773 Edge Case 2 «받은 초대 먼저»)', async () => {
    receivedInvitations(() => json({ accountState: 'VERIFIED', items: [] }, 200));
    const tree = await renderPage();
    const all = elements(tree);
    const iPanel = all.findIndex((e) => e.type === ReceivedInvitations);
    const iAsk = all.findIndex((e) => (e.props as Record<string, unknown>)['data-testid'] === 'new-tenant-ask-admin');
    const iForm = all.findIndex((e) => e.type === CreateOrganizationForm);
    expect(iPanel).toBeGreaterThan(-1);
    expect(iAsk).toBeGreaterThan(iPanel);
    expect(iForm).toBeGreaterThan(iAsk);
  });

  it('🔴 the ONLY network call is the IAM received-invitations read with the access token as subjectToken (no operator token)', async () => {
    receivedInvitations(() => json({ accountState: 'EMAIL_NOT_VERIFIED', items: [] }, 200));
    await renderPage();
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('http://iam.local/api/admin/onboarding/received-invitations');
    expect(JSON.parse(String(init.body))).toEqual({ subjectToken: 'iam.acc' });
    expect(new Headers(init.headers).get('Authorization')).toBeNull();
  });

  it('🔴 IAM 503 → state «unavailable» (never rendered as «초대 없음»)', async () => {
    receivedInvitations(() => json({ code: 'DOWNSTREAM_ERROR' }, 503));
    const tree = await renderPage();
    const [panel] = mounted(tree, ReceivedInvitations);
    expect(props(panel).state).toEqual({ kind: 'unavailable' });
    // the form is still offered — a read outage does not block creation
    expect(mounted(tree, CreateOrganizationForm)).toHaveLength(1);
  });
});

describe('operator modes', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'iam.acc');
    cookieJar.set(OPERATOR_COOKIE, 'op.jwt');
    cookieJar.set(TENANT_COOKIE, 'demo-corp');
  });

  const meBody = (roles: string[], homeTenantId: string) => ({
    operatorId: 'op-1',
    email: 'op@example.com',
    displayName: 'Op',
    status: 'ACTIVE',
    roles,
    createdAt: '2026-01-01T00:00:00Z',
    homeTenantId,
  });

  it('🔴 a `*` SUPER_ADMIN → the first-admin form (TenantCreateScreen) with org-node options; no self form, no received-invitations read', async () => {
    me(() => json(meBody(['SUPER_ADMIN'], '*'), 200));
    orgNodes(() =>
      json({ items: [{ orgNodeId: 'node-hq', name: 'HQ', parentId: null }], page: 0, size: 100, totalElements: 1, totalPages: 1 }, 200),
    );
    const tree = await renderPage();

    const [screen] = mounted(tree, TenantCreateScreen);
    expect(screen, 'TenantCreateScreen mounted').toBeDefined();
    expect(mounted(tree, CreateOrganizationForm)).toHaveLength(0);
    expect(mounted(tree, ReceivedInvitations)).toHaveLength(0);
    expect(fetchSpy.mock.calls.some(([u]) => String(u).includes('received-invitations'))).toBe(false);
  });

  it('🔴 a company operator (no `*` tenant.manage) → the SECOND-company self form; no empty state', async () => {
    me(() => json(meBody(['TENANT_ADMIN', 'TENANT_BILLING_ADMIN'], 'demo-corp'), 200));
    const tree = await renderPage();

    const [form] = mounted(tree, CreateOrganizationForm);
    expect(props(form).mode).toBe('additional');
    expect(mounted(tree, TenantCreateScreen)).toHaveLength(0);
    expect(mounted(tree, ReceivedInvitations)).toHaveLength(0);
    expect(byTestId(tree, 'new-tenant-ask-admin')).toBeUndefined();
  });

  it('🔵 a company-scoped SUPER_ADMIN role row (home ≠ `*`) is still a company operator → self form', async () => {
    me(() => json(meBody(['SUPER_ADMIN'], 'demo-corp'), 200));
    const tree = await renderPage();
    expect(props(mounted(tree, CreateOrganizationForm)[0]).mode).toBe('additional');
  });

  it('🔵 `/me` cannot be read → fail toward the self form (the producer is the authority — a `*` caller gets 409)', async () => {
    me(() => json({ code: 'DOWNSTREAM_ERROR' }, 503));
    const tree = await renderPage();
    expect(props(mounted(tree, CreateOrganizationForm)[0]).mode).toBe('additional');
    expect(mounted(tree, TenantCreateScreen)).toHaveLength(0);
  });
});
