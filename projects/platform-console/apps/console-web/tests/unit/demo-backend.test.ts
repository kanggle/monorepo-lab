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
      'http://some-service:8080/actuator/health',
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

  it('🔵 컨테이너 DNS(점 없는 서비스명)도 그대로다 — `.local` 이 아닌 자리다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'running', ip: IP });
    const { resolveBackendUrl } = await load();
    expect(await resolveBackendUrl('http://some-service:8080/x')).toBe(
      'http://some-service:8080/x',
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

  it('🔴🔴 TASK-MONO-668 — running + selection_ready=false → `starting`', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'running', ip: IP, selection_ready: false });
    const { resolveDemoBackendState, resolveDemoBackend } = await load();
    expect(await resolveDemoBackendState()).toBe('starting');
    // 🔴 켜지는 중에도 주소는 준다 — 콘솔의 BFF 호출이 폴백으로 떨어지면 안 된다.
    expect(await resolveDemoBackend()).not.toBeNull();
  });

  it('🔴 TASK-MONO-668 — 필드 없음·null(옛 람다 · 판정 불가) → `running` 그대로', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    for (const extra of [{}, { selection_ready: null }]) {
      vi.resetModules();
      stubStatus({ state: 'running', ip: IP, ...extra });
      const { resolveDemoBackendState } = await load();
      expect(await resolveDemoBackendState()).toBe('running');
    }
  });

  it('🔴 `/status` 실패도 `unavailable` 이다 — 「모른다」를 「켜졌다」로 번역하지 않는다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({}, { ok: false });
    const { resolveDemoBackendState } = await load();
    expect(await resolveDemoBackendState()).toBe('unavailable');
  });
});

