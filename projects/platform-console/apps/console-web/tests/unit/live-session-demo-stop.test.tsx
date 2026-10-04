/**
 * `TASK-PC-FE-306` — 쿠키가 **살아 있는** 세션이 데모 종료를 만나면, **서로 다른 두 번의
 * 연속 `unavailable`** 뒤에만 샘플 셸로 끝난다 (`console-integration-contract.md` § 2.6.2
 * «Live session», 소유자 결정 ① 2026-10-04).
 *
 * -----------------------------------------------------------------------------
 * 무엇을 재는가 — 전부 **실제 코드**(레이아웃 가드 헬퍼 · 라우트 · 레이아웃)를 부른다
 * -----------------------------------------------------------------------------
 *  ① 판정표 — 첫 꺼짐 → 머문다 · 두 번째(15초 넘게 떨어진) 꺼짐 → 샘플 · 캐시 창 안의 두
 *     번째 → 머문다 · 꺼짐 다음 켜짐 → 표식 비움(연속 깨짐) · 10분 넘은 첫 판독 → 다시 센다.
 *  ② 대조군 — `running`·`starting`·`not-demo` → 오늘 그대로(홉 없음 · 쿠키 무변경).
 *  ③ 홉 상한 — 인스턴스끼리 신호가 엇갈려도 한 내비게이션이 라우트에 닿는 횟수 ≤ 2 ·
 *     브라우저가 표식을 거부해도 한 홉에서 끝 · «신호 불일치» 착지는 `/login` 이 아니라 그 화면.
 *  ④ 305 경로 보존 — `live` 없는 홉 · 쿠키가 죽은 세션은 305 핸들러 그대로.
 *  ⑤ 표식 쿠키의 속성 · 위조의 범위.
 *  ⑥ 실제 `(console)` 레이아웃이 그 헬퍼를 인증 분기에서만 부른다.
 *
 * 🔴 신호는 `resolveDemoBackendState` 를 **직접 모킹**한다(305 스위트와 같은 방식). 해석기의
 *    캐시·`not-demo` 단락·`/status` 실패는 실제 해석기로 `live-session-demo-stop-resolver.test.ts`
 *    가 잰다.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

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
  useSearchParams: () => new URLSearchParams(''),
}));

const { jar } = vi.hoisted(() => ({
  jar: {
    values: new Map<string, string>(),
    setOpts: new Map<string, Record<string, unknown>>(),
    gets: 0,
    refuse: new Set<string>(),
    xPathname: '/finance/accounts' as string,
  },
}));
const cookieStore = {
  get: (name: string) => {
    jar.gets += 1;
    return jar.values.has(name) ? { name, value: jar.values.get(name)! } : undefined;
  },
  set: (name: string, value: string, opts?: Record<string, unknown>) => {
    if (jar.refuse.has(name)) return; // 브라우저가 거부한 쿠키
    jar.values.set(name, value);
    if (opts) jar.setOpts.set(name, opts);
  },
  delete: (name: string) => {
    jar.values.delete(name);
  },
};
vi.mock('next/headers', () => ({
  cookies: async () => cookieStore,
  headers: async () => new Headers({ 'x-pathname': jar.xPathname }),
}));

vi.mock('@/shared/config/env', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/config/env')>();
  return {
    ...actual,
    getServerEnv: () => ({
      NEXT_PUBLIC_APP_URL: 'http://console.local',
      LOG_LEVEL: 'error',
      CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
      REGISTRY_TIMEOUT_MS: 50,
      IAM_ADMIN_API_BASE: 'http://iam.local',
      ORG_NODES_TIMEOUT_MS: 50,
    }),
    publicOrigin: () => 'http://console.local',
  };
});

/** 신호 — `next` 가 비어 있지 않으면 한 판독마다 하나씩 꺼낸다(인스턴스별로 다른 캐시를 흉내낸다). */
const { demo } = vi.hoisted(() => ({
  demo: { state: 'not-demo' as string, next: [] as string[], calls: 0 },
}));
vi.mock('@/shared/config/demo-backend', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/config/demo-backend')>();
  return {
    ...actual,
    resolveDemoBackendState: async () => {
      demo.calls += 1;
      return demo.next.length > 0 ? demo.next.shift()! : demo.state;
    },
  };
});

