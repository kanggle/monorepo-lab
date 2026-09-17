/**
 * 🔴🔴 `(console)` 가드 — **`ADR-MONO-074` 가 이 파일의 기대값을 바꿨다** (`TASK-PC-FE-282` AC-6 · AC-11).
 *
 * 이 파일은 원래 «미인증 요청은 `/login` 으로 튕기고 fetch 0» 을 쟀다. 그 기대는
 * 「빨개져서 고친」 것이 아니라 **결정이 뒤집은** 것이다: `ADR-MONO-074`
 * (ACCEPTED — A · R1ⓐ · R2ⓐ · R3ⓐ) 가 `(console)/layout.tsx` 가드의 의미를
 *
 *     «익명은 들어올 수 없다»  →  «익명은 들어오지만 백엔드에 닿을 수 없다»
 *
 * 로 바꿨다. 그래서 이 파일이 재는 명제도 바뀐다:
 *
 *   1. 샘플 방문자(두 쿠키 모두 없음) → 리다이렉트 **없이** 셸이 렌더된다 — 그리고
 *      🔴 `fetch` 는 **여전히 0** 이다(레지스트리·조직 노드 호출이 샘플 라우터로 답해진다).
 *      «들어왔다» 와 «백엔드에 닿았다» 를 한 파일에서 함께 재는 것이 요점이다.
 *   2. 셸의 익명판: 배너 · «로그인» 링크(`?redirect=<원래경로>`) · 샘플 테넌트 하나 ·
 *      `DemoHeartbeat`/`DemoBackendNotice` **미렌더**(ADR-MONO-071 D8) · 계정 메뉴 없음.
 *   3. 🔴 반쪽 세션(액세스만 / 운영자만) → **예전 그대로** `/login?redirect=…`.
 *   4. 🔵 대조군 — 인증된 운영자 → 예전 셸(계정 메뉴 · 하트비트), 샘플 배너 없음, 그리고
 *      실제 경로가 `fetch` 에 닿는다(분기가 실경로를 삼키지 않았다).
 *
 * 🔴 여전히 «가드 로직을 흉내낸 순수 함수» 가 아니라 **실제 레이아웃 모듈**을 호출한다.
 *    반환된 React 엘리먼트 트리를 걸어서 무엇이 마운트되는지 본다(클라이언트 컴포넌트는
 *    실행하지 않는다 — 여기서 재는 것은 셸의 구성이다).
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import type { ReactElement, ReactNode } from 'react';

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  headers: async () => new Headers({ 'x-pathname': '/finance/accounts' }),
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
  usePathname: () => '/finance/accounts',
  useRouter: () => ({ push: () => undefined, refresh: () => undefined }),
}));

import ConsoleLayout from '@/app/(console)/layout';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';
import { SAMPLE_TENANT_ID } from '@/shared/sample/codes';
import { SampleVisitorBanner } from '@/widgets/sample-visitor/SampleVisitorBanner';
import { SampleScreenNotice } from '@/widgets/sample-visitor/SampleScreenNotice';
import { DemoHeartbeat } from '@/widgets/demo-heartbeat/DemoHeartbeat';
import { DemoBackendNotice } from '@/widgets/demo-notice/DemoBackendNotice';
import { AccountMenu } from '@/shared/ui/AccountMenu';
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
  fetchSpy = vi.fn(async () => {
    throw new Error('network reached');
  });
  vi.stubGlobal('fetch', fetchSpy);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('샘플 방문자 (두 쿠키 모두 없음) — ADR-MONO-074 A1 · A7', () => {
  it('🔴 리다이렉트 없이 셸이 렌더되고, fetch 는 **0** 이다', async () => {
    const tree = await ConsoleLayout({ children: null });
    expect(tree).toBeTruthy();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('배너와 «준비 중» 알림은 레이아웃이 그린다', async () => {
    const tree = await ConsoleLayout({ children: null });
    expect(mounted(tree, SampleVisitorBanner)).toHaveLength(1);
    expect(mounted(tree, SampleScreenNotice)).toHaveLength(1);
  });

  it('🔴 DemoHeartbeat · DemoBackendNotice 는 마운트되지 않는다 (ADR-MONO-071 D8)', async () => {
    const tree = await ConsoleLayout({ children: null });
    expect(mounted(tree, DemoHeartbeat)).toHaveLength(0);
    expect(mounted(tree, DemoBackendNotice)).toHaveLength(0);
  });

  it('계정 메뉴 자리에 «로그인» — 원래 경로를 redirect 로 싣는다', async () => {
    const tree = await ConsoleLayout({ children: null });
    expect(mounted(tree, AccountMenu)).toHaveLength(0);
    const login = elements(tree).find((e) => (e.props as Record<string, unknown>)['data-testid'] === 'sample-visitor-login');
    expect(login).toBeDefined();
    expect((login!.props as { href: string }).href).toBe(
      `/login?redirect=${encodeURIComponent('/finance/accounts')}`,
    );
  });

  it('테넌트 스위처는 샘플 테넌트 하나(읽기 전용), 알림 벨은 그대로 마운트', async () => {
    const tree = await ConsoleLayout({ children: null });
    const [switcher] = mounted(tree, TenantSwitcher);
    expect(switcher.props).toMatchObject({
      tenants: [SAMPLE_TENANT_ID],
      activeTenant: SAMPLE_TENANT_ID,
    });
    expect(mounted(tree, NotificationBell)).toHaveLength(1);
  });
});

describe('🔴 반쪽 세션 — 예전 경로 그대로 /login', () => {
  it('액세스 쿠키만 → /login?redirect=<원래경로>', async () => {
    cookieJar.set(ACCESS_COOKIE, 'access');
    await expect(ConsoleLayout({ children: null })).rejects.toMatchObject({
      to: `/login?redirect=${encodeURIComponent('/finance/accounts')}`,
    });
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('운영자 쿠키만 → /login?redirect=<원래경로>', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'operator');
    await expect(ConsoleLayout({ children: null })).rejects.toMatchObject({
      to: `/login?redirect=${encodeURIComponent('/finance/accounts')}`,
    });
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

describe('🔵 대조군 — 인증된 운영자는 예전 셸', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'access');
    cookieJar.set(OPERATOR_COOKIE, 'operator');
  });

  it('계정 메뉴 · 하트비트 · 데모 알림이 마운트되고, 샘플 배너는 없다', async () => {
    const tree = await ConsoleLayout({ children: null });
    expect(mounted(tree, AccountMenu)).toHaveLength(1);
    expect(mounted(tree, DemoHeartbeat)).toHaveLength(1);
    expect(mounted(tree, DemoBackendNotice)).toHaveLength(1);
    expect(mounted(tree, SampleVisitorBanner)).toHaveLength(0);
    expect(mounted(tree, SampleScreenNotice)).toHaveLength(0);
  });

  it('🔴 실경로가 네트워크에 닿는다 — 샘플 분기가 운영자 호출을 삼키지 않았다', async () => {
    await ConsoleLayout({ children: null });
    expect(fetchSpy).toHaveBeenCalled();
  });
});
