import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `TASK-PC-FE-300` — `POST /api/auth/refresh` (the browser API client's `401`
 * retry, `shared/api/client.ts`) no longer wipes the WHOLE shared-cookie
 * session on a race-loser `400 invalid_grant` (IAM `TASK-BE-606`'s 30s reuse
 * grace window). Cookies are shared across tabs, so the pre-300 behaviour —
 * clear immediately — let the losing tab log the WINNING tab out too.
 *
 * -----------------------------------------------------------------------------
 * 무엇을 재는가
 * -----------------------------------------------------------------------------
 *   AC-1 — 경쟁 패자(400) + 그 사이 쿠키가 바뀜(승자가 씀) → 세션 유지, 200/ok.
 *   AC-2 — 진짜 거부(쿠키 불변, 대조군) → 지금처럼 세션을 끝낸다, 401.
 *   AC-3 — GET·POST 가 «같은 판정 함수» (`hasCompleteSession`) 를 쓴다 — 한쪽만
 *          고쳐지는 것 방지(Failure Scenario 1).
 *
 * 🔴 `POST` 자신의 `jar` 는 이 요청이 도착했을 때의 Cookie 헤더에 얼어붙는다 — 같은
 * 실행 안에서 기다린 뒤 다시 읽어도 다른 탭이 그 사이 쓴 것을 볼 수 없다(HTTP 상
 * 불가능). 그래서 `GET` 이 이미 하는 것과 똑같이, 진짜 새 요청(`307` 재시도 — 브라우저
 * 의 `fetch()` 가 투명하게 따라간다)이 필요하다. 이 스위트는 그 두 홉을 **각각 실제로
 * 호출**해서 잰다(사본이 아니라 실제 라우트 핸들러).
 */

const ORIGIN = 'http://console.local';
const REFRESH_PATH = '/api/auth/refresh';

const cookieJar = new Map<string, { value: string; opts?: Record<string, unknown> }>();
const cookieDeletes: string[] = [];
const cookiesMock = {
  get: (name: string) => {
    const e = cookieJar.get(name);
    return e ? { value: e.value } : undefined;
  },
  set: (name: string, value: string, opts?: Record<string, unknown>) => {
    cookieJar.set(name, { value, opts });
  },
  delete: (name: string) => {
    cookieJar.delete(name);
    cookieDeletes.push(name);
  },
};
vi.mock('next/headers', () => ({ cookies: async () => cookiesMock }));

// 경합 대기는 짧게 — 그러나 0 이 아니게(정말 기다렸다를 잴 수 있게).
// `actualHasCompleteSessionRef` also has to live in `vi.hoisted()` — `vi.mock`
// factories are hoisted above ALL top-level code, so a plain `let` declared
// after them throws a TDZ ReferenceError the moment the factory runs.
const { ENV, hasCompleteSessionSpy, TEST_GRACE_MS, actualHasCompleteSessionRef } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://iam.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 5000,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
  // AC-3 — a single spy wrapping the ACTUAL predicate, shared by whatever
  // calls it. If GET or POST ever inlined its own copy instead of importing
  // this export, that call site would never touch the spy.
  hasCompleteSessionSpy: vi.fn(),
  TEST_GRACE_MS: 30,
  actualHasCompleteSessionRef: { current: undefined as ((jar: never) => boolean) | undefined },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
  publicOrigin: (e: { CONSOLE_PUBLIC_ORIGIN?: string; NEXT_PUBLIC_APP_URL: string }) =>
    e.CONSOLE_PUBLIC_ORIGIN ?? e.NEXT_PUBLIC_APP_URL,
}));