// 레이아웃 통합 칸의 «가드를 통과했다» 표지 — 카탈로그 401 은 RE_LOGIN_PATH 로 꺾인다
// (`console-guard-catalog-401.test.tsx` 와 같은 장치). 가드가 홉을 냈다면 그 전에 끊긴다.
vi.mock('@/features/catalog', async () => {
  const { ApiError } = await import('@/shared/api/errors');
  return {
    getCatalog: () => {
      throw new ApiError(401, 'TOKEN_INVALID', 'stub');
    },
  };
});

import {
  liveSessionDemoStopRedirect,
  liveRouteDemoStopOutcome,
  liveShellDemoStopAction,
  parseDemoStopMarker,
  DEMO_STOP_MARKER_COOKIE,
  DEMO_STATE_SNAPSHOT_TTL_MS,
  DEMO_STOP_MARKER_MAX_AGE_S,
} from '@/shared/lib/live-session-demo-stop';
import { GET as demoEndedGET } from '@/app/api/auth/demo-ended/route';
import ConsoleLayout from '@/app/(console)/layout';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  ID_TOKEN_COOKIE,
  LAST_TENANT_COOKIE,
} from '@/shared/lib/session';
import { RE_LOGIN_PATH } from '@/shared/lib/re-login';

const T0 = Date.UTC(2026, 9, 4, 12, 0, 0);
const TTL = DEMO_STATE_SNAPSHOT_TTL_MS;
const SESSION_COOKIES = [
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  ID_TOKEN_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
];

function seedLiveSession(): void {
  for (const c of SESSION_COOKIES) jar.values.set(c, `live-${c}`);
  jar.values.set(LAST_TENANT_COOKIE, 'op-1|demo-corp');
}
const sessionAlive = (): boolean =>
  jar.values.has(ACCESS_COOKIE) && jar.values.has(OPERATOR_COOKIE);
const marker = () => parseDemoStopMarker(jar.values.get(DEMO_STOP_MARKER_COOKIE));

function at(ms: number): void {
  vi.setSystemTime(ms);
}

async function callRoute(pathAndQuery: string) {
  const res = await demoEndedGET(new Request(`http://console.local${pathAndQuery}`));
  const loc = new URL(res.headers.get('location')!);
  return { location: `${loc.pathname}${loc.search}`, cacheControl: res.headers.get('cache-control') };
}

/**
 * 브라우저 한 번의 하드 내비게이션: 레이아웃 가드(인증 분기에서만) → 라우트 → 되돌아온 화면 …
 * 착지할 때까지 따라간다. 🔴 상한(8)을 넘으면 루프다 — 그 자체가 실패다.
 */
async function navigate(path: string): Promise<{ landed: string; routeVisits: number; cacheControls: (string | null)[] }> {
  let current = path;
  let routeVisits = 0;
  const cacheControls: (string | null)[] = [];
  for (let i = 0; i < 8; i += 1) {
    if (!sessionAlive()) return { landed: current, routeVisits, cacheControls };
    const hop = await liveSessionDemoStopRedirect(current);
    if (hop === null) return { landed: current, routeVisits, cacheControls };
    const r = await callRoute(hop);
    routeVisits += 1;
    cacheControls.push(r.cacheControl);
    current = r.location;
  }
  throw new Error(`redirect loop: ${routeVisits} route visits`);
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  at(T0);
  jar.values.clear();
  jar.setOpts.clear();
  jar.refuse.clear();
  jar.gets = 0;
  jar.xPathname = '/finance/accounts';
  demo.state = 'not-demo';
  demo.next = [];
  demo.calls = 0;
});

afterEach(() => {
  vi.useRealTimers();
});

