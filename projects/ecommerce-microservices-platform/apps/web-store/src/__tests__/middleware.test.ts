import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/shared/auth/auth', () => ({
  auth: vi.fn().mockResolvedValue(null),
}));

import { NextRequest } from 'next/server';
import { middleware, config } from '@/middleware';

function request(path: string) {
  return new NextRequest(new URL(`http://localhost:3001${path}`));
}

describe('web-store route-guard middleware', () => {
  beforeEach(() => vi.clearAllMocks());

  it('serves /sw.js without an auth redirect (TASK-FE-083-fix-001)', async () => {
    const res = await middleware(request('/sw.js'));
    // NextResponse.next() → 200 with no Location; a guard redirect would be 307→/login.
    expect(res.status).toBe(200);
    expect(res.headers.get('location')).toBeNull();
  });

  it('redirects a protected path to /login when unauthenticated', async () => {
    const res = await middleware(request('/cart'));
    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toContain('/login');
  });

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
});