// TASK-PC-FE-300 — `tests/setup.ts` runs `vi.restoreAllMocks()` in a global
// `afterEach`, which wipes a bare `vi.fn()`'s `mockImplementation` back to
// "return undefined" after every test (not just the first). Re-applying it
// ONCE here is not enough — `beforeEach` below re-applies it before EVERY
// test, or every test after the first silently treats every session as
// incomplete (a false failure that looks like the route, not the test, is
// broken).
vi.mock('@/shared/lib/session-refresh', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/session-refresh')>();
  actualHasCompleteSessionRef.current = actual.hasCompleteSession;
  return { ...actual, REFRESH_RACE_GRACE_MS: TEST_GRACE_MS, hasCompleteSession: hasCompleteSessionSpy };
});

import { POST as refreshPOST, GET as refreshGET } from '@/app/api/auth/refresh/route';
import { ACCESS_COOKIE, REFRESH_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';
import { REFRESH_RETRY_PARAM } from '@/shared/lib/login-redirect';

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function post(path = REFRESH_PATH) {
  return refreshPOST(new Request(`${ORIGIN}${path}`, { method: 'POST' }));
}

/** IAM `400 invalid_grant` — the shape BOTH a race loser and a genuinely dead
 *  token get (rotationSuspect is set from the error code alone; § session-refresh.ts). */
const IAM_INVALID_GRANT = () => json({ error: 'invalid_grant' }, 400);

beforeEach(() => {
  cookieJar.clear();
  cookieDeletes.length = 0;
  hasCompleteSessionSpy.mockReset();
  hasCompleteSessionSpy.mockImplementation(actualHasCompleteSessionRef.current!);
  vi.unstubAllGlobals();
});

describe('POST /api/auth/refresh — 회전 경합 패자는 세션을 안 지운다 (TASK-PC-FE-300)', () => {
  it('AC-1 🔴🔴 경쟁 패자(400) — 아무것도 지우지 않고 대기 뒤 `retry=1` 로 딱 한 번 다시 본다', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref' });
    vi.stubGlobal('fetch', vi.fn(IAM_INVALID_GRANT));

    const started = Date.now();
    const first = await post();
    const elapsed = Date.now() - started;

    expect(first.status).toBe(307);
    const loc = new URL(first.headers.get('location')!);
    expect(loc.origin + loc.pathname).toBe(`${ORIGIN}${REFRESH_PATH}`);
    expect(loc.searchParams.get(REFRESH_RETRY_PARAM)).toBe('1');
    // 🔴 지웠다면 그 Set-Cookie 가 승자 탭이 방금 세운 새 쿠키를 덮을 수 있었다.
    expect(cookieDeletes).toEqual([]);
    expect(elapsed).toBeGreaterThanOrEqual(TEST_GRACE_MS - 10);
  });

  it('AC-1 승자가 그 사이 새 쿠키를 썼으면(재시도가 실어 오는 CURRENT 쿠키) — 세션을 유지하고 200/ok', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref' });
    const fetchFn = vi.fn(IAM_INVALID_GRANT);
    vi.stubGlobal('fetch', fetchFn);

    const first = await post();
    const retryUrl = new URL(first.headers.get('location')!);

    // 승자 탭이 이 사이 브라우저의 공유 쿠키 저장소에 새 세션을 썼다 — 재시도 요청은
    // 이 최신 상태를 실어 온다(실제로는 브라우저가, 여기서는 같은 공유 맵이 대신한다).
    cookieJar.set(REFRESH_COOKIE, { value: 'winner.ref' });
    cookieJar.set(ACCESS_COOKIE, { value: 'winner.acc' });
    cookieJar.set(OPERATOR_COOKIE, { value: 'winner.op' });

    const res = await refreshPOST(new Request(retryUrl, { method: 'POST' }));

    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ ok: true });
    expect(cookieDeletes).toEqual([]);
    // 재시도는 IAM 을 다시 부르지 않는다(루프 상한 — GET 과 같음).
    expect(fetchFn).toHaveBeenCalledTimes(1);
    // 승자가 쓴 값이 그대로 남는다 — 패자가 덮어쓰지 않는다.
    expect(cookieJar.get(REFRESH_COOKIE)?.value).toBe('winner.ref');
  });

  it('AC-2 🔵 대조군 — 진짜 거부(쿠키 불변, 경쟁 없음) → 지금처럼 세션 전체를 끝낸다(401)', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'dead.ref' });
    cookieJar.set(OPERATOR_COOKIE, { value: 'stale.op' });
    const fetchFn = vi.fn(IAM_INVALID_GRANT);
    vi.stubGlobal('fetch', fetchFn);

    const first = await post();
    expect(first.status).toBe(307);
    expect(cookieDeletes).toEqual([]); // 아직은 안 지운다 — AC-1 과 같은 안전장치.

    const retryUrl = new URL(first.headers.get('location')!);
    // 아무도 이 사이 쓰지 않았다 — 단일 탭의 진짜 폐기된 토큰.
    const res = await refreshPOST(new Request(retryUrl, { method: 'POST' }));

    expect(res.status).toBe(401);
    expect((await res.json()).code).toBe('TOKEN_INVALID');
    expect(cookieDeletes).toEqual(
      expect.arrayContaining([ACCESS_COOKIE, REFRESH_COOKIE, OPERATOR_COOKIE]),
    );
    expect(fetchFn).toHaveBeenCalledTimes(1);
  });

  it('🔴 대기 중 승자 응답도 실패한 경우(Edge Case) — 재시도에도 세션이 안 채워지면 지운다', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref' });
    vi.stubGlobal('fetch', vi.fn(IAM_INVALID_GRANT));

    const first = await post();
    const retryUrl = new URL(first.headers.get('location')!);
    // 승자도 실패했다 — 아무 쿠키도 안 바뀌었다(위 대조군과 동일 결과).
    const res = await refreshPOST(new Request(retryUrl, { method: 'POST' }));

    expect(res.status).toBe(401);
    expect(cookieDeletes).toEqual(
      expect.arrayContaining([ACCESS_COOKIE, REFRESH_COOKIE, OPERATOR_COOKIE]),
    );
  });

  it('회전 의심이 아닌 거부(예: invalid_client)는 대기 없이 지금처럼 즉시 지운다 — 변경 없음', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref' });
    vi.stubGlobal('fetch', vi.fn(() => json({ error: 'invalid_client' }, 401)));

    const res = await post();

    expect(res.status).toBe(401);
    expect(cookieDeletes).toEqual(expect.arrayContaining([ACCESS_COOKIE, REFRESH_COOKIE]));
  });

  it('🔵 루프 상한 — `retry=1` 은 세션이 여전히 불완전해도 IAM 을 다시 부르지 않는다', async () => {
    const fetchFn = vi.fn(IAM_INVALID_GRANT);
    vi.stubGlobal('fetch', fetchFn);

    const res = await post(`${REFRESH_PATH}?${REFRESH_RETRY_PARAM}=1`);

    expect(res.status).toBe(401);
    expect(fetchFn).not.toHaveBeenCalled();
  });
});

describe('AC-3 🔴🔴 GET·POST 가 같은 판정 함수를 쓴다 (`hasCompleteSession`) — 한쪽만 고쳐지는 것 방지', () => {
  it('GET 의 "이미 완전한 세션" 단축 경로가 공유 판정 함수를 부른다', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'a' });
    cookieJar.set(OPERATOR_COOKIE, { value: 'o' });

    const res = await refreshGET(new Request(`${ORIGIN}/api/auth/refresh?redirect=%2Fecommerce`));

    expect(res.status).toBe(307);
    expect(hasCompleteSessionSpy).toHaveBeenCalled();
  });

  it('POST 의 재시도 판정이 같은 공유 함수를 부른다', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'a' });
    cookieJar.set(OPERATOR_COOKIE, { value: 'o' });

    const res = await post(`${REFRESH_PATH}?${REFRESH_RETRY_PARAM}=1`);

    expect(res.status).toBe(200);
    expect(hasCompleteSessionSpy).toHaveBeenCalled();
  });
});