// ===========================================================================
// TASK-MONO-814 — 동시 조회는 `/status` 하나 · 실패가 좋은 스냅샷을 덮지 않는다
// ===========================================================================
// 🔴🔴 33차 창(2026-10-23 UTC) 실측: 개요 한 렌더가 다리마다 `/status` 를 따로 쳐서 계정
//    동시 실행 한도 10 을 넘겼고(람다 Throttles 4·4·12·4 = APIGW 5xx 같은 수), 스로틀된
//    조회가 `null` 을 캐시해 그 다리들이 `*.local` 로 떨어졌다 → `DOWNSTREAM_ERROR`
//    (`TASK-PC-FE-369` § 측정). 아래 칸 중 앞의 셋은 **고치기 전 코드에서 빨갛다**(행동으로).
//    나머지는 대조군 — 고침이 «꺼짐» 을 가리거나 첫 실패를 «켜짐» 으로 번역하지 않는다.
describe('TASK-MONO-814 — single-flight + keep-last-good', () => {
  const T0 = 1_800_000_000_000;
  const RUNNING = { state: 'running', ip: IP };

  function statusResponse(body: unknown, ok = true): Response {
    return { ok, json: async () => body } as unknown as Response;
  }

  /** 바깥에서 풀 수 있는 fetch — 「동시에 기다리는 중」 을 만들려면 응답이 늦어야 한다. */
  function deferredFetch() {
    let release!: (r: Response) => void;
    const pending = new Promise<Response>((r) => {
      release = r;
    });
    const fetchMock = vi.fn().mockReturnValue(pending);
    vi.stubGlobal('fetch', fetchMock);
    return { fetchMock, release };
  }

  let now = T0;
  beforeEach(() => {
    now = T0;
    vi.spyOn(Date, 'now').mockImplementation(() => now);
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('🔴 차가운 캐시에 동시 호출 13 + 상태 1 → `/status` 는 **한 번**, 전부 같은 데모 주소', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    const { fetchMock, release } = deferredFetch();
    const { resolveBackendUrl, resolveDemoBackendState } = await load();

    // 개요 한 렌더의 모양 — 다리들이 같은 틱에 해석을 시작한다.
    const urls = [
      'http://iam.local',
      'http://wms.local/api/v1/admin',
      'http://scm.local',
      'http://finance.local',
      'http://erp.local',
      'http://ecommerce.local/api/admin',
      'http://iam.local/api/admin/console/registry',
      'http://wms.local/api/v1/admin',
      'http://scm.local',
      'http://finance.local',
      'http://erp.local',
      'http://ecommerce.local/api/admin',
      'http://iam.local',
    ];
    const pending = urls.map((u) => resolveBackendUrl(u));
    const statePending = resolveDemoBackendState();

    release(statusResponse(RUNNING));
    const got = await Promise.all(pending);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(got).toEqual(urls.map((u) => u.replace('.local', `.${DOMAIN}`)));
    expect(await statePending).toBe('running');
  });

  it('🔴 동시 호출 중 조회가 **실패**해도 `/status` 는 한 번이다 (실패를 N 배로 키우지 않는다)', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    const fetchMock = vi.fn().mockResolvedValue(statusResponse({}, false));
    vi.stubGlobal('fetch', fetchMock);
    const { resolveDemoBackend } = await load();

    const got = await Promise.all(Array.from({ length: 6 }, () => resolveDemoBackend()));

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(got).toEqual([null, null, null, null, null, null]);
  });

  it('🔴🔴 좋은 스냅샷 → TTL 만료 → 조회 실패(스로틀 5xx) → **좋은 스냅샷을 준다** · 캐시를 덮지 않는다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(statusResponse(RUNNING))
      .mockResolvedValueOnce(statusResponse({ message: 'Service Unavailable' }, false))
      .mockResolvedValue(statusResponse(RUNNING));
    vi.stubGlobal('fetch', fetchMock);
    const { resolveBackendUrl, resolveDemoBackendState } = await load();

    expect(await resolveBackendUrl('http://wms.local/api/v1/admin')).toBe(
      `http://wms.${DOMAIN}/api/v1/admin`,
    );

    now = T0 + 16_000; // TTL(15 s) 지남 → 다시 묻는다 → 스로틀
    expect(await resolveBackendUrl('http://wms.local/api/v1/admin')).toBe(
      `http://wms.${DOMAIN}/api/v1/admin`,
    );
    expect(await resolveDemoBackendState()).toBe('running');
    expect(fetchMock).toHaveBeenCalledTimes(2);

    // 실패 뒤 TTL 안 — 다시 묻지 않고(실패도 캐시한다), 그 칸에 든 것은 좋은 스냅샷이다.
    now = T0 + 20_000;
    expect(await resolveBackendUrl('http://scm.local')).toBe(`http://scm.${DOMAIN}`);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('🔵 대조군 — 좋은 스냅샷이 **한 번도 없었던** 첫 실패는 그대로 `null` · 설정값', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('throttled')));
    const { resolveBackendUrl, resolveDemoBackendState } = await load();

    expect(await resolveBackendUrl('http://wms.local/api/v1/admin')).toBe(
      'http://wms.local/api/v1/admin',
    );
    expect(await resolveDemoBackendState()).toBe('unavailable');
  });

  it('🔵 대조군 — 좋은 스냅샷이 **너무 오래됐으면** 실패 때 `null` (옛 IP 에 무기한 붙지 않는다)', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(statusResponse(RUNNING))
      .mockRejectedValue(new Error('timeout'));
    vi.stubGlobal('fetch', fetchMock);
    const { resolveDemoBackend } = await load();

    expect(await resolveDemoBackend()).not.toBeNull();
    now = T0 + 5 * 60_000; // 부팅 한 번(약 11 분)보다 짧지만, 상한보다는 길다
    expect(await resolveDemoBackend()).toBeNull();
  });

  it('🔴 대조군 — 좋은 스냅샷 뒤의 **확정 답** `stopped` 는 실패가 아니다 → 즉시 `null`', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(statusResponse(RUNNING))
        .mockResolvedValueOnce(statusResponse({ state: 'stopped' }))
        .mockRejectedValue(new Error('timeout')),
    );
    const { resolveDemoBackend, resolveDemoBackendState } = await load();

    expect(await resolveDemoBackend()).not.toBeNull();
    now = T0 + 16_000;
    // 🔴 여기서 옛 IP 를 주면 «조용히 옛 IP 로 붙는다» — AWS 가 회수한 주소일 수 있다.
    expect(await resolveDemoBackend()).toBeNull();
    expect(await resolveDemoBackendState()).toBe('unavailable');

    // 그리고 `stopped` 가 마지막 좋은 스냅샷을 **지웠으므로** 그 뒤 실패도 `null` 이다.
    now = T0 + 32_000;
    expect(await resolveDemoBackend()).toBeNull();
  });

  it('🔵 대조군 — 실패한 진행 중 조회가 약속을 붙잡아 두지 않는다 (TTL 뒤엔 다시 묻는다)', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new Error('throttled'))
      .mockResolvedValue(statusResponse(RUNNING));
    vi.stubGlobal('fetch', fetchMock);
    const { resolveDemoBackend } = await load();

    expect(await resolveDemoBackend()).toBeNull();
    now = T0 + 16_000;
    expect(await resolveDemoBackend()).toEqual({
      baseUrl: `http://console.${DOMAIN}`,
      demoDomain: DOMAIN,
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('🔵 대조군 — `__resetDemoBackendCache` 는 마지막 좋은 스냅샷도 지운다 (칸 사이 누수 없음)', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(statusResponse(RUNNING))
        .mockRejectedValue(new Error('boom')),
    );
    const { resolveDemoBackend, __resetDemoBackendCache } = await load();

    expect(await resolveDemoBackend()).not.toBeNull();
    __resetDemoBackendCache();
    expect(await resolveDemoBackend()).toBeNull();
  });
});
