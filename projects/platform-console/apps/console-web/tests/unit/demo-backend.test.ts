/**
 * `shared/config/demo-backend.ts` — 데모 백엔드 주소의 **런타임 해석**
 * (TASK-MONO-585 AC-2 / AC-5 · ADR-MONO-067 D2).
 *
 * 🔴 이 표에서 가장 중요한 칸은 "있음/꺼짐" 이 아니라 **`/status` 가 실패하는 칸**이다.
 *    그 칸이 없으면 *"데모 컨트롤 플레인이 잠깐 죽으면 콘솔도 죽는다"* 를 아무도 모른다.
 *    안전한 쪽은 언제나 **설정된 값**이고, "꺼졌다" 로도 "켜졌다" 로도 번역하지 않는다.
 *
 * 🔴 그리고 **`DEMO_API_BASE` 부재 칸**이 회귀 방지선이다 — 로컬 개발과 CI 와 컨테이너
 *    데모에는 컨트롤 플레인이 없다. 이 변경이 그 셋을 깨면 안 된다.
 *
 * 🔴🔴 콘솔에만 있는 칸: **여섯 도메인 전수**. 이 티켓의 Edge Case 가 *"한 곳만 매핑을
 *    빠뜨리면 그 화면만 죽고 원인이 안 보인다"* 라고 적었다. 그래서 `env.ts` 의 12개 URL 을
 *    **하나씩** 걸어 본다 — 「여섯 개를 다 적었나」가 아니라 「실제로 열두 개가 다 바뀌나」다.
 */

import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';

const ORIGINAL_ENV = { ...process.env };
const CONTROL = 'https://control.example';
const IP = '13.125.1.2';
const DOMAIN = '13-125-1-2.sslip.io';

/** 매 칸 새 모듈 인스턴스를 얻는다 — 모듈 스코프 캐시가 칸 사이로 새면 안 된다. */
async function load() {
  const mod = await import('@/shared/config/demo-backend');
  mod.__resetDemoBackendCache();
  return mod;
}

function stubStatus(body: unknown, init: { ok?: boolean } = {}) {
  const ok = init.ok ?? true;
  const fetchMock = vi.fn().mockResolvedValue({
    ok,
    json: async () => body,
  } as unknown as Response);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

beforeEach(() => {
  vi.resetModules();
  vi.unstubAllGlobals();
  delete process.env.DEMO_API_BASE;
  delete process.env.CONSOLE_PUBLIC_ORIGIN;
  delete process.env.NEXT_PUBLIC_APP_URL;
});

afterEach(() => {
  vi.unstubAllGlobals();
  process.env = { ...ORIGINAL_ENV };
});

// ===========================================================================
// demoizeUrl — 순수 함수. 여기가 틀리면 아래 전부가 틀린다.
// ===========================================================================
describe('demoizeUrl', () => {
  it('호스트 꼬리 `.local` 만 바꾸고 **나머지 바이트는 안 건드린다**', async () => {
    const { demoizeUrl } = await load();
    expect(demoizeUrl('http://iam.local/api/admin/console/registry', DOMAIN)).toBe(
      `http://iam.${DOMAIN}/api/admin/console/registry`,
    );
  });

  it('🔴 경로가 없는 URL 에 **슬래시를 붙이지 않는다**', async () => {
    const { demoizeUrl } = await load();
    // `new URL(...).toString()` 은 `http://iam.local` 을 `http://iam.local/` 로 정규화한다.
    // 그러면 호출부의 `${base}/api/...` 가 `//api/...` 가 되고 Traefik 이 404 를 낸다 —
    // DNS 도 TCP 도 멀쩡한 채로 라우터만 못 찾는, 진단이 가장 오래 걸리는 종류다.
    expect(demoizeUrl('http://iam.local', DOMAIN)).toBe(`http://iam.${DOMAIN}`);
    expect(demoizeUrl('http://iam.local', DOMAIN).endsWith('/')).toBe(false);
  });

  it('`.local` 이 아닌 값은 **그대로 통과**한다 — 데모가 아닌 배포에선 아무 일도 안 일어난다', async () => {
    const { demoizeUrl } = await load();
    for (const url of [
      'https://auth.hubwang.com/oauth2/token',
      'http://console-bff:8080/api/console/dashboards/domain-health',
      'https://console.hubwang.com',
      '/api/console/notifications',
    ]) {
      expect(demoizeUrl(url, DOMAIN)).toBe(url);
    }
  });

  it('`.local` 이 **호스트 꼬리가 아닌** 자리에 있으면 안 바꾼다', async () => {
    const { demoizeUrl } = await load();
    // 경로 안의 `.local` 은 호스트가 아니다.
    expect(demoizeUrl('http://iam.example.com/a/b.local', DOMAIN)).toBe(
      'http://iam.example.com/a/b.local',
    );
  });
});

// ===========================================================================
// resolveDemoBackend — 왕복
// ===========================================================================
describe('resolveDemoBackend', () => {
  it('running + ip → <ip-대시>.sslip.io 로 조립한다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    const fetchMock = stubStatus({ state: 'running', ip: IP });

    const { resolveDemoBackend } = await load();
    expect(await resolveDemoBackend()).toEqual({
      baseUrl: `http://console.${DOMAIN}`,
      demoDomain: DOMAIN,
    });
    // 🔵 부팅 경로와 같은 표기(점→대시)를 쓰는지가 요점이다. 점 표기면 DNS 는 풀리는데
    //    Traefik 이 라우터를 못 찾아 404 를 낸다(TASK-MONO-389 가 밟은 함정).
    expect(fetchMock).toHaveBeenCalledWith(
      `${CONTROL}/status`,
      expect.objectContaining({ cache: 'no-store' }),
    );
  });

  it('🔴 `DEMO_API_BASE` 부재 → **왕복 자체를 안 한다** (로컬·CI·컨테이너 데모)', async () => {
    const fetchMock = stubStatus({ state: 'running', ip: IP });
    const { resolveDemoBackend } = await load();
    expect(await resolveDemoBackend()).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('🔴 `state` 가 running 이 아니면 **주소를 안 만든다**', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'stopped', ip: IP });
    const { resolveDemoBackend } = await load();
    // 조용히 옛 IP 로 붙는 것이 가장 나쁘다 — AWS 가 회수해 **남의 인스턴스**일 수 있다.
    expect(await resolveDemoBackend()).toBeNull();
  });

  it('🔴 running 인데 `ip` 가 없는 반쪽 응답 → 주소를 안 만든다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'running' });
    const { resolveDemoBackend } = await load();
    expect(await resolveDemoBackend()).toBeNull();
  });

  it('🔴 `/status` 가 실패(비-2xx)하면 판정 불가 → 주소를 안 만든다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({}, { ok: false });
    const { resolveDemoBackend } = await load();
    expect(await resolveDemoBackend()).toBeNull();
  });

  it('🔴 타임아웃/네트워크 실패(fetch throw)도 같은 자리로 떨어진다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(Object.assign(new Error('aborted'), { name: 'AbortError' })),
    );
    const { resolveDemoBackend } = await load();
    expect(await resolveDemoBackend()).toBeNull();
  });
});

