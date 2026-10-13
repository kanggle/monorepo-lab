import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

vi.mock('@/shared/auth/session-token', () => ({
  decodeSessionCookieHeader: vi.fn().mockResolvedValue(null),
}));

import { NextRequest } from 'next/server';
import { middleware, config } from '@/middleware';
import { decodeSessionCookieHeader } from '@/shared/auth/session-token';

const mockDecode = vi.mocked(decodeSessionCookieHeader);

function request(path: string) {
  return new NextRequest(new URL(`http://localhost:3001${path}`));
}

describe('web-store route-guard middleware', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDecode.mockResolvedValue(null);
  });

  it('serves /sw.js without an auth redirect (TASK-FE-083-fix-001)', async () => {
    const res = await middleware(request('/sw.js'));
    // NextResponse.next() → 200 with no Location; a guard redirect would be 307→/login.
    expect(res.status).toBe(200);
    expect(res.headers.get('location')).toBeNull();
  });

  // TASK-FE-102 — /cart is public now (guest cart); ordering stays behind login.
  it('redirects a protected path (/checkout) to /login when unauthenticated', async () => {
    const res = await middleware(request('/checkout?items=p1%3Av1'));
    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toContain('/login');
    expect(res.headers.get('location')).toContain(encodeURIComponent('/checkout'));
  });

  it('serves /cart to an anonymous visitor (guest cart, TASK-FE-102)', async () => {
    const res = await middleware(request('/cart'));
    expect(res.status).toBe(200);
    expect(res.headers.get('location')).toBeNull();
  });

  // 🔵 control — opening /cart must not open its neighbours: the rule is exactly '/cart'.
  it.each(['/cartx', '/cart/anything', '/checkout/payment', '/my/orders'])(
    '🔵 %s is still gated for an anonymous visitor',
    async (path) => {
      const res = await middleware(request(path));
      expect(res.status).toBe(307);
      expect(res.headers.get('location')).toContain('/login');
    },
  );

  it('config.matcher excludes /sw.js so middleware never runs on the SW script', () => {
    expect(config.matcher[0]).toContain('sw.js');
  });

  // 🔴🔴 TASK-MONO-654 — 데모 판정 탐침은 **익명이 부를 수 있어야 한다.**
  //    이 배너의 청중이 정확히 «로그인하지 않은 방문자» 이므로, 여기서 307 이 나면
  //    배너는 그 방문자에게 **영영 안 뜬다** — 결함이 「배너가 낡았다」에서
  //    「배너가 아예 없다」로 바뀔 뿐이고, 그것이 더 조용하다.
  it('🔴 /api/demo/backend-state 를 익명에게 열어 둔다 (TASK-MONO-654)', async () => {
    const res = await middleware(request('/api/demo/backend-state'));
    expect(res.status).toBe(200);
    expect(res.headers.get('location')).toBeNull();
  });

  // 🔵 대조군 — 같은 `/api/demo/` 아래라고 전부 열린 것이 아니다. 하트비트는 여전히
  //    막혀야 한다(익명 하트비트가 EC2 예산을 태운다, ADR-MONO-071 § D8).
  //    이 칸이 없으면 위 칸을 `startsWith('/api/demo')` 로 넓혀도 초록이다.
  it('🔵 대조군 — /api/demo/heartbeat 는 여전히 익명에게 막혀 있다', async () => {
    const res = await middleware(request('/api/demo/heartbeat'));
    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toContain('/login');
  });

  // 🔴🔴 TASK-FE-109 — `/api/vitals` 는 `useReportWebVitals` 가 `sendBeacon`/`fetch`
  // 로 쏘는 POST(가끔 GET, cf. 브라우저의 beacon 폴백) 를 받는 공개 엔드포인트다. 이
  // 경로가 공개 목록에 없으면 비로그인 방문자의 vitals POST 가 여기서 `/login` 으로
  // 307 되고(307 은 메서드를 보존 — RFC 7231 §6.4.7), `/login` 페이지는 POST 핸들러가
  // 없어 405 를 낸다. Vercel 로그의 "POST /login 405" 가 이것이다 — AC-0.
  it('🔴 /api/vitals 로 가는 POST 를 /login 으로 307 하지 않는다 (TASK-FE-109 AC-0/AC-1)', async () => {
    const res = await middleware(
      new NextRequest(new URL('http://localhost:3001/api/vitals'), {
        method: 'POST',
        body: JSON.stringify({ name: 'LCP', value: 2000, id: 'v1' }),
      }),
    );
    expect(res.status).toBe(200);
    expect(res.headers.get('location')).toBeNull();
  });

  // 🔵 대조군 — GET /api/vitals 도 같은 이유로 열려 있어야 한다(로그에 `GET /api/vitals
  // 307` 도 함께 찍혔다 — sendBeacon 폴백/프리플라이트성 호출을 배제하지 않는다).
  it('🔵 대조군 — GET /api/vitals 도 열려 있다', async () => {
    const res = await middleware(request('/api/vitals'));
    expect(res.status).toBe(200);
    expect(res.headers.get('location')).toBeNull();
  });

  // 🔵 대조군 — 로그인한 방문자도 /api/vitals 에서 막히지 않는다(이 경로는 인증 여부와
  // 무관하게 공개이지, "인증되면 통과" 로 바뀐 게 아니다).
  it('🔵 대조군 — 로그인한 방문자도 /api/vitals 에서 막히지 않는다', async () => {
    mockDecode.mockResolvedValue({
      accountId: 'acc-1',
      roles: ['CUSTOMER'],
      expiresAt: Math.floor(Date.now() / 1000) + 1800,
    });
    const res = await middleware(request('/api/vitals'));
    expect(res.status).toBe(200);
    expect(res.headers.get('location')).toBeNull();
  });
});