describe('① 판정표 — 두 번의 서로 다른 연속 `unavailable` 만 세션을 끝낸다 (AC-1 · 소유자 결정 ①)', () => {
  it('🔴🔴 첫 `unavailable` → 머문다: 세션 쿠키 그대로, 요청한 화면으로 복귀, 첫 판독이 기록된다', async () => {
    seedLiveSession();
    demo.state = 'unavailable';
    const r = await navigate('/accounts?page=2');
    expect(r.landed).toBe('/accounts?page=2&demo_checked=1');
    expect(r.routeVisits).toBe(1);
    for (const c of SESSION_COOKIES) expect(jar.values.has(c), c).toBe(true);
    expect(marker()).toEqual({ firstUnavailableAt: T0, checkedAt: T0 });
  });

  it('🔴🔴 두 번째 `unavailable`(15초 넘게 떨어진) → 세션 쿠키 6종 삭제 · 표식 삭제 · 샘플 + 안내 표식', async () => {
    seedLiveSession();
    demo.state = 'unavailable';
    await navigate('/accounts?page=2');
    at(T0 + TTL + 1);
    const r = await navigate('/accounts?page=2&demo_checked=1');
    expect(r.landed).toBe('/accounts?page=2&signed_out=demo_stopped');
    for (const c of SESSION_COOKIES) expect(jar.values.has(c), c).toBe(false);
    expect(jar.values.has(DEMO_STOP_MARKER_COOKIE)).toBe(false);
    // 선호(자격 아님)는 로그아웃과 마찬가지로 남는다(305 와 같다).
    expect(jar.values.get(LAST_TENANT_COOKIE)).toBe('op-1|demo-corp');
  });

  it('🔴🔴 캐시 창 안의 두 번째 판독은 같은 스냅샷일 수 있다 → 끝내지 않는다(가드는 홉도 안 낸다)', async () => {
    seedLiveSession();
    demo.state = 'unavailable';
    await navigate('/accounts');
    at(T0 + TTL - 1);
    const r = await navigate('/accounts');
    expect(r.routeVisits).toBe(0);
    expect(sessionAlive()).toBe(true);
  });

  it('🔴🔴 라우트를 직접 다시 불러도(정확히 15초째) 같은 창 → 끝내지 않는다', async () => {
    seedLiveSession();
    demo.state = 'unavailable';
    await navigate('/accounts');
    at(T0 + TTL);
    const r = await callRoute('/api/auth/demo-ended?live=check&redirect=%2Faccounts');
    expect(r.location).toBe('/accounts?demo_checked=1');
    expect(sessionAlive()).toBe(true);
    expect(marker()?.firstUnavailableAt).toBe(T0); // 첫 판독은 밀리지 않는다
  });

  it('🔴🔴 `unavailable` 다음 `running` → 표식이 비워지고(연속 깨짐), 그 뒤의 꺼짐은 다시 «첫» 판독이다', async () => {
    seedLiveSession();
    demo.state = 'unavailable';
    await navigate('/accounts');
    at(T0 + 5_000);
    demo.state = 'running';
    const cleared = await navigate('/accounts');
    expect(cleared.routeVisits).toBe(1); // `live=clear`
    expect(cleared.landed).toBe('/accounts?demo_checked=1');
    expect(marker()?.firstUnavailableAt).toBe(0);

    // 첫 판독(T0)으로부터 15초를 넘긴 꺼짐이지만, 사이에 켜짐이 있었다 → «두 번째» 가 아니다.
    // (T0+5s 의 라우트 답 이후 15초 홉 창도 지난 시각.)
    at(T0 + 5_000 + TTL + 1);
    demo.state = 'unavailable';
    const again = await navigate('/accounts');
    expect(again.landed).toBe('/accounts?demo_checked=1');
    expect(sessionAlive()).toBe(true);
    expect(marker()?.firstUnavailableAt).toBe(T0 + 5_000 + TTL + 1);
  });

  it('🔴 `unavailable` 다음 `starting` 도 연속을 깬다', async () => {
    seedLiveSession();
    demo.state = 'unavailable';
    await navigate('/accounts');
    at(T0 + TTL + 1);
    demo.state = 'starting';
    await navigate('/accounts');
    expect(marker()?.firstUnavailableAt).toBe(0);
    expect(sessionAlive()).toBe(true);
  });

  it('🔴 10분 넘은 첫 판독은 «연속» 의 증거가 아니다 → 다시 센다(끝내지 않는다)', async () => {
    seedLiveSession();
    demo.state = 'unavailable';
    await navigate('/accounts');
    const later = T0 + DEMO_STOP_MARKER_MAX_AGE_S * 1000 + 1;
    at(later);
    const r = await navigate('/accounts');
    expect(r.landed).toBe('/accounts?demo_checked=1');
    expect(sessionAlive()).toBe(true);
    expect(marker()?.firstUnavailableAt).toBe(later);
  });

  it('🔵 판정은 305 의 `sessionEndDestination` 하나 — 순수 함수 칸', () => {
    const m = { firstUnavailableAt: T0, checkedAt: T0 };
    const base = { intent: 'check' as const, marker: m, startedAt: T0 + TTL + 1, settledAt: T0 + TTL + 1 };
    expect(liveRouteDemoStopOutcome({ ...base, state: 'unavailable' })).toEqual({ end: true });
    for (const s of ['running', 'starting', 'not-demo'] as const) {
      expect(liveRouteDemoStopOutcome({ ...base, state: s })).toEqual({ end: false, firstUnavailableAt: 0 });
    }
    expect(liveRouteDemoStopOutcome({ ...base, intent: 'clear', state: null })).toEqual({
      end: false,
      firstUnavailableAt: 0,
    });
  });
});

