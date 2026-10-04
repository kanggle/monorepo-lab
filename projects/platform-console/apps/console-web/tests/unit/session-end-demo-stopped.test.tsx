/**
 * `TASK-PC-FE-305` — 데모 종료 뒤 남은 세션은 **로그인 벽이 아니라 샘플 셸**로 끝난다
 * (`console-integration-contract.md` § 2.6.2).
 *
 * -----------------------------------------------------------------------------
 * 무엇을 재는가 — 네 층, 전부 **실제 코드**를 import 해서 부른다
 * -----------------------------------------------------------------------------
 *  ① 판정 `sessionEndDestination` — 신호 4값 전부(`unavailable`→샘플, 나머지→로그인).
 *  ② `/login?error=session_expired` 페이지 — 그 판정으로 넘기는가 / 대조군은 오늘 그대로인가.
 *  ③ `GET /api/auth/demo-ended` — 신호를 **다시** 읽고, 꺼졌을 때만 쿠키를 지우고 샘플로.
 *     아니면 쿠키를 안 건드리고 `demo_checked=1` 로 로그인(루프 상한).
 *  ④ 샘플 셸의 안내 — 표식이 있을 때만 뜨고, 그때 Query 캐시를 비운다(PC-FE-299 AC-5 승계).
 *
 * 🔴 신호는 `resolveDemoBackendState` 를 **직접 모킹**한다(형제 스위트와 같은 방식) — 그 함수가
 *    네트워크를 어떻게 읽는지는 `demo-backend.test.ts` 의 몫이다. 이 파일이 재는 것은 그 값을
 *    **받은 뒤의 분기**다.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const redirectMock = vi.fn();
const { nav } = vi.hoisted(() => ({ nav: { search: '' } }));
vi.mock('next/navigation', () => ({
  redirect: (path: string) => {
    redirectMock(path);
    throw new Error(`REDIRECT:${path}`);
  },
  useSearchParams: () => new URLSearchParams(nav.search),
}));

const cookieJar = new Map<string, string>();
const cookieDeletes: string[] = [];
const cookiesMock = {
  get: (name: string) => (cookieJar.has(name) ? { value: cookieJar.get(name)! } : undefined),
  set: (name: string, value: string) => {
    cookieJar.set(name, value);
  },
  delete: (name: string) => {
    cookieJar.delete(name);
    cookieDeletes.push(name);
  },
};
vi.mock('next/headers', () => ({ cookies: async () => cookiesMock }));

vi.mock('@/shared/config/env', () => ({
  getServerEnv: () => ({ NEXT_PUBLIC_APP_URL: 'http://console.local', LOG_LEVEL: 'info' }),
  publicOrigin: () => 'http://console.local',
}));

const { demo } = vi.hoisted(() => ({ demo: { state: 'not-demo' as string, calls: 0 } }));
vi.mock('@/shared/config/demo-backend', () => ({
  resolveDemoBackendState: async () => {
    demo.calls += 1;
    return demo.state;
  },
}));

vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));
vi.mock('@/widgets/demo-credentials/DemoLoginCredentials', () => ({
  DemoLoginCredentials: () => null,
}));
vi.mock('@/widgets/demo-notice/DemoBackendNotice', () => ({
  DemoBackendNotice: () => null,
}));

import { sessionEndDestination } from '@/shared/lib/session-end';
import { GET as demoEndedGET } from '@/app/api/auth/demo-ended/route';
import { DemoSignedOutNotice } from '@/widgets/sample-visitor/DemoSignedOutNotice';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  ID_TOKEN_COOKIE,
  LAST_TENANT_COOKIE,
} from '@/shared/lib/session';
import { SESSION_EXPIRED } from '@/shared/lib/re-login';

const SESSION_COOKIES = [
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  ID_TOKEN_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
];

function seedStaleSession(): void {
  for (const c of SESSION_COOKIES) cookieJar.set(c, `stale-${c}`);
  cookieJar.set(LAST_TENANT_COOKIE, 'op-1|demo-corp');
}

async function renderLogin(sp: { error?: string; redirect?: string; demo_checked?: string }) {
  const { default: LoginPage } = await import('@/app/(auth)/login/page');
  const el = await LoginPage({ searchParams: Promise.resolve(sp) });
  const qc = new QueryClient();
  render(<QueryClientProvider client={qc}>{el}</QueryClientProvider>);
}

/** 페이지가 redirect() 를 던지면 그 목적지, 아니면 null. */
async function loginDestination(sp: { error?: string; redirect?: string; demo_checked?: string }) {
  try {
    await renderLogin(sp);
    return null;
  } catch (e) {
    const m = /^REDIRECT:(.*)$/.exec((e as Error).message);
    if (!m) throw e;
    return m[1];
  }
}

async function callRoute(query = ''): Promise<{ location: string; cacheControl: string | null }> {
  const res = await demoEndedGET(new Request(`http://console.local/api/auth/demo-ended${query}`));
  const loc = new URL(res.headers.get('location')!);
  return { location: `${loc.pathname}${loc.search}`, cacheControl: res.headers.get('cache-control') };
}

beforeEach(() => {
  redirectMock.mockClear();
  cookieJar.clear();
  cookieDeletes.length = 0;
  demo.state = 'not-demo';
  demo.calls = 0;
  nav.search = '';
});

