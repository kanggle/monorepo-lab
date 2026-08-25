/**
 * `demo-backend.ts` — 데모 백엔드 주소의 **런타임 해석** (TASK-MONO-580 / ADR-MONO-067 D2).
 *
 * 🔴 이 표에서 가장 중요한 칸은 "있음/꺼짐" 이 아니라 **`/status` 가 실패하는 칸**이다.
 *    그 칸이 없으면 *"데모 컨트롤 플레인이 잠깐 죽으면 스토어도 죽는다"* 를 아무도 모른다.
 *    안전한 쪽은 언제나 **기존 env 사슬**이고, "꺼졌다" 로도 "켜졌다" 로도 번역하지 않는다.
 *
 * 🔴 그리고 **`DEMO_API_BASE` 부재 칸**이 회귀 방지선이다 — 로컬 개발과 CI 에는 컨트롤
 *    플레인이 없다. 이 변경이 그 둘을 깨면 안 된다.
 */

import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';

const ORIGINAL_ENV = { ...process.env };
const CONTROL = 'https://control.example';

/** 매 칸 새 모듈 인스턴스를 얻는다 — 모듈 스코프 캐시가 칸 사이로 새면 안 된다. */
async function load() {
  const mod = await import('../demo-backend');
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
  delete process.env.API_URL_INTERNAL;
  delete process.env.NEXT_PUBLIC_API_URL;
});

afterEach(() => {
  vi.unstubAllGlobals();
  process.env = { ...ORIGINAL_ENV };
});

describe('resolveDemoBackend', () => {
  it('running + ip → <ip-대시>.sslip.io 로 조립한다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    const fetchMock = stubStatus({ state: 'running', ip: '13.125.1.2' });

    const { resolveDemoBackend } = await load();
    const demo = await resolveDemoBackend();

    expect(demo).toEqual({
      baseUrl: 'http://ecommerce.13-125-1-2.sslip.io',
      demoDomain: '13-125-1-2.sslip.io',
    });
    // 🔵 부팅 경로와 같은 표기(점→대시)를 쓰는지가 요점이다. 점 표기면 DNS 는 풀리는데
    //    Traefik 이 라우터를 못 찾아 404 를 낸다(TASK-MONO-389 가 밟은 함정).
    expect(demo?.demoDomain).not.toContain('13.125');
    expect(fetchMock).toHaveBeenCalledWith(
      `${CONTROL}/status`,
      expect.objectContaining({ cache: 'no-store' }),
    );
  });

  it('state 가 running 이 아니면 주소를 만들지 않는다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'stopped', ip: '13.125.1.2' });

    const { resolveDemoBackend } = await load();
    // 🔴 꺼져 있는데 옛 IP 로 조립하면 **남의 인스턴스**에 붙을 수 있다(AWS 가 회수해
    //    재할당한다). ip 가 응답에 있어도 만들지 않는다.
    expect(await resolveDemoBackend()).toBeNull();
  });

  it('🔴 DEMO_API_BASE 가 없으면 컨트롤 플레인을 부르지도 않는다 (로컬·CI)', async () => {
    const fetchMock = stubStatus({ state: 'running', ip: '13.125.1.2' });

    const { resolveDemoBackend } = await load();
    expect(await resolveDemoBackend()).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('🔴 /status 가 5xx 면 null — "꺼짐" 이 아니라 "판정 불가" 다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'running', ip: '13.125.1.2' }, { ok: false });

    const { resolveDemoBackend } = await load();
    expect(await resolveDemoBackend()).toBeNull();
  });

  it('🔴 /status 가 던지면(네트워크·타임아웃) null 이고 예외를 새지 않는다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('boom')));

    const { resolveDemoBackend } = await load();
    await expect(resolveDemoBackend()).resolves.toBeNull();
  });

  it('running 인데 ip 가 없는 반쪽 응답은 만들지 않는다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'running' });

    const { resolveDemoBackend } = await load();
    expect(await resolveDemoBackend()).toBeNull();
  });

  it('ip 가 IPv4 모양이 아니면 만들지 않는다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    stubStatus({ state: 'running', ip: 'not-an-ip' });

    const { resolveDemoBackend } = await load();
    expect(await resolveDemoBackend()).toBeNull();
  });

  it('TTL 안의 연속 호출은 컨트롤 API 를 한 번만 때린다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    const fetchMock = stubStatus({ state: 'running', ip: '13.125.1.2' });

    const { resolveDemoBackend } = await load();
    await resolveDemoBackend();
    await resolveDemoBackend();
    await resolveDemoBackend();

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('🔵 실패한 판정도 캐시한다 — 컨트롤 플레인이 죽었을 때 매 요청 재시도하지 않는다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    const fetchMock = vi.fn().mockRejectedValue(new Error('boom'));
    vi.stubGlobal('fetch', fetchMock);

    const { resolveDemoBackend } = await load();
    await resolveDemoBackend();
    await resolveDemoBackend();

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('DEMO_API_BASE 의 끝 슬래시를 먹는다 (//status 가 되지 않게)', async () => {
    process.env.DEMO_API_BASE = `${CONTROL}/`;
    const fetchMock = stubStatus({ state: 'running', ip: '13.125.1.2' });

    const { resolveDemoBackend } = await load();
    await resolveDemoBackend();

    expect(fetchMock).toHaveBeenCalledWith(
      `${CONTROL}/status`,
      expect.anything(),
    );
  });
});

describe('resolveUpstreamBaseUrl — 폴백 사슬', () => {
  it('해석되면 그 주소', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    process.env.API_URL_INTERNAL = 'http://gateway-service:8080';
    stubStatus({ state: 'running', ip: '13.125.1.2' });

    const { resolveUpstreamBaseUrl } = await load();
    expect(await resolveUpstreamBaseUrl()).toBe(
      'http://ecommerce.13-125-1-2.sslip.io',
    );
  });

  it('🔴 해석 안 되면 API_URL_INTERNAL — 데모 호스트의 컨테이너 판이 이걸로 산다', async () => {
    process.env.API_URL_INTERNAL = 'http://gateway-service:8080';
    process.env.NEXT_PUBLIC_API_URL = 'http://ecommerce.local';

    const { resolveUpstreamBaseUrl } = await load();
    expect(await resolveUpstreamBaseUrl()).toBe('http://gateway-service:8080');
  });

  it('🔴 그다음 NEXT_PUBLIC_API_URL — 로컬 개발이 이걸로 산다', async () => {
    process.env.NEXT_PUBLIC_API_URL = 'http://ecommerce.local';

    const { resolveUpstreamBaseUrl } = await load();
    expect(await resolveUpstreamBaseUrl()).toBe('http://ecommerce.local');
  });

  it('🔴 아무것도 없으면 localhost — CI 가 이걸로 산다', async () => {
    const { resolveUpstreamBaseUrl } = await load();
    expect(await resolveUpstreamBaseUrl()).toBe('http://localhost:8080');
  });

  it('🔴 데모가 꺼져 있어도 기존 사슬로 떨어진다 (업스트림이 사라지지 않는다)', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    process.env.API_URL_INTERNAL = 'http://gateway-service:8080';
    stubStatus({ state: 'stopped' });

    const { resolveUpstreamBaseUrl } = await load();
    expect(await resolveUpstreamBaseUrl()).toBe('http://gateway-service:8080');
  });
});