/**
 * 🔴 TASK-FE-106 — the gate must never spend the refresh token. The old gate
 * called `auth()` (RSC form), which ran the silent refresh — rotating the
 * refresh token at IAM — and threw the rotated cookie away.
 */
describe('web-store middleware — decode-only gate (TASK-FE-106)', () => {
  const fetchMock = vi.fn();
  const nowSec = () => Math.floor(Date.now() / 1000);

  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('🔴 만료됐지만 refresh 가능한 CUSTOMER 세션은 통과시키고, IAM 에 refresh 를 보내지 않는다', async () => {
    mockDecode.mockResolvedValue({
      accountId: 'acc-1',
      roles: ['CUSTOMER'],
      accessToken: 'expired-access',
      refreshToken: 'rt-0',
      expiresAt: nowSec() - 600,
    });
    const res = await middleware(request('/my/orders'));
    expect(res.status).toBe(200);
    expect(res.headers.get('location')).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('refresh 실패 플래그(error)가 있는 세션은 /login 으로 보낸다', async () => {
    mockDecode.mockResolvedValue({
      accountId: 'acc-1',
      roles: ['CUSTOMER'],
      error: 'RefreshAccessTokenError',
    });
    const res = await middleware(request('/checkout'));
    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toContain('/login');
  });

  it('CUSTOMER 역할이 없는 세션은 /login 으로 보낸다 (교차 앱 가드 유지)', async () => {
    mockDecode.mockResolvedValue({
      accountId: 'op-1',
      roles: ['ECOMMERCE_OPERATOR'],
      expiresAt: nowSec() + 1800,
    });
    const res = await middleware(request('/checkout'));
    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toContain('/login');
  });

  it('🔵 대조군 — 유효한 CUSTOMER 세션은 통과', async () => {
    mockDecode.mockResolvedValue({
      accountId: 'acc-1',
      roles: ['CUSTOMER'],
      expiresAt: nowSec() + 1800,
    });
    const res = await middleware(request('/checkout'));
    expect(res.status).toBe(200);
  });
});