describe('① 판정 sessionEndDestination — 신호 4값 (TASK-PC-FE-305 AC-1/AC-2)', () => {
  it('🔴🔴 `unavailable`(데모 꺼짐) → 샘플', () => {
    expect(sessionEndDestination('unavailable')).toBe('sample');
  });
  it('🔵 대조군 — `running`(진짜 세션 만료) → 로그인', () => {
    expect(sessionEndDestination('running')).toBe('login');
  });
  it('🔵 대조군 — `starting`(켜지는 중, 꺼진 것이 아니다) → 로그인', () => {
    expect(sessionEndDestination('starting')).toBe('login');
  });
  it('🔵 대조군 — `not-demo`(물어볼 컨트롤 플레인이 없다) → 로그인', () => {
    expect(sessionEndDestination('not-demo')).toBe('login');
  });
});

describe('② /login?error=session_expired — 판정이 착지에서 쓰인다 (AC-1/AC-2/AC-3)', () => {
  it('🔴🔴 `unavailable` → 세션 종료 라우트로 넘긴다(요청한 화면을 싣고)', async () => {
    demo.state = 'unavailable';
    const to = await loginDestination({ error: SESSION_EXPIRED, redirect: '/accounts?page=2' });
    expect(to).toBe(`/api/auth/demo-ended?redirect=${encodeURIComponent('/accounts?page=2')}`);
  });

  it('🔴 `unavailable`, 목적지 없음(401 지점은 redirect 를 안 싣는다) → 라우트 기본(`/`)', async () => {
    demo.state = 'unavailable';
    expect(await loginDestination({ error: SESSION_EXPIRED })).toBe('/api/auth/demo-ended');
  });

  it.each(['running', 'starting', 'not-demo'])(
    '🔵 대조군 — `%s` → 넘기지 않고 오늘의 일반 세션 만료 문구',
    async (s) => {
      demo.state = s;
      expect(await loginDestination({ error: SESSION_EXPIRED })).toBeNull();
      expect(screen.getByRole('alert').textContent).toContain('세션이 만료');
    },
  );

  it('🔵 대조군 — 마커 없는 일반 /login 은 신호를 묻지도, 넘기지도 않는다', async () => {
    demo.state = 'unavailable';
    expect(await loginDestination({})).toBeNull();
    expect(demo.calls).toBe(0);
  });

  it('🔴 루프 상한 — `demo_checked=1` 이면 `unavailable` 이어도 다시 넘기지 않는다', async () => {
    demo.state = 'unavailable';
    expect(await loginDestination({ error: SESSION_EXPIRED, demo_checked: '1' })).toBeNull();
  });
});

describe('③ GET /api/auth/demo-ended — 신호를 다시 읽고, 꺼졌을 때만 지운다 (AC-1/AC-2/AC-4)', () => {
  it('🔴🔴 `unavailable` → 세션 쿠키 6종 삭제 · 샘플 착지 + 안내 표식 · no-store', async () => {
    demo.state = 'unavailable';
    seedStaleSession();
    const r = await callRoute(`?redirect=${encodeURIComponent('/accounts?page=2')}`);
    expect(r.location).toBe('/accounts?page=2&signed_out=demo_stopped');
    expect(r.cacheControl).toBe('no-store');
    for (const c of SESSION_COOKIES) expect(cookieJar.has(c), c).toBe(false);
    // 선호(자격 아님)는 로그아웃과 마찬가지로 남는다.
    expect(cookieJar.get(LAST_TENANT_COOKIE)).toBe('op-1|demo-corp');
    expect(demo.calls).toBe(1);
  });

  it.each(['running', 'starting', 'not-demo'])(
    '🔵 대조군 — `%s` → 쿠키를 안 건드리고 `demo_checked=1` 로 로그인',
    async (s) => {
      demo.state = s;
      seedStaleSession();
      const r = await callRoute(`?redirect=${encodeURIComponent('/accounts')}`);
      expect(r.location).toBe(
        `/login?error=session_expired&demo_checked=1&redirect=${encodeURIComponent('/accounts')}`,
      );
      expect(r.cacheControl).toBe('no-store');
      expect(cookieDeletes).toEqual([]);
    },
  );

  it.each([
    ['//evil.example', '/'],
    ['https://evil.example/x', '/'],
    ['/api/auth/refresh', '/'],
    ['/login?error=session_expired', '/'],
    ['/onboarding', '/'],
    ['/onboarding/create', '/'],
  ])('🔴 목적지 소독 — %s → %s (샘플로 닿지 않거나 사이트 밖)', async (raw, expected) => {
    demo.state = 'unavailable';
    const r = await callRoute(`?redirect=${encodeURIComponent(raw)}`);
    expect(r.location).toBe(`${expected}?signed_out=demo_stopped`);
  });
});

describe('④ 샘플 셸 안내 — 표식이 있을 때만, 그때 Query 캐시를 비운다 (AC-1/AC-4)', () => {
  function renderNotice(qc: QueryClient) {
    render(
      <QueryClientProvider client={qc}>
        <DemoSignedOutNotice />
      </QueryClientProvider>,
    );
  }

  it('🔴🔴 `signed_out=demo_stopped` → 안내 + 지난 세션의 캐시 비움', () => {
    nav.search = 'signed_out=demo_stopped';
    const qc = new QueryClient();
    qc.setQueryData(['accounts'], { items: ['previous-session'] });
    renderNotice(qc);
    expect(screen.getByTestId('demo-signed-out-notice').textContent).toContain(
      '데모 서버가 종료되어 로그아웃되었습니다',
    );
    expect(qc.getQueryCache().getAll()).toHaveLength(0);
  });

  it('🔵 대조군 — 표식 없음(평범한 샘플 방문) → 아무것도 안 그리고 캐시도 안 건드린다', () => {
    nav.search = '';
    const qc = new QueryClient();
    qc.setQueryData(['sample'], { ok: true });
    renderNotice(qc);
    expect(screen.queryByTestId('demo-signed-out-notice')).toBeNull();
    expect(qc.getQueryCache().getAll()).toHaveLength(1);
  });
});
