import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * TASK-MONO-611 AC-1 — `OIDC_ISSUER_URL` 의 조용한 폴백을 fail-fast 로.
 *
 * 🔴 이 스위트가 지키는 성질은 **두 개**이고, 둘째가 첫째보다 중요하다.
 *
 *   1. Vercel 에서 값이 없으면 로그인 시도가 **변수 이름을 부르며** 죽는다.
 *   2. 🔴🔴 **모듈을 import 하는 것만으로는 절대 안 던진다.** 이것이 회귀
 *      방지선이다 — 611 이 실측했다: object literal 안에 throw 를 넣으면
 *      `next build` 가 `Failed to collect page data for
 *      /api/auth/[...nextauth]` 로 rc=1 이 된다(같은 환경의 대조군 빌드는
 *      rc=0). `middleware.ts`·`Header.tsx`·auth 라우트가 전부 이 모듈을
 *      물기 때문이다. 즉 순진한 fail-fast 는 «깨진 로그인» 이 아니라
 *      «앱 전체의 모든 향후 배포» 를 죽인다.
 *
 * 🔵 술어가 «값이 없다» 가 아니라 **«Vercel 인데 값이 없다»** 인 이유:
 * `http://iam.local` 은 로컬 Traefik 과 데모 호스트에서 **진짜 유효한 값**이고,
 * 데모 호스트는 production 빌드로 돈다 ⇒ `NODE_ENV` 로 갈랐으면 데모가 깨진다.
 */

const KEYS = ['OIDC_ISSUER_URL', 'VERCEL'] as const;
type Key = (typeof KEYS)[number];

const saved: Partial<Record<Key, string | undefined>> = {};

function setEnv(key: Key, value: string | undefined) {
  if (value === undefined) {
    delete process.env[key];
  } else {
    process.env[key] = value;
  }
}

const loadEnvModule = () => import('@/shared/config/env');

beforeEach(() => {
  for (const k of KEYS) saved[k] = process.env[k];
  vi.resetModules();
});

afterEach(() => {
  for (const k of KEYS) setEnv(k, saved[k]);
  vi.restoreAllMocks();
});

describe('oidcIssuerUrl 폴백', () => {
  it('로컬·CI (VERCEL 없음 · 값 없음) → iam.local 로 폴백하고 던지지 않는다', async () => {
    setEnv('OIDC_ISSUER_URL', undefined);
    setEnv('VERCEL', undefined);

    const { env, assertOidcIssuerConfigured } = await loadEnvModule();

    expect(env.oidcIssuerUrl).toBe('http://iam.local');
    expect(() => assertOidcIssuerConfigured()).not.toThrow();
  });

  it('값이 있으면 어디서든 그 값을 쓴다 (VERCEL 여부와 무관)', async () => {
    setEnv('OIDC_ISSUER_URL', 'https://auth.example.test');
    setEnv('VERCEL', '1');

    const { env, assertOidcIssuerConfigured } = await loadEnvModule();

    expect(env.oidcIssuerUrl).toBe('https://auth.example.test');
    expect(() => assertOidcIssuerConfigured()).not.toThrow();
  });
});

describe('🔴 Vercel 인데 값이 없을 때', () => {
  it('assertOidcIssuerConfigured() 가 변수 이름을 부르며 던진다', async () => {
    setEnv('OIDC_ISSUER_URL', undefined);
    setEnv('VERCEL', '1');
    vi.spyOn(console, 'error').mockImplementation(() => {});

    const { assertOidcIssuerConfigured } = await loadEnvModule();

    // 🔴 메시지의 «부류» 가 아니라 이름 자체를 문다 — 다음 사람이 문구를
    //    바꾸더라도 변수 이름이 빠지면 이 단언이 빨개진다.
    expect(() => assertOidcIssuerConfigured()).toThrow(/OIDC_ISSUER_URL/);
  });

  it('모듈 로드 시점에 변수 이름을 담은 진단을 한 번 낸다', async () => {
    setEnv('OIDC_ISSUER_URL', undefined);
    setEnv('VERCEL', '1');
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await loadEnvModule();

    expect(spy).toHaveBeenCalledTimes(1);
    expect(String(spy.mock.calls[0]?.[0])).toContain('OIDC_ISSUER_URL');
  });

  it('🔴🔴 그래도 import 자체는 던지지 않는다 — 이것이 배포를 지키는 선이다', async () => {
    setEnv('OIDC_ISSUER_URL', undefined);
    setEnv('VERCEL', '1');
    vi.spyOn(console, 'error').mockImplementation(() => {});

    await expect(loadEnvModule()).resolves.toBeDefined();
  });
});

describe('음성 대조군 — 진단이 아무 데서나 울지 않는다', () => {
  it('VERCEL 이 없으면 값이 없어도 console.error 를 내지 않는다', async () => {
    setEnv('OIDC_ISSUER_URL', undefined);
    setEnv('VERCEL', undefined);
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await loadEnvModule();

    expect(spy).not.toHaveBeenCalled();
  });
});