describe('② 대조군 — `running`·`starting`·`not-demo` 는 오늘 그대로 (AC-2)', () => {
  it.each(['running', 'starting', 'not-demo'])('🔵 `%s` → 홉 없음 · 쿠키 무변경', async (s) => {
    seedLiveSession();
    demo.state = s;
    const before = new Map(jar.values);
    const r = await navigate('/accounts');
    expect(r.routeVisits).toBe(0);
    expect(r.landed).toBe('/accounts');
    expect(jar.values).toEqual(before);
  });

  it('🔴 `not-demo` 에서는 표식 쿠키조차 읽지 않는다(가드의 비용 0 — 쿠키를 심어 둬도)', async () => {
    seedLiveSession();
    jar.values.set(DEMO_STOP_MARKER_COOKIE, `${T0 - 60_000}.${T0 - 60_000}`);
    demo.state = 'not-demo';
    jar.gets = 0;
    expect(await liveSessionDemoStopRedirect('/accounts')).toBeNull();
    expect(jar.gets).toBe(0);
  });

  it('🔵 순수 함수 — 대기 판독이 없으면 꺼짐 아님은 전부 `stay`', () => {
    for (const s of ['running', 'starting', 'not-demo'] as const) {
      expect(liveShellDemoStopAction({ state: s, marker: null, now: T0, urlChecked: false })).toBe('stay');
      expect(
        liveShellDemoStopAction({ state: s, marker: { firstUnavailableAt: 0, checkedAt: T0 }, now: T0, urlChecked: false }),
      ).toBe('stay');
    }
  });
});

