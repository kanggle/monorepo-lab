/**
 * `TASK-PC-FE-306` AC-3 — 레이아웃 가드는 컨트롤 플레인을 **요청마다** 부르지 않는다.
 * **실제 해석기**(`@/shared/config/demo-backend` → `@demo/backend-resolver`)로 잰다 —
 * 모킹하면 이 파일이 재려는 것(캐시 · `not-demo` 단락 · 실패한 `/status`)이 사라진다.
 *
 *  ① 비데모(`DEMO_API_BASE` 없음) → `/status` 호출 0, 표식 쿠키도 안 읽는다.
 *  ② 데모 → 가드와 `DemoBackendNotice` 가 **같은 스냅샷** — 한 렌더에서 `/status` 1회.
 *  ③ «서로 다른» 의 상수가 해석기의 캐시 TTL 을 넘지 않는다(해석기 TTL 이 늘면 빨강).
 *  ④ 실패한 `/status` **하나**는 세션을 끝내지 않고, 다음 판독이 켜짐이면 표식이 비워진다.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const { jar } = vi.hoisted(() => ({
  jar: { values: new Map<string, string>(), gets: 0 },
}));
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (name: string) => {
      jar.gets += 1;
      return jar.values.has(name) ? { name, value: jar.values.get(name)! } : undefined;
    },
    set: (name: string, value: string) => {
      jar.values.set(name, value);
    },
    delete: (name: string) => {
      jar.values.delete(name);
    },
  }),
}));
vi.mock('@/shared/config/env', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/config/env')>();
  return {
    ...actual,
    getServerEnv: () => ({ NEXT_PUBLIC_APP_URL: 'http://console.local', LOG_LEVEL: 'error' }),
    publicOrigin: () => 'http://console.local',
  };
});

import {
  liveSessionDemoStopRedirect,
  parseDemoStopMarker,
  DEMO_STATE_SNAPSHOT_TTL_MS,
  DEMO_STOP_MARKER_COOKIE,
} from '@/shared/lib/live-session-demo-stop';
import {
  resolveDemoBackendState,
  __resetDemoBackendCache,
} from '@/shared/config/demo-backend';
import { DemoBackendNotice } from '@/widgets/demo-notice/DemoBackendNotice';
import { GET as demoEndedGET } from '@/app/api/auth/demo-ended/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

const ORIGINAL_ENV = { ...process.env };
const T0 = Date.UTC(2026, 9, 4, 12, 0, 0);
const RUNNING = { state: 'running', ip: '13.125.1.2' };
const STOPPED = { state: 'stopped' };

type StatusReply = { ok: true; body: unknown } | { fail: true };
function stubStatus(replies: StatusReply[]) {
  const fetchMock = vi.fn(async (url: string) => {
    expect(String(url)).toMatch(/\/status$/);
    const r = replies.shift();
    if (!r || 'fail' in r) throw new Error('control plane unreachable');
    return { ok: true, json: async () => r.body } as unknown as Response;
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function route(path: string): Promise<string> {
  const res = await demoEndedGET(new Request(`http://console.local${path}`));
  const loc = new URL(res.headers.get('location')!);
  return `${loc.pathname}${loc.search}`;
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(T0);
  __resetDemoBackendCache();
  jar.values.clear();
  jar.gets = 0;
  delete process.env.DEMO_API_BASE;
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  process.env = { ...ORIGINAL_ENV };
});

describe('① 비데모 배포 — 가드의 비용 0 (AC-3)', () => {
  it('🔴🔴 `DEMO_API_BASE` 없음 → `/status` 호출 0 · 표식 쿠키 읽기 0 · 홉 없음', async () => {
    const fetchMock = stubStatus([]);
    jar.values.set(ACCESS_COOKIE, 'a');
    jar.values.set(OPERATOR_COOKIE, 'o');
    jar.values.set(DEMO_STOP_MARKER_COOKIE, `${T0 - 60_000}.${T0 - 60_000}`);
    for (let i = 0; i < 5; i += 1) {
      expect(await liveSessionDemoStopRedirect('/accounts')).toBeNull();
    }
    expect(fetchMock).not.toHaveBeenCalled();
    expect(jar.gets).toBe(0);
  });
});

describe('② 데모 배포 — 가드는 셸의 기존 판독 외에 왕복을 더하지 않는다 (AC-3)', () => {
  beforeEach(() => {
    process.env.DEMO_API_BASE = 'https://control.example';
  });

  it('🔴🔴 한 렌더(가드 → `DemoBackendNotice`)에서 `/status` 는 1회 — 같은 스냅샷', async () => {
    const fetchMock = stubStatus([{ ok: true, body: STOPPED }]);
    jar.values.set(ACCESS_COOKIE, 'a');
    jar.values.set(OPERATOR_COOKIE, 'o');
    expect(await liveSessionDemoStopRedirect('/accounts')).toBe(
      '/api/auth/demo-ended?live=check&redirect=%2Faccounts',
    );
    await DemoBackendNotice();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('🔵 같은 인스턴스의 TTL 안 다음 요청도 `/status` 를 다시 부르지 않는다', async () => {
    const fetchMock = stubStatus([{ ok: true, body: RUNNING }]);
    await liveSessionDemoStopRedirect('/a');
    await DemoBackendNotice();
    vi.setSystemTime(T0 + DEMO_STATE_SNAPSHOT_TTL_MS - 1);
    await liveSessionDemoStopRedirect('/b');
    await DemoBackendNotice();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe('③ «서로 다른» 의 상수 ↔ 해석기의 캐시 TTL', () => {
  beforeEach(() => {
    process.env.DEMO_API_BASE = 'https://control.example';
  });

  it('🔴🔴 `DEMO_STATE_SNAPSHOT_TTL_MS` 만큼 떨어진 판독은 **새 왕복**이다(해석기 TTL ≤ 콘솔 상수)', async () => {
    // 이 칸이 빨개지면 해석기의 캐시가 콘솔이 아는 것보다 길어졌다 — 그러면 «15초 넘게 떨어진
    // 두 판독» 이 같은 스냅샷일 수 있고, 두 번 규칙이 판독 하나로 무너진다. 콘솔 상수를 올려라.
    const fetchMock = stubStatus([
      { ok: true, body: STOPPED },
      { ok: true, body: RUNNING },
    ]);
    expect(await resolveDemoBackendState()).toBe('unavailable');
    vi.setSystemTime(T0 + DEMO_STATE_SNAPSHOT_TTL_MS);
    expect(await resolveDemoBackendState()).toBe('running');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});

describe('④ 실패한 `/status` 하나는 작동하는 세션을 끝내지 않는다 (소유자 결정 ①)', () => {
  beforeEach(() => {
    process.env.DEMO_API_BASE = 'https://control.example';
  });

  it('🔴🔴 실패 → 머문다 · 15초 뒤 성공(켜짐) → 표식 비움 · 세션 그대로', async () => {
    stubStatus([{ fail: true }, { ok: true, body: RUNNING }]);
    jar.values.set(ACCESS_COOKIE, 'a');
    jar.values.set(OPERATOR_COOKIE, 'o');

    // 판독 1 — 실패(해석기는 `unavailable`). 가드 → 라우트(같은 캐시 스냅샷).
    const hop = await liveSessionDemoStopRedirect('/accounts');
    expect(hop).toBe('/api/auth/demo-ended?live=check&redirect=%2Faccounts');
    expect(await route(hop!)).toBe('/accounts?demo_checked=1');
    expect(jar.values.has(ACCESS_COOKIE)).toBe(true);
    expect(parseDemoStopMarker(jar.values.get(DEMO_STOP_MARKER_COOKIE))?.firstUnavailableAt).toBe(T0);

    // 판독 2 — 15초 뒤 새 왕복, 켜짐 → 연속이 깨진다.
    vi.setSystemTime(T0 + DEMO_STATE_SNAPSHOT_TTL_MS + 1);
    const clear = await liveSessionDemoStopRedirect('/accounts');
    expect(clear).toBe('/api/auth/demo-ended?live=clear&redirect=%2Faccounts');
    expect(await route(clear!)).toBe('/accounts?demo_checked=1');
    expect(parseDemoStopMarker(jar.values.get(DEMO_STOP_MARKER_COOKIE))?.firstUnavailableAt).toBe(0);
    expect(jar.values.has(ACCESS_COOKIE)).toBe(true);
    expect(jar.values.has(OPERATOR_COOKIE)).toBe(true);
  });

  it('🔵 대조군 — 두 번 연속 실패(15초 넘게 떨어진 두 왕복)는 꺼짐으로 읽혀 샘플로 끝난다', async () => {
    stubStatus([{ fail: true }, { fail: true }]);
    jar.values.set(ACCESS_COOKIE, 'a');
    jar.values.set(OPERATOR_COOKIE, 'o');
    await route((await liveSessionDemoStopRedirect('/accounts'))!);
    vi.setSystemTime(T0 + DEMO_STATE_SNAPSHOT_TTL_MS + 1);
    expect(await route((await liveSessionDemoStopRedirect('/accounts?demo_checked=1'))!)).toBe(
      '/accounts?signed_out=demo_stopped',
    );
    expect(jar.values.has(ACCESS_COOKIE)).toBe(false);
  });
});
