import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-772 S4 — console-integration-contract § 2.6.3: IAM's token endpoint refuses a personal
 * (consumer-pool) session with `400 invalid_grant` + `error_description` **exactly**
 * `operator_eligibility_unavailable` when it could not ask admin-service whether the account is an operator.
 *
 * 무엇을 재는가 — 세 진입점(콜백 · refresh POST · refresh GET)이 이 거절을
 *   ① `sso_wrong_account`(«다른 계정») 로도, `token_exchange_failed` 로도 읽지 않고 `operator_check_unavailable` 로 읽는다;
 *   ② refresh 에서는 쿠키를 **하나도 지우지 않는다**(IAM 이 refresh 토큰을 돌리기 전에 실패했다 — S1-13);
 *   ③ 회전 경합 재시도(`retry=1`)를 타지 않는다(그것은 맨 `invalid_grant` 의 길이다).
 * 대조군: PC-FE-324 의 `'consumer-pool'` 문구는 그대로 `sso_wrong_account`, 값이 «포함» 일 뿐 «같지» 않은 문구는 재분류되지 않는다.
 *
 * 🔴 실제 라우트 핸들러를 import 해서 부른다 — 로직 사본을 재지 않는다.
 */

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

const { ENV } = vi.hoisted(() => ({
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
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
  publicOrigin: (e: { CONSOLE_PUBLIC_ORIGIN?: string; NEXT_PUBLIC_APP_URL: string }) =>
    e.CONSOLE_PUBLIC_ORIGIN ?? e.NEXT_PUBLIC_APP_URL,
}));
vi.mock('@/shared/lib/session-refresh', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/session-refresh')>();
  return { ...actual, REFRESH_RACE_GRACE_MS: 30 };
});

import { GET as callbackGET } from '@/app/api/auth/callback/route';
import { GET as refreshGET, POST as refreshPOST } from '@/app/api/auth/refresh/route';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  ID_TOKEN_COOKIE,
  PKCE_VERIFIER_COOKIE,
  OAUTH_STATE_COOKIE,
} from '@/shared/lib/session';
import { SESSION_REFRESH_PATH } from '@/shared/lib/login-redirect';
import {
  isOperatorEligibilityUnavailable,
  OPERATOR_CHECK_UNAVAILABLE,
  OPERATOR_CHECK_UNAVAILABLE_CODE,
  OPERATOR_ELIGIBILITY_UNAVAILABLE,
} from '@/shared/lib/iam-token-refusal';

const ORIGIN = 'http://console.local';
const UNAVAILABLE_BODY = { error: 'invalid_grant', error_description: 'operator_eligibility_unavailable' };
const NO_FACET_BODY = {
  error: 'invalid_grant',
  error_description: "tenant_id 'consumer-pool' is a reserved storage value and is never issued",
};

function iamReplies(body: unknown, status = 400) {
  const fn = vi.fn(() =>
    Promise.resolve(
      new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }),
    ),
  );
  vi.stubGlobal('fetch', fn);
  return fn;
}

function callback() {
  cookieJar.set(PKCE_VERIFIER_COOKIE, { value: 'v' });
  cookieJar.set(OAUTH_STATE_COOKIE, { value: 's|/accounts' });
  return callbackGET(new Request(`${ORIGIN}/api/auth/callback?code=x&state=s`));
}

/** A browser that still holds a full session plus its 30-day refresh cookie. */
function fullSession() {
  cookieJar.set(ACCESS_COOKIE, { value: 'acc' });
  cookieJar.set(OPERATOR_COOKIE, { value: 'op' });
  cookieJar.set(ID_TOKEN_COOKIE, { value: 'id' });
  cookieJar.set(REFRESH_COOKIE, { value: 'ref' });
}

beforeEach(() => {
  cookieJar.clear();
  cookieDeletes.length = 0;
  vi.unstubAllGlobals();
});

describe('§ 2.6.3 상수 — 콘솔과 IAM 이 같은 값을 쓴다', () => {
  it('IAM 고정 상수 · 콘솔 로그인 코드 · BFF 코드', () => {
    expect(OPERATOR_ELIGIBILITY_UNAVAILABLE).toBe('operator_eligibility_unavailable');
    expect(OPERATOR_CHECK_UNAVAILABLE).toBe('operator_check_unavailable');
    expect(OPERATOR_CHECK_UNAVAILABLE_CODE).toBe('OPERATOR_CHECK_UNAVAILABLE');
  });

  it('🔴 값 전체 일치 — 포함·접두·다른 error 는 아니다 · consumer-pool 문구도 아니다', () => {
    expect(isOperatorEligibilityUnavailable(UNAVAILABLE_BODY)).toBe(true);
    expect(isOperatorEligibilityUnavailable(NO_FACET_BODY)).toBe(false);
    expect(
      isOperatorEligibilityUnavailable({
        error: 'invalid_grant',
        error_description: 'operator_eligibility_unavailable: admin down',
      }),
    ).toBe(false);
    expect(
      isOperatorEligibilityUnavailable({ error: 'server_error', error_description: 'operator_eligibility_unavailable' }),
    ).toBe(false);
    expect(isOperatorEligibilityUnavailable(null)).toBe(false);
    expect(isOperatorEligibilityUnavailable('operator_eligibility_unavailable')).toBe(false);
  });
});