describe('③ 홉 상한 · «신호 불일치» 착지 (Edge Case 2)', () => {
  it('🔴🔴 라우트가 다시 읽은 신호가 꺼짐이 아니면 → `/login` 이 아니라 요청한 화면(세션은 작동한다)', async () => {
    seedLiveSession();
    demo.next = ['unavailable', 'running']; // 가드는 꺼짐, 라우트(다른 인스턴스)는 켜짐
    demo.state = 'running';
    const r = await navigate('/accounts?page=2');
    expect(r.landed).toBe('/accounts?page=2&demo_checked=1');
    expect(r.landed.startsWith('/login')).toBe(false);
    for (const c of SESSION_COOKIES) expect(jar.values.has(c), c).toBe(true);
  });

  it.each([
    ['가드 꺼짐 / 라우트 켜짐 반복', ['unavailable', 'running']],
    ['가드 켜짐 / 라우트 꺼짐 반복', ['running', 'unavailable']],
    ['꺼짐 · 꺼짐 · 켜짐 반복', ['unavailable', 'unavailable', 'running']],
  ])('🔴🔴 인스턴스끼리 신호가 엇갈려도(%s) 한 내비게이션의 라우트 방문 ≤ 2', async (_label, cycle) => {
    seedLiveSession();
    for (let step = 0; step < 6; step += 1) {
      demo.next = Array.from({ length: 12 }, (_, i) => cycle[i % cycle.length]!);
      // 이미 표식이 있는 상태 · 없는 상태를 다 지나가도록 매번 15초 넘게 띄운다.
      at(T0 + step * (TTL + 1));
      const r = await navigate('/accounts');
      expect(r.routeVisits).toBeLessThanOrEqual(2);
    }
  });

  it('🔴 브라우저가 표식을 거부해도(Secure 불일치 등) URL 의 `demo_checked=1` 이 한 홉에서 끊는다', async () => {
    seedLiveSession();
    jar.refuse.add(DEMO_STOP_MARKER_COOKIE);
    demo.state = 'unavailable';
    const r = await navigate('/accounts');
    expect(r.routeVisits).toBe(1);
    expect(r.landed).toBe('/accounts?demo_checked=1');
    // 그때는 두 판독 규칙이 성립할 수 없다 → 끝내지 않는다(오늘의 동작).
    at(T0 + TTL + 1);
    await navigate('/accounts?demo_checked=1');
    expect(sessionAlive()).toBe(true);
  });

  it('🔴 표식이 있으면 URL 의 `demo_checked=1` 은 홉을 영원히 막지 못한다(새로고침이 두 번째 판독이 된다)', async () => {
    seedLiveSession();
    demo.state = 'unavailable';
    const first = await navigate('/accounts');
    at(T0 + TTL + 1);
    const second = await navigate(first.landed); // 같은 URL 새로고침
    expect(second.landed).toBe('/accounts?signed_out=demo_stopped');
  });

  it('🔵 live 모드의 모든 응답은 `Cache-Control: no-store`', async () => {
    seedLiveSession();
    demo.state = 'unavailable';
    const a = await navigate('/accounts');
    at(T0 + 5_000);
    demo.state = 'running';
    const b = await navigate('/accounts');
    at(T0 + 30_000);
    demo.state = 'unavailable';
    await navigate('/accounts');
    at(T0 + 60_000);
    const c = await navigate('/accounts');
    for (const cc of [...a.cacheControls, ...b.cacheControls, ...c.cacheControls]) expect(cc).toBe('no-store');
    expect(c.landed).toContain('signed_out=demo_stopped');
  });
});

describe('④ 305 경로 보존 — live 모드는 «레이아웃의 홉 + 살아 있는 쿠키» 일 때만', () => {
  it('🔵 `live` 없는 홉(305 의 `/login` 착지에서 온 것)은 305 그대로 — 꺼짐 한 번에 지운다', async () => {
    seedLiveSession();
    demo.state = 'unavailable';
    const r = await callRoute(`/api/auth/demo-ended?redirect=${encodeURIComponent('/accounts')}`);
    expect(r.location).toBe('/accounts?signed_out=demo_stopped');
    expect(sessionAlive()).toBe(false);
  });

  it('🔵 `live` 가 붙어 있어도 쿠키가 이미 죽은 세션(갱신 불가 — 305 의 모집단)은 305 그대로', async () => {
    jar.values.set(REFRESH_COOKIE, 'stale-refresh');
    demo.state = 'unavailable';
    const r = await callRoute('/api/auth/demo-ended?live=check&redirect=%2Faccounts');
    expect(r.location).toBe('/accounts?signed_out=demo_stopped');
    expect(jar.values.has(REFRESH_COOKIE)).toBe(false);
  });

  it('🔵 305 의 «불일치» 착지(`live` 없음)는 그대로 `/login?…demo_checked=1`', async () => {
    seedLiveSession();
    demo.state = 'running';
    const r = await callRoute(`/api/auth/demo-ended?redirect=${encodeURIComponent('/accounts')}`);
    expect(r.location).toBe(
      `/login?error=session_expired&demo_checked=1&redirect=${encodeURIComponent('/accounts')}`,
    );
  });
});

