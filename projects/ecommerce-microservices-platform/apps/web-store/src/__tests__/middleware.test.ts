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
});