describe('GET /api/auth/callback — 판정 실패는 «다른 계정» 이 아니다', () => {
  it('🔴 operator_eligibility_unavailable → /login?error=operator_check_unavailable · 토큰 쿠키 없음', async () => {
    iamReplies(UNAVAILABLE_BODY);

    const res = await callback();

    const loc = res.headers.get('location')!;
    expect(loc).toContain('/login?error=operator_check_unavailable');
    expect(loc).not.toContain('sso_wrong_account');
    expect(cookieJar.has(ACCESS_COOKIE)).toBe(false);
    expect(cookieJar.has(OPERATOR_COOKIE)).toBe(false);
  });

  it('🔵 대조군 (PC-FE-324 회귀 불변) — consumer-pool 문구는 그대로 sso_wrong_account', async () => {
    iamReplies(NO_FACET_BODY);

    const res = await callback();

    expect(res.headers.get('location')).toContain('/login?error=sso_wrong_account');
  });

  it('🔵 대조군 — 상수를 «포함» 할 뿐인 문구는 재분류되지 않는다 (token_exchange_failed)', async () => {
    iamReplies({ error: 'invalid_grant', error_description: 'x operator_eligibility_unavailable' });

    const res = await callback();

    expect(res.headers.get('location')).toContain('/login?error=token_exchange_failed');
  });
});

describe('POST /api/auth/refresh — 판정 실패는 일시적: 쿠키 유지 · 503', () => {
  it('🔴 503 {code: OPERATOR_CHECK_UNAVAILABLE} · 쿠키 삭제 0 · 회전 경합 재시도(307) 아님', async () => {
    fullSession();
    const fetchMock = iamReplies(UNAVAILABLE_BODY);

    const res = await refreshPOST(new Request(`${ORIGIN}/api/auth/refresh`, { method: 'POST' }));

    expect(res.status).toBe(503);
    expect((await res.json()).code).toBe('OPERATOR_CHECK_UNAVAILABLE');
    expect(cookieDeletes).toEqual([]);
    expect(cookieJar.get(REFRESH_COOKIE)?.value).toBe('ref');
    expect(cookieJar.get(OPERATOR_COOKIE)?.value).toBe('op');
    // Only the IAM refresh call — no operator re-exchange was attempted.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('🔵 대조군 — 맨 invalid_grant 는 지금처럼 회전 경합 재시도(307 ?retry=1)', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'ref' });
    iamReplies({ error: 'invalid_grant' });

    const res = await refreshPOST(new Request(`${ORIGIN}/api/auth/refresh`, { method: 'POST' }));

    expect(res.status).toBe(307);
    expect(new URL(res.headers.get('location')!).searchParams.get('retry')).toBe('1');
  });
});

describe('GET /api/auth/refresh (유휴 갱신) — 판정 실패는 사유와 목적지를 들고 /login · 쿠키 유지', () => {
  it('🔴 /login?error=operator_check_unavailable&redirect=<target> · 쿠키 삭제 0', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'ref' }); // idle-expired: only the refresh cookie survived
    iamReplies(UNAVAILABLE_BODY);

    const res = await refreshGET(
      new Request(`${ORIGIN}${SESSION_REFRESH_PATH}?redirect=${encodeURIComponent('/accounts')}`),
    );

    const loc = new URL(res.headers.get('location')!);
    expect(loc.pathname).toBe('/login');
    expect(loc.searchParams.get('error')).toBe('operator_check_unavailable');
    expect(loc.searchParams.get('redirect')).toBe('/accounts');
    expect(loc.searchParams.get('retry')).toBeNull();
    expect(cookieDeletes).toEqual([]);
    expect(cookieJar.get(REFRESH_COOKIE)?.value).toBe('ref');
    expect(res.headers.get('Cache-Control')).toBe('no-store');
  });

  it('🔵 대조군 (OD-6) — 측면 없는 refresh(consumer-pool 문구)는 지금처럼 회전 경합 홉 뒤 session_expired', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'ref' });
    iamReplies(NO_FACET_BODY);

    // a plain `invalid_grant` takes the rotation-race hop first; its retry is the final answer
    const first = await refreshGET(new Request(`${ORIGIN}${SESSION_REFRESH_PATH}?redirect=%2Faccounts`));
    const retry = new URL(first.headers.get('location')!);
    expect(retry.searchParams.get('retry')).toBe('1');
    const res = await refreshGET(new Request(retry.toString()));

    const loc = new URL(res.headers.get('location')!);
    expect(loc.searchParams.get('error')).toBe('session_expired');
  });
});