describe('⑤ 표식 쿠키 — 속성과 위조의 범위', () => {
  it('🔵 HttpOnly · SameSite=Lax · Path=/ · Max-Age=600', async () => {
    seedLiveSession();
    demo.state = 'unavailable';
    await navigate('/accounts');
    expect(jar.setOpts.get(DEMO_STOP_MARKER_COOKIE)).toMatchObject({
      httpOnly: true,
      sameSite: 'lax',
      path: '/',
      maxAge: 600,
    });
  });

  it('🔴 형식이 틀린 표식은 없는 것 — 첫 판독으로 다시 센다', async () => {
    seedLiveSession();
    jar.values.set(DEMO_STOP_MARKER_COOKIE, 'garbage;1');
    demo.state = 'unavailable';
    const r = await navigate('/accounts');
    expect(sessionAlive()).toBe(true);
    expect(r.landed).toBe('/accounts?demo_checked=1');
    expect(marker()).toEqual({ firstUnavailableAt: T0, checkedAt: T0 });
  });

  it('🔴 위조한 표식 + 꺼짐 아님 → 아무것도 안 지운다(라우트가 신호를 다시 읽는다)', async () => {
    seedLiveSession();
    jar.values.set(DEMO_STOP_MARKER_COOKIE, `${T0 - 60_000}.${T0 - 60_000}`);
    demo.state = 'running';
    const r = await callRoute('/api/auth/demo-ended?live=check&redirect=%2Faccounts');
    expect(r.location).toBe('/accounts?demo_checked=1');
    expect(sessionAlive()).toBe(true);
  });

  it('🔵 (받아들인 성질) 위조한 표식이 할 수 있는 최대치 = 진짜 꺼짐 한 번 뒤 **자기** 세션 종료', async () => {
    seedLiveSession();
    jar.values.set(DEMO_STOP_MARKER_COOKIE, `${T0 - 60_000}.${T0 - 60_000}`);
    demo.state = 'unavailable';
    const r = await callRoute('/api/auth/demo-ended?live=check&redirect=%2Faccounts');
    expect(r.location).toBe('/accounts?signed_out=demo_stopped');
  });
});

describe('⑥ 실제 `(console)` 레이아웃 — 가드는 인증 분기에서만, 꺼짐을 읽으면 live 홉', () => {
  async function layoutTarget(): Promise<string> {
    try {
      await ConsoleLayout({ children: null });
    } catch (err) {
      if (err instanceof RedirectError) return err.to;
      throw err;
    }
    return '(rendered)';
  }

  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => {
        throw new Error('no network in this suite');
      }),
    );
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('🔴🔴 인증 + `unavailable` → `/api/auth/demo-ended?live=check&redirect=<path>`', async () => {
    seedLiveSession();
    jar.xPathname = '/finance/accounts?page=2';
    demo.state = 'unavailable';
    expect(await layoutTarget()).toBe(
      `/api/auth/demo-ended?live=check&redirect=${encodeURIComponent('/finance/accounts?page=2')}`,
    );
  });

  it.each(['running', 'starting', 'not-demo'])(
    '🔵 대조군 — 인증 + `%s` → 가드를 지나 셸로(카탈로그 401 표지 = RE_LOGIN_PATH)',
    async (s) => {
      seedLiveSession();
      demo.state = s;
      expect(await layoutTarget()).toBe(RE_LOGIN_PATH);
    },
  );

  it('🔵 샘플 방문자(쿠키 없음)에게는 가드를 걸지 않는다 — 신호도 묻지 않는다', async () => {
    demo.state = 'unavailable';
    await layoutTarget();
    expect(demo.calls).toBe(0);
  });
});