// ===========================================================================
// resolveBackendUrl — 여섯 도메인 전수 (AC-2 의 본체)
// ===========================================================================
describe('resolveBackendUrl — env.ts 의 12개 URL 을 하나씩', () => {
  /**
   * 🔴 이 표는 `shared/config/env.ts` 에서 `.default('http…')` 를 갖는 **필드 전수**다
   * (2026-09-05 실측: 출현 14 · distinct URL **12** · 호스트 7 — 아래 13행 중
   * `FINANCE_BASE_URL`/`LEDGER_BASE_URL` 이 같은 값, `NEXT_PUBLIC_APP_URL` 이
   * `ClientEnvSchema` 시절의 중복분과 같은 값이라 distinct 는 12다).
   * 「여섯 접두사를 적었나」가 아니라 「열두 개가 실제로 다 바뀌나」를 잰다 — 하나를
   * 빠뜨리면 **그 화면만** 죽고 원인이 안 보인다.
   *
   * 🔵 `NEXT_PUBLIC_APP_URL` 은 예외적으로 «백엔드» 가 아니라 **이 앱 자신의 오리진**이고,
   * 실제 코드에서 `resolveBackendUrl` 을 지나지 않는다(그 몫은 `self-origin.ts` +
   * `CONSOLE_PUBLIC_ORIGIN`). 그래도 표에 남기는 이유는 이 표의 명제가 «흐름» 이 아니라
   * **«`.default` 를 가진 것을 하나도 빠뜨리지 않았다»** 이기 때문이다 — 모집단을
   * 코드 경로로 좁히면 그 경로가 바뀔 때 표가 조용히 공허해진다.
   */
  const CONFIGURED: Array<[key: string, url: string]> = [
    ['CONSOLE_REGISTRY_URL', 'http://iam.local/api/admin/console/registry'],
    ['CONSOLE_TOKEN_EXCHANGE_URL', 'http://iam.local/api/admin/auth/token-exchange'],
    ['CONSOLE_ONBOARDING_URL', 'http://iam.local/api/admin/onboarding/organizations'],
    ['IAM_ADMIN_API_BASE', 'http://iam.local'],
    ['WMS_ADMIN_BASE_URL', 'http://wms.local/api/v1/admin'],
    ['WMS_OUTBOUND_BASE_URL', 'http://wms.local/api/v1/outbound'],
    ['SCM_GATEWAY_BASE_URL', 'http://scm.local'],
    ['FINANCE_BASE_URL', 'http://finance.local'],
    ['LEDGER_BASE_URL', 'http://finance.local'],
    ['ERP_BASE_URL', 'http://erp.local'],
    ['ECOMMERCE_ADMIN_BASE_URL', 'http://ecommerce.local/api/admin'],
    ['ECOMMERCE_PUBLIC_BASE_URL', 'http://ecommerce.local/api'],
    ['NEXT_PUBLIC_APP_URL', 'http://console.local'],
  ];

  it('데모가 running 이면 **전부** 데모 도메인으로 바뀐다 (0건도 안 남는다)', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'running', ip: IP });
    const { resolveBackendUrl } = await load();

    const unchanged: string[] = [];
    for (const [key, url] of CONFIGURED) {
      const got = await resolveBackendUrl(url);
      expect(got).toBe(url.replace('.local', `.${DOMAIN}`));
      if (got === url) unchanged.push(key);
    }
    // 🔴 «전부 바뀌었나» 를 목록으로 단언한다. 개수만 세면 어느 것이 빠졌는지 안 나온다.
    expect(unchanged).toEqual([]);

    // 여섯 도메인이 실제로 다 등장했는지 — 표가 줄어들면 여기서 빨개진다.
    const prefixes = new Set(
      CONFIGURED.map(([, u]) => u.replace(/^https?:\/\//, '').split('.')[0]),
    );
    expect([...prefixes].sort()).toEqual([
      'console',
      'ecommerce',
      'erp',
      'finance',
      'iam',
      'scm',
      'wms',
    ]);
  });

  it('🔴 데모가 아니면 **한 글자도 안 바뀐다**', async () => {
    stubStatus({ state: 'running', ip: IP });
    const { resolveBackendUrl } = await load();
    for (const [, url] of CONFIGURED) {
      expect(await resolveBackendUrl(url)).toBe(url);
    }
  });

  it('🔴 데모가 꺼져 있으면 설정값 그대로 — 옛 IP 로 조용히 붙지 않는다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'stopped', ip: IP });
    const { resolveBackendUrl } = await load();
    expect(await resolveBackendUrl('http://iam.local/api/admin')).toBe(
      'http://iam.local/api/admin',
    );
  });

  it('🔵 `OIDC_ISSUER_URL` 축의 값은 이 함수를 **안 지난다** — 지나더라도 무해함을 고정한다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'running', ip: IP });
    const { resolveBackendUrl } = await load();
    // ADR-MONO-069 C2 의 고정 이름. `.local` 이 아니므로 술어가 반응하지 않는다.
    expect(await resolveBackendUrl('https://auth.hubwang.com/oauth2/token')).toBe(
      'https://auth.hubwang.com/oauth2/token',
    );
  });

  it('🔵 `console-bff` 의 컨테이너 DNS 도 그대로다 — 공개 호스트명이 없는 자리다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'running', ip: IP });
    const { resolveBackendUrl } = await load();
    expect(await resolveBackendUrl('http://console-bff:8080/x')).toBe(
      'http://console-bff:8080/x',
    );
  });
});

// ===========================================================================
// resolveDemoBackendState — AC-3 이 읽는 세 값
// ===========================================================================
describe('resolveDemoBackendState', () => {
  it('컨트롤 플레인이 없으면 `not-demo` — 여기서 "데모가 꺼졌다" 는 거짓말이다', async () => {
    const { resolveDemoBackendState } = await load();
    expect(await resolveDemoBackendState()).toBe('not-demo');
  });

  it('컨트롤 플레인 + running → `running`', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'running', ip: IP });
    const { resolveDemoBackendState } = await load();
    expect(await resolveDemoBackendState()).toBe('running');
  });

  it('컨트롤 플레인 + stopped → `unavailable` (배너가 뜨는 유일한 칸)', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'stopped' });
    const { resolveDemoBackendState } = await load();
    expect(await resolveDemoBackendState()).toBe('unavailable');
  });

  it('🔴 `/status` 실패도 `unavailable` 이다 — 「모른다」를 「켜졌다」로 번역하지 않는다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({}, { ok: false });
    const { resolveDemoBackendState } = await load();
    expect(await resolveDemoBackendState()).toBe('unavailable');
  });
});
