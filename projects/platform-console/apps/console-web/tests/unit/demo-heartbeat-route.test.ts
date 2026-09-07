/**
 * `POST /api/demo/heartbeat` — **익명은 아무 일도 안 한다**.
 *
 * =============================================================================
 * 🔴🔴 왜 이 칸이 회귀선인가
 * =============================================================================
 * 하트비트는 데모 EC2 인스턴스를 **살려 두는 신호**이고, 그 인스턴스에는 예산이 있다
 * (`infra/demo/aws/terraform/lambda/handler.py` — 유휴/최대가동/월예산으로 stop).
 * 공개 둘러보기는 백엔드 없이도 완전히 동작하므로 인스턴스를 켜 둘 이유가 없고, 익명
 * 방문자의 열린 탭이 예산을 태우면 **정작 로그인해서 기능을 쓰려는 사람이 왔을 때 꺼져
 * 있다**. 그래서 익명 호출은 upstream 을 부르지 않는다.
 *
 * 🔴 그 성질을 «핑거를 (demo) 에 안 달았다» 로만 얻지 않는다 — `curl` 은 컴포넌트를
 *    거치지 않는다. 서버가 다시 확인하는 것이 진짜 경계이고, 이 파일이 재는 것이 그것이다.
 *
 * 🔴 «upstream 을 안 불렀다» 를 **fetch 호출 수**로 잰다. 상태 코드(204)만 재면, 부르고
 *    나서 204 를 주는 구현도 통과한다 — 두 사실은 다르고 예산을 태우는 쪽은 전자다.
 * =============================================================================
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));

import { POST } from '@/app/api/demo/heartbeat/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

const ORIGINAL_ENV = { ...process.env };
let fetchSpy: ReturnType<typeof vi.fn>;

/** 운영자 세션 = IAM 접근 토큰 **과** 운영자 토큰 둘 다 (session.ts § isAuthenticated). */
function signIn() {
  cookieJar.set(ACCESS_COOKIE, 'iam-access-token');
  cookieJar.set(OPERATOR_COOKIE, 'operator-token');
}

beforeEach(() => {
  cookieJar.clear();
  process.env = { ...ORIGINAL_ENV };
  delete process.env.DEMO_API_BASE;
  fetchSpy = vi.fn(async () => new Response(null, { status: 204 }));
  vi.stubGlobal('fetch', fetchSpy);
});

afterEach(() => {
  vi.unstubAllGlobals();
  process.env = { ...ORIGINAL_ENV };
});

describe('POST /api/demo/heartbeat', () => {
  it('🔴 익명 + 컨트롤 플레인 설정 있음 → 204 이고 upstream 을 **안 부른다**', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    const res = await POST();
    expect(res.status).toBe(204);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('🔴 반쪽 세션(접근 토큰만, 운영자 토큰 없음)도 익명과 같다', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    cookieJar.set(ACCESS_COOKIE, 'iam-access-token');
    const res = await POST();
    expect(res.status).toBe(204);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('인증됨 + 설정 없음(로컬·CI) → 204 no-op. 결함이 아니라 선언된 상태다', async () => {
    signIn();
    const res = await POST();
    expect(res.status).toBe(204);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('인증됨 + 설정 있음 → 컨트롤 플레인 `/heartbeat` 로 POST 를 중계한다', async () => {
    process.env.DEMO_API_BASE = 'https://control.example/';
    signIn();
    const res = await POST();
    expect(res.status).toBe(204);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    // 꼬리 슬래시가 `//heartbeat` 가 되지 않는다(Traefik/APIGW 404 의 단골 원인).
    expect(url).toBe('https://control.example/heartbeat');
    expect(init.method).toBe('POST');
  });

  it('upstream 이 죽어도 콘솔은 204 다 — 백엔드 장애가 콘솔 장애로 보이면 안 된다', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    signIn();
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new Error('ECONNREFUSED');
      }),
    );
    const res = await POST();
    expect(res.status).toBe(204);
  });

  it('빈 문자열 설정은 «설정 없음» 과 같다(빈 값이 사슬을 멈추지 않는다)', async () => {
    process.env.DEMO_API_BASE = '   ';
    signIn();
    const res = await POST();
    expect(res.status).toBe(204);
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
