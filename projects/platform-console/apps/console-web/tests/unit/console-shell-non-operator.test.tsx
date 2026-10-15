/**
 * 🔴🔴 `(console)` 가드 — «로그인 · 운영자 아님» 셸 (TASK-MONO-773 S6 · console-integration-contract § 2.6.4).
 *
 * 무엇을 재는가 (실제 레이아웃 모듈을 부르고, 돌려받은 React 엘리먼트 트리를 걷는다 — 형제
 * `console-shell-sample-visitor-guard.test.tsx` 와 같은 모양):
 *
 *   1. 가드 3행 — access **그리고** 표식 `console_non_operator` **그리고** 운영자 쿠키 없음 → 리다이렉트 없이 셸.
 *   2. 🔴 그 셸은 운영자 토큰 호출을 **하나도** 하지 않는다 — 레지스트리 · `/me` · org-nodes 가 전부 `fetch` 를
 *      지나므로 «fetch 0» 이 그 단언이다(Failure Scenario 1 — 401 재로그인 고리). 벨 · 하트비트 · 스위처는
 *      마운트되지 않고, 계정 메뉴(로그아웃 — AC-7) · 데모 알림은 마운트된다.
 *   3. 사이드바는 «비운영자» 판(두 항목 — 항목 자체는 `sidebar-non-operator.test.tsx` 가 잰다).
 *   4. 경로 허용 목록 — `/new-tenant` · `/guide` (경로 같음 · 쿼리 허용)만, 나머지(`/` 포함)는 `/new-tenant`.
 *   5. 🔴 대조군 — 표식 없는 «access 만» 은 지금 그대로(§ 2.6.1 — 표식이 술어이고 «운영자 쿠키 없음» 이 아니다,
 *      R2) · 운영자 셸은 표식이 남아 있어도 운영자 셸.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import type { ReactElement, ReactNode } from 'react';

const MARKER = 'console_non_operator';

const cookieJar = new Map<string, string>();
const req = vi.hoisted(() => ({ path: '/new-tenant' }));
vi.mock('next/headers', () => ({
  headers: async () => new Headers({ 'x-pathname': req.path }),
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));

vi.mock('@/shared/config/env', () => ({
  getServerEnv: () => ({
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    IAM_ADMIN_API_BASE: 'http://iam.local',
    ORG_NODES_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
  }),
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
  usePathname: () => req.path,
  useRouter: () => ({ push: () => undefined, refresh: () => undefined }),
}));

import ConsoleLayout from '@/app/(console)/layout';
import { ACCESS_COOKIE, OPERATOR_COOKIE, REFRESH_COOKIE } from '@/shared/lib/session';
import { DemoHeartbeat } from '@/widgets/demo-heartbeat/DemoHeartbeat';
import { DemoBackendNotice } from '@/widgets/demo-notice/DemoBackendNotice';
import { SampleVisitorBanner } from '@/widgets/sample-visitor/SampleVisitorBanner';
import { AccountMenu } from '@/shared/ui/AccountMenu';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';
import { TenantSwitcher } from '@/features/tenant';
import { NotificationBell } from '@/features/notifications';

function elements(node: ReactNode): ReactElement[] {
  if (Array.isArray(node)) return node.flatMap(elements);
  if (node === null || typeof node !== 'object' || !('props' in node)) return [];
  const el = node as ReactElement<{ children?: ReactNode }>;
  return [el, ...elements(el.props.children)];
}

function mounted(tree: ReactNode, type: unknown): ReactElement[] {
  return elements(tree).filter((e) => e.type === type);
}

let fetchSpy: ReturnType<typeof vi.fn>;

beforeEach(() => {
  cookieJar.clear();
  req.path = '/new-tenant';
  fetchSpy = vi.fn(async () => {
    throw new Error('network reached');
  });
  vi.stubGlobal('fetch', fetchSpy);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function nonOperatorSession() {
  cookieJar.set(ACCESS_COOKIE, 'iam.acc');
  cookieJar.set(REFRESH_COOKIE, 'iam.ref');
  cookieJar.set(MARKER, '1');
}

describe('1 · 2 — 비운영자 셸: 리다이렉트 없음 · 운영자 토큰 호출 0', () => {
  beforeEach(nonOperatorSession);

  it('🔴 `/new-tenant` → 셸이 렌더되고(리다이렉트 없음), fetch 는 **0** 이다', async () => {
    await expect(ConsoleLayout({ children: null })).resolves.toBeTruthy();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('🔴 벨 · 하트비트 · 테넌트 스위처는 마운트되지 않는다', async () => {
    const tree = await ConsoleLayout({ children: null }).catch((e: unknown) => e);
    expect(tree, 'the layout rendered (no redirect)').not.toBeInstanceOf(RedirectError);
    expect(mounted(tree as ReactNode, NotificationBell)).toHaveLength(0);
    expect(mounted(tree as ReactNode, DemoHeartbeat)).toHaveLength(0);
    expect(mounted(tree as ReactNode, TenantSwitcher)).toHaveLength(0);
  });

  it('🔴 계정 메뉴(로그아웃 — AC-7) · 데모 알림은 마운트된다 · 샘플 배너는 없다', async () => {
    const tree = await ConsoleLayout({ children: null }).catch((e: unknown) => e);
    expect(tree, 'the layout rendered (no redirect)').not.toBeInstanceOf(RedirectError);
    expect(mounted(tree as ReactNode, AccountMenu)).toHaveLength(1);
    expect(mounted(tree as ReactNode, DemoBackendNotice)).toHaveLength(1);
    expect(mounted(tree as ReactNode, SampleVisitorBanner)).toHaveLength(0);
  });

  it('🔴 사이드바는 비운영자 판(variant="non-operator")', async () => {
    const tree = await ConsoleLayout({ children: null }).catch((e: unknown) => e);
    expect(tree, 'the layout rendered (no redirect)').not.toBeInstanceOf(RedirectError);
    const [nav] = mounted(tree as ReactNode, ConsoleSidebarNav);
    expect((nav?.props as Record<string, unknown> | undefined)?.variant).toBe('non-operator');
  });
});

describe('4 — 경로 허용 목록 (`/new-tenant` · `/guide` 만)', () => {
  beforeEach(nonOperatorSession);

  it.each(['/new-tenant', '/new-tenant?step=1', '/guide', '/guide?tab=menu'])(
    '🔴 %s → 셸 (리다이렉트 없음)',
    async (path) => {
      req.path = path;
      await expect(ConsoleLayout({ children: null })).resolves.toBeTruthy();
      expect(fetchSpy).not.toHaveBeenCalled();
    },
  );

  it.each(['/', '/finance/accounts', '/operators', '/tenants', '/new-tenant-x', '/guide/extra', '/dashboards/overview'])(
    '🔴 %s → 307 /new-tenant (운영자 화면은 그려지지 않는다)',
    async (path) => {
      req.path = path;
      await expect(ConsoleLayout({ children: null })).rejects.toMatchObject({ to: '/new-tenant' });
      expect(fetchSpy).not.toHaveBeenCalled();
    },
  );
});

describe('5 — 🔵 대조군: 표식이 술어다', () => {
  it('표식 없는 «access + refresh» (유휴 · 교환 장애) → 지금 그대로 갱신 홉', async () => {
    req.path = '/finance/accounts';
    cookieJar.set(ACCESS_COOKIE, 'iam.acc');
    cookieJar.set(REFRESH_COOKIE, 'iam.ref');
    await expect(ConsoleLayout({ children: null })).rejects.toMatchObject({
      to: `/api/auth/refresh?redirect=${encodeURIComponent('/finance/accounts')}`,
    });
  });

  it('표식만 있고 access 가 없으면(표식은 자격이 아니다) → 지금 그대로 /login', async () => {
    req.path = '/new-tenant';
    cookieJar.set(MARKER, '1');
    await expect(ConsoleLayout({ children: null })).rejects.toMatchObject({
      to: `/login?redirect=${encodeURIComponent('/new-tenant')}`,
    });
  });

  it('운영자 셸에 남은 표식은 무시된다 — 운영자 셸 그대로(실경로가 fetch 에 닿는다)', async () => {
    req.path = '/dashboards/overview';
    cookieJar.set(ACCESS_COOKIE, 'iam.acc');
    cookieJar.set(OPERATOR_COOKIE, 'op.jwt');
    cookieJar.set(MARKER, '1');
    const tree = await ConsoleLayout({ children: null });
    expect(mounted(tree, NotificationBell)).toHaveLength(1);
    expect(mounted(tree, TenantSwitcher)).toHaveLength(1);
    expect(fetchSpy).toHaveBeenCalled();
    const [nav] = mounted(tree, ConsoleSidebarNav);
    expect((nav.props as Record<string, unknown>).variant ?? 'operator').toBe('operator');
  });
});
